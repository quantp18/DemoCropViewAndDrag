package com.example.democropviewanddrag

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import com.example.democropviewanddrag.databinding.ActivityMainNewBinding
import kotlin.math.atan2


class Main2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityMainNewBinding

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainNewBinding.inflate(layoutInflater)

        setContentView(binding.root)

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

                // Điều chỉnh tỷ lệ của backgroundRect trong CropZoomView
                val ratio = cropRect.width() / cropRect.height()

                binding.ivRotate.adjustToRatio(ratio)
            }
        }
    }

    private fun mergeBitmaps(): Bitmap? {
        val backgroundBitmap = binding.cropView.getAccurateCropBitmap() ?: return null
        val foregroundBitmap = binding.ivRotate.getBitmap() ?: return null

        val resultBitmap = Bitmap.createBitmap(
            backgroundBitmap.width,
            backgroundBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)

        // Vẽ ảnh nền
        canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)

        // === Tính scale giữa cropView (view) và ảnh crop thật (bitmap) ===
        val scaleX = backgroundBitmap.width.toFloat() / binding.cropView.width
        val scaleY = backgroundBitmap.height.toFloat() / binding.cropView.height

        // === Tính vị trí center của ivRotate (ảnh overlay) so với cropView ===
        val cropViewLocation = IntArray(2)
        val ivRotateLocation = IntArray(2)

        binding.cropView.getLocationOnScreen(cropViewLocation)
        binding.ivRotate.getLocationOnScreen(ivRotateLocation)

        val relativeCenterX = (ivRotateLocation[0] - cropViewLocation[0]) + binding.ivRotate.width / 2f
        val relativeCenterY = (ivRotateLocation[1] - cropViewLocation[1]) + binding.ivRotate.height / 2f

        // Chuyển sang toạ độ bitmap thật
        val drawX = relativeCenterX * scaleX - foregroundBitmap.width / 2f
        val drawY = relativeCenterY * scaleY - foregroundBitmap.height / 2f

        // Vẽ ảnh foreground
        canvas.drawBitmap(foregroundBitmap, drawX, drawY, null)

        return resultBitmap
    }
    fun Bitmap.trimTransparent(): Bitmap {
        val width = width
        val height = height
        var top = 0
        var bottom = height

        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        // Tìm phần trên
        loop@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != 0) {
                    top = y
                    break@loop
                }
            }
        }

        // Tìm phần dưới
        loop@ for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != 0) {
                    bottom = y
                    break@loop
                }
            }
        }

        return Bitmap.createBitmap(this, 0, top, width, bottom - top)
    }




    fun addWatermark(): Bitmap? {

        val mergedBitmap = mergeBitmaps() ?: return null

        val resultBitmap = createBitmap(mergedBitmap.width, mergedBitmap.height)

        val watermarkBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_watermark)

        val canvas = Canvas(resultBitmap)

        canvas.drawBitmap(mergedBitmap,0f,0f, null)

        canvas.drawBitmap(
            watermarkBitmap,
            (mergedBitmap.width - watermarkBitmap.width).toFloat(),
            (mergedBitmap.height - watermarkBitmap.height).toFloat(),
            null
        )

        return resultBitmap
    }

}