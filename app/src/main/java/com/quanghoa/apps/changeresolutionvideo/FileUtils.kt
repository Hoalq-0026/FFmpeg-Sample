import android.app.DownloadManager
import android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
import android.content.*
import android.database.Cursor
import android.database.DatabaseUtils
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import com.quanghoa.apps.changeresolutionvideo.BuildConfig
import com.quanghoa.apps.changeresolutionvideo.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.util.*

object FileUtils {

    const val TAG: String = "FileUtils"
    const val MIME_TYPE_AUDIO = "audio/*"
    const val MIME_TYPE_TEXT = "text/*"
    const val MIME_TYPE_IMAGE = "image/*"
    const val MIME_TYPE_VIDEO = "video/*"
    const val MIME_TYPE_APP = "app/*"
    const val MIME_TYPE_ALL = "*/*"
    const val FILE_SIZE_FORMAT = "#,##0.#"
    const val MAX_IMAGES_FOR_UPLOAD = 10
    const val MAX_SIZE_FILE_FOR_UPLOAD = 25 * 1024 * 1024 // 25MB
    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.<br></br>
     * <br></br>
     * Callers should check whether the path is local before assuming it
     * represents a local file.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @see .isLocal
     * @see .getFile
     * @author paulburke
     */
    fun getPath(context: Context, uri: Uri): String? {

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG + " File -",
                "Authority: " + uri.authority +
                        ", Fragment: " + uri.fragment +
                        ", Port: " + uri.port +
                        ", Query: " + uri.query +
                        ", Scheme: " + uri.scheme +
                        ", Host: " + uri.host +
                        ", Segments: " + uri.pathSegments.toString()
            )
        }

        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split((":").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                }

                // TODO handle non-primary volumes
            } else if (isDownloadsDocument(uri)) {
                return getDataColumnDowloadDocument(context, uri)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split((":").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }// MediaProvider
            // DownloadsProvider
            // ExternalStorageProvider
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {

            // Return the remote address
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(context, uri, null, null)

        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }// File
        // MediaStore (and general)

        return null
    }

    /**
     * Convert Uri into File, if possible.
     *
     * @return file A local file that the Uri was pointing to, or null if the
     * Uri is unsupported or pointed to a remote resource.
     * @see .getPath
     * @author paulburke
     */
    fun getFile(context: Context, uri: Uri?): File? {
        if (uri != null) {
            val path = getPath(context, uri)
            if (path != null && isLocal(path)) {
                return File(path)
            }
        }
        return null
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     * @author paulburke
     */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     * @author paulburke
     */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     * @author paulburke
     */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    /**
     * @return Whether the URI is a local one.
     */
    fun isLocal(url: String?): Boolean {
        return url != null && !url.startsWith("http://") && !url.startsWith("https://")
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     * @author paulburke
     */
    fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String>?
    ): String? {

        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)

            cursor?.let {
                if (cursor.moveToFirst()) {
                    if (BuildConfig.DEBUG)
                        DatabaseUtils.dumpCursor(it)

                    val columnIndex = it.getColumnIndexOrThrow(column)
                    return it.getString(columnIndex)
                }
            }
        } finally {
            cursor?.let {
                it.close()
            }
        }
        return null
    }

    /**
     * @return The MIME type for the give Uri.
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        val file = File(getPath(context, uri))
        return getMimeType(file)
    }

    /**
     * @return The MIME type for the given file.
     */
    fun getMimeType(file: File?): String? {
        file?.let {
            val extension = getExtension(it.name)
            extension?.let {
                return if (extension.isNotEmpty()) MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.substring(1).toLowerCase())
                else "application/octet-stream"
            }
        }
        return null
    }

    /**
     * Gets the extension of a file name, like ".png" or ".jpg".
     *
     * @param uri
     * @return Extension including the dot("."); "" if there is no extension;
     * null if uri was null.
     */
    fun getExtension(uri: String?): String? {
        if (uri == null) {
            return null
        }

        val dot = uri.lastIndexOf(".")
        return if (dot >= 0) {
            uri.substring(dot)
        } else {
            // No extension.
            ""
        }
    }

    fun downloadFile(context: Context, fileUrl: String, fileName: String) {
        val uriDownload = Uri.parse(fileUrl)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(uriDownload)

        request.setTitle(fileName)
        request.setDescription(context.getString(R.string.download_file_description))

        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        request.setNotificationVisibility(VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        downloadManager.enqueue(request)
    }

    fun convertFileSizeToString(size: Long): String {
        if (size <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat(FILE_SIZE_FORMAT).format(
            size / Math.pow(
                1024.0,
                digitGroups.toDouble()
            )
        ) + " " + units[digitGroups]
    }

    fun openFileDownloaded(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val downloadLocalUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
            val downloadMimeType = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE))
            if (downloadStatus == DownloadManager.STATUS_SUCCESSFUL && downloadLocalUri != null && !downloadMimeType.contains(
                    "image",
                    ignoreCase = true
                )
            ) {
                openFileByOtherApp(context, Uri.parse(downloadLocalUri), downloadMimeType)
            }
        }
    }

    private fun openFileByOtherApp(context: Context, fileUri: Uri?, mimeType: String) {
        fileUri?.let {
            if (ContentResolver.SCHEME_FILE.equals(fileUri.scheme, ignoreCase = true)) {
                val file = File(fileUri.path)
                val attachmentUri = FileProvider.getUriForFile(context, "vn.com.eschool.labhok.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(attachmentUri, mimeType)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Log.d("error", e.message)
                }

            }
        }
    }

    fun checkTypeImage(extension: String): Boolean {
        val types = arrayListOf("jpg", "jpeg", "gif", "png")
        var result = false
        types.forEach {
            if (extension.toLowerCase() == it) {
                result = true
            }
        }
        return result
    }

    fun isFromGoogleDrive(rawPath: String): Boolean {
        if (rawPath.contains("com.google.android.apps.docs.storage")) {
            return true
        }
        return false
    }

    fun getFileSize(file: File?): Long {
        file?.let {
            return it.length()
        }
        return 0
    }

    private fun getDataColumnDowloadDocument(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val fileName = cursor.getString(0)
                val path = Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName
                if (!TextUtils.isEmpty(path)) {
                    return path
                }
            }
        } finally {
            cursor?.close()
        }
        val id = DocumentsContract.getDocumentId(uri)
        return try {
            val contentUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
            )

            getDataColumn(context, contentUri, null, null)
        } catch (e: NumberFormatException) {
            //In Android 8 and Android P the id is not a number
            uri.path.replaceFirst("^/document/raw:", "").replaceFirst("^raw:", "")
        }
    }

    fun isSupportForUploading(file: File?): Boolean {
        val types = arrayListOf(
            ".txt",
            ".csv",
            ".doc",
            ".docx",
            ".xls",
            ".xlsx",
            ".ppt",
            ".pptx",
            ".ppsx",
            ".pdf",
            ".jpg",
            ".jpeg",
            ".gif",
            ".png",
            ".mp4",
            ".mpeg4",
            ".3gp",
            ".mov",
            ".avi",
            ".mp3",
            ".m4a",
            ".wma",
            ".zip",
            ".pages",
            ".numbers",
            ".key"
        )
        file?.let { originalFile ->
            val extension = getExtension(originalFile.name)
            if (!extension.isNullOrEmpty() && types.contains(extension?.toLowerCase())) {
                return true
            }
        }
        return false
    }

    fun writeVideoFileToApp(applicationContext: Context, bitmap: Bitmap): Uri {
        val nameFile = String.format("labhok-%s.mp4", UUID.randomUUID().toString())
        val outputDir = File(applicationContext.filesDir, "filter_outputs")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputFile = File(outputDir, nameFile)
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, out)
        } finally {
            out?.let {
                try {
                    it.close()
                } catch (ignore: IOException) {

                }
            }
        }
        return Uri.fromFile(outputFile)
    }

    fun createTemporaryVideoFile(applicationContext: Context): File {
        val nameFile = String.format("temp-%s.mp4", UUID.randomUUID().toString())
        val outputDir = File(applicationContext.filesDir, "labhok_videos")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return File(outputDir, nameFile)
    }

    fun getNameFile(file: File): String? {
        return file.nameWithoutExtension
    }

    fun deleteFile(file: File) {
        if(file.exists()){
            file.delete()
        }
    }



    /* - https://www.bugcodemaster.com/article/changing-resolution-video-using-ffmpeg
       - https://androidadvanced.com/2017/03/17/ffmpeg-video-editor/
       - https://github.com/bravobit/FFmpeg-Android
     */
}

