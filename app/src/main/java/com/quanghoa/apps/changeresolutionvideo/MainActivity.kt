package com.quanghoa.apps.changeresolutionvideo

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.Intent
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.MediaController
import android.widget.VideoView
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.FFmpeg.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.FFmpeg.RETURN_CODE_SUCCESS
import kotlinx.android.synthetic.main.activity_main.*
import permissions.dispatcher.*
import vn.com.eschool.base.util.FileUtils
import java.io.File

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    val SELECT_FILES_REQUEST = 1
    var destinationFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        button.setOnClickListener {
            selectFileUploadWithPermissionCheck()
        }

        playbutton.setOnClickListener {
            val mediaController = MediaController(this)
            val videoView = findViewById<VideoView>(R.id.videoView)
            mediaController.setAnchorView(videoView)

            val uri = Uri.fromFile(destinationFile)
            videoView.setMediaController(mediaController)
            videoView.setVideoURI(uri)
            videoView.requestFocus()
            videoView.start()

        }
    }

    @NeedsPermission(READ_EXTERNAL_STORAGE)
    fun selectFileUpload() {
        val intent: Intent = Intent().apply {
            type = FileUtils.MIME_TYPE_ALL
            action = Intent.ACTION_GET_CONTENT
        }
        startActivityForResult(Intent.createChooser(intent, getString(R.string.prompt_select_file)), SELECT_FILES_REQUEST)
    }

    @OnPermissionDenied(READ_EXTERNAL_STORAGE)
    fun showDeniedForReadExternalStorage() {
        showToast(this.getString(R.string.permission_read_external_storage_denied))
    }

    @OnShowRationale(READ_EXTERNAL_STORAGE)
    fun showRationaleForReadExternalStorage(request: PermissionRequest) {
        AlertDialog.Builder(this)
                .setPositiveButton(android.R.string.ok) { _, _ -> request.proceed() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> request.cancel() }
                .setCancelable(false)
                .setMessage(R.string.permission_read_external_rationale)
                .show()
    }

    @OnNeverAskAgain(READ_EXTERNAL_STORAGE)
    fun showNeverAskForReadExternalStorage() {
        showToast(this.getString(R.string.permission_read_external_storage_never_ask_again))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && requestCode == SELECT_FILES_REQUEST) {
            data?.let {
                uploadFilesWithData(it)
            }
        }
    }

    fun uploadFilesWithData(intent: Intent) {
        val data = intent.data

        val originalFile = FileUtils.getFile(this, data)
        /* if (FileUtils.getFileSize(originalFile) >= FileUtils.MAX_SIZE_FILE_FOR_UPLOAD) {
             showErrorDialog(requireContext().getString(R.string.cannot_upload_file_have_large_size_message))
             return
         }*/

        destinationFile = FileUtils.createTemporaryVideoFile(this)

        FFmpeg.execute("-i ${originalFile?.absolutePath} -c:v mpeg4 ${destinationFile?.absolutePath} ")
        val rc = FFmpeg.getLastReturnCode()
        val output = FFmpeg.getLastCommandOutput()
        if (rc == RETURN_CODE_SUCCESS) {
            Log.d("Hoa", "Command execution completed successfully.")
        } else if (rc == RETURN_CODE_CANCEL) {
            Log.d("Hoa", "Command execution be cancelled by user.")
        } else {
            Log.d("Hoa", "Command execution failed with rc =$rc and output=$output")
        }

        val info = FFmpeg.getMediaInformation(destinationFile?.absolutePath)

        Log.d("Hoa", "info:rawinfo: ${info.rawInformation} bitrate:${info.bitrate} -duration:${info.duration} - format:${info.format} -path:${info.path}")

    }
}
