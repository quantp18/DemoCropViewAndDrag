package com.example.democropviewanddrag

import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewTreeObserver
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.democropviewanddrag.databinding.ActivityDemoCustomView2Binding

class DemoCustomView2Activity : AppCompatActivity() {
    private val permissionCommon by lazy { PermissionCommon(this, this) }
    private var isFg = false

    private var binding : ActivityDemoCustomView2Binding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDemoCustomView2Binding.inflate(LayoutInflater.from(this), null, false)
        setContentView(binding!!.root)

        permissionCommon.initLauncher()
        permissionCommon.onChooseSuccess = {
            val bitmap = BitmapFactory.decodeFile(it)
            if (!isFg) {
                binding?.cropImageView?.setImageBitmap(bitmap)
            } else {
                binding?.zoomView?.setImageBitmap(bitmap)
                val cropRect = binding?.cropImageView?.getCropBorderRect()
                cropRect?.let { rect -> binding?.zoomView?.setBackgroundRect(rect) }
            }
        }

        binding?.apply {
            btnSetBGImageFromCam.setOnClickListener {
                isFg = false
                permissionCommon.takePicture(this@DemoCustomView2Activity)
            }

            btnSetBGImageFromStorage.setOnClickListener {
                isFg = false
                permissionCommon.onSelectFromGallery()
            }

            btnSetFgFromCam.setOnClickListener {
                isFg = true
                permissionCommon.takePicture(this@DemoCustomView2Activity)
            }
            btnSetFgFromStorage.setOnClickListener {
                isFg = true
                permissionCommon.onSelectFromGallery()
            }

            btnRatio11.setOnClickListener {
                cropImageView.setCropRatio(1f, 1f)
            }
            btnRatio34.setOnClickListener {
                cropImageView.setCropRatio(3f, 4f)
            }
            btnRatio169.setOnClickListener {
                cropImageView.setCropRatio(16f, 9f)
            }
            btnRatio916.setOnClickListener {
                cropImageView.setCropRatio(9f, 16f)
            }

            var visible = false
            btnCrop.setOnClickListener {
                visible = !visible
                ivPreview.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
                val (bgBitmap, fgBitmap, mergedBitmap) = zoomView.getProcessedImages()
                ivPreview.setImageBitmap(mergedBitmap)
            }


            val vto = zoomView.viewTreeObserver
            vto.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    zoomView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    if (cropImageView.drawable != null) {
                        // Nếu CropImageView có hình ảnh, khớp với vùng crop
                        val cropRect = cropImageView.getCropBorderRect()
                        zoomView.setBackgroundRect(cropRect)
                    } else {
                        // Nếu CropImageView null hoặc không có hình ảnh, khớp với chiều rộng FrameLayout
                        val viewRect = RectF(0f, 0f, zoomView.width.toFloat(), zoomView.height.toFloat())
                        zoomView.setBackgroundRect(viewRect)
                    }
                }
            })

            // Đồng bộ khi vùng crop thay đổi
            cropImageView.onCropRectChangedListener = { newRect ->
                zoomView.setBackgroundRect(newRect)
            }
        }
    }
}