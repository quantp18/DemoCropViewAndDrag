package com.example.democropviewanddrag

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.example.democropviewanddrag.customview.PositionWatermark
import com.example.democropviewanddrag.databinding.ActivityTestGroupCustomViewBinding
import com.example.democropviewanddrag.extension.addWatermark
import com.example.democropviewanddrag.model.PaddingWatermark
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class TestGroupCustomViewActivity : AppCompatActivity() {

    private var binding: ActivityTestGroupCustomViewBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTestGroupCustomViewBinding.inflate(layoutInflater)
        setContentView(binding!!.root)


        binding?.apply {
            viewEditor.setCropRatioForBackground(1f, 1f)
            viewEditor.setBackgroundImageResource(R.drawable.bg_remove)
            viewEditor.setForegroundImageResource(R.drawable.content)

            btn1.setOnLongClickListener {
                startActivity(
                    Intent(
                        this@TestGroupCustomViewActivity,
                        DemoRemovePixelActivity::class.java
                    )
                )
                true
            }

            btn1.setOnClickListener {
                viewEditor.setCropRatioForBackground(1f, 1f)
            }

            btnCrop.setText("showGrid")
            btnCrop.setOnClickListener {
                viewEditor.showGridClipPath()
            }

            btn2.setText("Original")
            btn2.setOnClickListener {
                viewEditor.setCropRatioOriginal()
            }

            btn3.setOnClickListener {
                viewEditor.setCropRatioForBackground(3f, 2f)
            }

            btn26.setOnClickListener {
                viewEditor.setCropRatioForBackground(6f, 2f)
            }

            btnShow.setOnClickListener {
                ivPreview.setImageBitmap(viewEditor.getResultBitmap())
            }

            btnWatermark.setOnClickListener {
                ivPreview.setImageBitmap(
                    viewEditor.getResultBitmap().addWatermark(
                        resources = resources,
                        padding = PaddingWatermark(top = 16, right = 16),
                        position = PositionWatermark.TOP_END
                    )
                )
            }

            btnSetBorder.setOnClickListener {
//                viewEditor.setBackgroundColorResource("#9CC5FF")

//                viewEditor.clearBackground()
//                viewEditor.setBackgroundRadius(0f)
//                viewEditor.setCropRatioForBackground(1f, 1f)
                ivPreview.setImageURI(
                    viewEditor.getResultBitmap().addWatermark(
                        resources = resources,
                        padding = PaddingWatermark(bottom = 16, right = 16),
                        position = PositionWatermark.BOTTOM_END
                    )!!.let { it1 -> saveBitmapAsPngFileSafe(it1, File(filesDir, "test.png")) }
                )
            }
        }
    }

    fun saveBitmapAsPngFileSafe(bitmap: Bitmap, file: File): Uri? {
        var scaledBitmap = bitmap
        var scaleFactor = 1f
        var countRetry = 2

        while (countRetry > 0) {
            try {
                FileOutputStream(file).use { outputStream ->
                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                return file.toUri()
            } catch (e: OutOfMemoryError) {
                countRetry--
                e.printStackTrace()
                // Giải phóng bitmap cũ nếu không phải bản gốc
                if (scaledBitmap != bitmap) scaledBitmap.recycle()

                // Giảm kích thước xuống 80% mỗi lần
                scaleFactor *= 0.8f
                val newWidth = (bitmap.width * scaleFactor).toInt()
                val newHeight = (bitmap.height * scaleFactor).toInt()

                // Ngừng nếu ảnh quá nhỏ
                if (newWidth < 100 || newHeight < 100) break

                scaledBitmap = bitmap.scale(newWidth, newHeight)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }

        return null
    }

}