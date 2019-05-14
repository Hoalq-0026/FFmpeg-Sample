package com.quanghoa.apps.changeresolutionvideo

import FileUtils
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import kotlinx.android.synthetic.main.activity_main.*
import nl.bravobit.ffmpeg.FFcommandExecuteResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import permissions.dispatcher.*
import java.io.File

/** Document reference
     - https://www.bugcodemaster.com/article/changing-resolution-video-using-ffmpeg
     - https://androidadvanced.com/2017/03/17/ffmpeg-video-editor/
     - https://github.com/bravobit/FFmpeg-Android
     - https://stackoverflow.com/questions/39473434/ffmpeg-command-for-faster-encoding-at-a-decent-bitrate-with-smaller-file-size
   */
@RuntimePermissions
class MainActivity : AppCompatActivity() {

    companion object{
        const val  TAG ="ChangeResolution"
    }
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
        startActivityForResult(
                Intent.createChooser(intent, getString(R.string.prompt_select_file)),
                SELECT_FILES_REQUEST
        )
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
        showProgress()
        val data = intent.data

        val originalFile = FileUtils.getFile(this, data)
        destinationFile = FileUtils.createTemporaryVideoFile(this)

        val ffMpeg = FFmpeg.getInstance(this)
            if (ffMpeg.isSupported) {
                val compressVideo = arrayOf("-y", "-i", "${originalFile?.absolutePath}", "-vf", "scale=-2:540:","-preset","veryfast", "-vcodec", "libx264", "-b:a"," 128k", "-crf","18","${destinationFile?.absolutePath}")
                ffMpeg.execute(compressVideo, object : FFcommandExecuteResponseHandler {
                    override fun onFinish() {
                        val fileSize = FileUtils.getFileSize(destinationFile)
                        Log.d(TAG, "onFinish: file size: ${FileUtils.convertFileSizeToString(fileSize)}")
                        hideProgress()
                    }

                    override fun onSuccess(message: String?) {
                        val fileSize = FileUtils.getFileSize(destinationFile)
                        Log.d(TAG, "onSuccess: $message $fileSize")
                        Toast.makeText(this@MainActivity, "file size: ${FileUtils.getFileSize(destinationFile)}", Toast.LENGTH_LONG).show()
                    }

                    override fun onFailure(message: String?) {
                        Log.d(TAG, "onFailure: $message")
                    }

                    override fun onProgress(message: String?) {
                        Log.d(TAG, "onFailure: $message")
                    }

                    override fun onStart() {

                    }

                })
            }
    }

    private fun showProgress() {
        progressbar.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        progressbar.visibility = View.GONE
    }
}
