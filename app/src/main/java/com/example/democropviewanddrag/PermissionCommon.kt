package com.example.democropviewanddrag

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File
import java.io.IOException


class PermissionCommon(
    private val mContext: Context,
    val activity: AppCompatActivity
) {
    private var takeFromGallery: Boolean = false
    private var mCurrentPhotoPath: String? = null
    private var permissionManager: PermissionManager? = null
    private var pickMediaLauncher: ActivityResultLauncher<String>? = null
    private var takePictureLauncher: ActivityResultLauncher<Intent>? = null
    var onChooseSuccess: ((String) -> Unit)? = null
    var onStartCamera: (() -> Unit)? = null

    fun initLauncher() {
        permissionManager = PermissionManager(activity)
        pickMediaLauncher =
            activity.registerForActivityResult(ActivityResultContracts.GetContent()) {
                Log.e("TAG", "pickMediaLauncher: $it")
                if (it != null) {
                    FileUtils.copyImageFileFromUri(context = activity, it)
                        ?.let { it1 -> onChooseSuccess?.invoke(it1.path) }
                }

            }
        takePictureLauncher =
            activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                Log.e("TAG", "pickMediaLauncher: $it $mCurrentPhotoPath")
                mCurrentPhotoPath?.let { path -> onChooseSuccess?.invoke(path) }
            }
    }

    fun takePicture(context: Context) {

        @Throws(IOException::class)
        fun createImageFile(): File {
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            return File.createTempFile(
                "IMG_${System.currentTimeMillis()}",
                ".jpg",
                storageDir
            ).apply {
                mCurrentPhotoPath = absolutePath
            }
        }

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(context.packageManager) != null) {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                null
            }
            photoFile?.let {
                val photoURI = FileProvider.getUriForFile(
                    context,
                    "${activity.packageName}.provider",
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureLauncher?.launch(takePictureIntent)
            }
        }
    }

    fun onSelectFromGallery() {
        pickMediaLauncher?.launch("image/*")
    }
    fun release() {
        permissionManager = null
        takePictureLauncher?.unregister()
        takePictureLauncher = null
    }

}