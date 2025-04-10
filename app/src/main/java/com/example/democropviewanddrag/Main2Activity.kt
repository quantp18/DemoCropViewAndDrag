package com.example.democropviewanddrag

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import com.example.democropviewanddrag.databinding.ActivityMainNewBinding


class Main2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityMainNewBinding

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainNewBinding.inflate(layoutInflater)

        setContentView(binding.root)
        binding.ivRotate.setImageFromResource(R.drawable.content)

        binding.btnCrop.setOnClickListener {
            binding.ivRotate.setImageFromResource(R.drawable.content2)
        }
        binding.btn1.setOnClickListener {
            binding.cropView.setCropRatio(1f, 1f)
        }

        binding.btn2.setOnClickListener {
            binding.cropView.setCropRatio(2f, 3f)
        }

        binding.btn3.setOnClickListener {
            binding.cropView.setCropRatio(3f, 2f)
        }

        binding.btn26.setOnClickListener {
            binding.cropView.setCropRatio(6f, 2f)
        }

        binding.btnShow.setOnClickListener {
            binding.ivPreview.setImageBitmap(mergeBitmaps())
        }

        binding.btnWatermark.setOnClickListener {
            binding.ivPreview.setImageBitmap(addWatermark())
        }

        // Đồng bộ tỷ lệ và vị trí của CropZoomView với vùng crop của CropImageView
        binding.cropView.viewTreeObserver.addOnGlobalLayoutListener {
            val cropRect = binding.cropView.mCropRectFClone
            if (cropRect.width() > 0 && cropRect.height() > 0) {
                // Cập nhật kích thước và vị trí của CropZoomView
                val layoutParams = binding.ivRotate.layoutParams
                layoutParams.width = cropRect.width().toInt()
                layoutParams.height = cropRect.height().toInt()
                binding.ivRotate.layoutParams = layoutParams

                // Căn chỉnh vị trí của CropZoomView
                binding.ivRotate.x = cropRect.left
                binding.ivRotate.y = cropRect.top
            }
        }
    }

    private fun mergeBitmaps(): Bitmap? {
        val backgroundBitmap = binding.cropView.getAccurateCropBitmap() ?: return null
        val foregroundBitmap =
            binding.ivRotate.getBitmap(backgroundBitmap.width, backgroundBitmap.height)
                ?: return null

        val resultBitmap = createBitmap(backgroundBitmap.width, backgroundBitmap.height)
        val canvas = Canvas(resultBitmap)

        canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
        canvas.drawBitmap(foregroundBitmap, 0f, 0f, null)

        Log.e("TAG", "mergeBitmaps: ")

        return resultBitmap
    }

    fun Bitmap.trimTransparent(): Bitmap {
        val width = width
        val height = height
        var top = 0
        var bottom = height
        var left = 0
        var right = width

        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        // Tìm top
        loop@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != 0) {
                    top = y
                    break@loop
                }
            }
        }

        // Tìm bottom
        loop@ for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != 0) {
                    bottom = y + 1
                    break@loop
                }
            }
        }

        // Tìm left
        loop@ for (x in 0 until width) {
            for (y in top until bottom) {
                if (pixels[y * width + x] != 0) {
                    left = x
                    break@loop
                }
            }
        }

        // Tìm right
        loop@ for (x in width - 1 downTo 0) {
            for (y in top until bottom) {
                if (pixels[y * width + x] != 0) {
                    right = x + 1
                    break@loop
                }
            }
        }

        val newWidth = right - left
        val newHeight = bottom - top

        if (newWidth <= 0 || newHeight <= 0) return this // Tránh lỗi

        return Bitmap.createBitmap(this, left, top, newWidth, newHeight)
    }


    private fun addWatermark(): Bitmap? {

        val mergedBitmap = mergeBitmaps() ?: return null

        val resultBitmap = createBitmap(mergedBitmap.width, mergedBitmap.height)

        val watermarkBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_watermark)

        val canvas = Canvas(resultBitmap)

        canvas.drawBitmap(mergedBitmap, 0f, 0f, null)

        canvas.drawBitmap(
            watermarkBitmap,
            (mergedBitmap.width - watermarkBitmap.width).toFloat(),
            (mergedBitmap.height - watermarkBitmap.height).toFloat(),
            null
        )

        return resultBitmap
    }

}