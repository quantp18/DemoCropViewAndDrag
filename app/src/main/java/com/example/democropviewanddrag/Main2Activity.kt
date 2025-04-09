package com.example.democropviewanddrag

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.View
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
//        binding.cropView.viewTreeObserver.addOnGlobalLayoutListener {
//            val cropRect = binding.cropView.mCropRectFClone
//            if (cropRect.width() > 0 && cropRect.height() > 0) {
//                // Cập nhật kích thước và vị trí của CropZoomView
//                val layoutParams = binding.ivRotate.layoutParams
//                layoutParams.width = cropRect.width().toInt()
//                layoutParams.height = cropRect.height().toInt()
//                binding.ivRotate.layoutParams = layoutParams
//
//                // Căn chỉnh vị trí của CropZoomView
//                binding.ivRotate.x = cropRect.left
//                binding.ivRotate.y = cropRect.top
//
//                // Điều chỉnh tỷ lệ của backgroundRect trong CropZoomView
//                val ratio = cropRect.width() / cropRect.height()
//                binding.ivRotate.adjustToRatio(ratio)
//            }
//        }
    }

    private fun mergeBitmaps(): Bitmap? {
        // Lấy bitmap từ CropImageView (ảnh nền đã crop)
        val backgroundBitmap = binding.cropView.getCropBitmap() ?: return null

        // Lấy bitmap và vị trí từ CropZoomView (ảnh trên nền)
        val foregroundBitmap = binding.ivRotate.getBitmap() ?: return null
        val foregroundPosition = binding.ivRotate.getBitmapPosition()

        // Tạo bitmap kết quả với kích thước của ảnh nền
        Log.e("TAG", "mergeBitmaps: bgBitmap => ${backgroundBitmap.width} - ${backgroundBitmap.height}")
        val resultBitmap = createBitmap(backgroundBitmap.width, backgroundBitmap.height)
        val canvas = Canvas(resultBitmap)

        // Vẽ ảnh nền lên canvas
        canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)

        // Tính toán vị trí của ảnh trên nền dựa trên foregroundPosition
        // foregroundPosition là tọa độ trong không gian của CropZoomView
        // Cần điều chỉnh để khớp với kích thước của backgroundBitmap
        val scaleX = backgroundBitmap.width.toFloat() / binding.cropView.width.toFloat()
        val scaleY = backgroundBitmap.height.toFloat() / binding.cropView.height.toFloat()
        val left = foregroundPosition.left * scaleX
        val top = foregroundPosition.top * scaleY

        // Vẽ ảnh trên nền lên canvas với vị trí đã điều chỉnh
        Log.e("TAG", "mergeBitmaps: left-top => ${left} - ${top}")

        canvas.drawBitmap(foregroundBitmap, left, top, null)

        return resultBitmap
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