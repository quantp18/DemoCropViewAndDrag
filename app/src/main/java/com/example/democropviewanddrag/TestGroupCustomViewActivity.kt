package com.example.democropviewanddrag

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.democropviewanddrag.customview.PositionWatermark
import com.example.democropviewanddrag.databinding.ActivityTestGroupCustomViewBinding
import com.example.democropviewanddrag.extension.addWatermark
import com.example.democropviewanddrag.model.PaddingWatermark

class TestGroupCustomViewActivity : AppCompatActivity() {

    private var binding: ActivityTestGroupCustomViewBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTestGroupCustomViewBinding.inflate(layoutInflater)
        setContentView(binding!!.root)


        binding?.apply {
            viewEditor.setBackgroundImageResource(R.drawable.bg_remove)
            viewEditor.setForegroundImageResource(R.drawable.content)

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
                viewEditor.setBackgroundRadius(0f)
            }
        }
    }
}