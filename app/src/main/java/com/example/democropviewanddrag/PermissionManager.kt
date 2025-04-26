package com.example.democropviewanddrag

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class PermissionManager(private val activity: AppCompatActivity) {
    private var onResult: ((granted: Boolean, deniedPermissions: List<String>) -> Unit)? = null

    fun onResult(onResult: (granted: Boolean, deniedPermissions: List<String>) -> Unit) {
        this.onResult = onResult
    }

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val denied = resultMap.filterValues { !it }.keys.toList()
        onResult?.invoke(denied.isEmpty(), denied)
    }

    private val imagePerm: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
    private val cameraPerm = Manifest.permission.CAMERA
    val enableRequestImage: Boolean get() = has(imagePerm)
    val enableCamera: Boolean get() = has(cameraPerm)
    val enabledFullCamAndStorage: Boolean get() = has(cameraPerm) && has(imagePerm)

    fun goToSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = ("package:" + context.packageName).toUri()
        context.startActivity(intent)
    }

    fun requestImagePermission() {
        launcher.launch(arrayOf(imagePerm))
    }

    fun requestCameraPermission() {
        launcher.launch(arrayOf(cameraPerm))
    }

    fun requestReadAndCamera() {
        val perms = mutableListOf<String>()
        if (!has(cameraPerm)) perms += cameraPerm
        if (!has(imagePerm)) perms += imagePerm
        if (perms.isNotEmpty()) {
            launcher.launch(perms.toTypedArray())
        } else {
            onResult?.invoke(true, emptyList())
        }
    }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
}
