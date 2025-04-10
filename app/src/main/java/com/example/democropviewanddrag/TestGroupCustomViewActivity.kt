package com.example.democropviewanddrag

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.democropviewanddrag.customview.addWatermark
import com.example.democropviewanddrag.databinding.ActivityTestGroupCustomViewBinding

class TestGroupCustomViewActivity : AppCompatActivity() {

    private var binding : ActivityTestGroupCustomViewBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTestGroupCustomViewBinding.inflate(layoutInflater)
        setContentView(binding!!.root)


        binding?.apply {
            viewEditor.setBackgroundImageResource(R.drawable.content2)
            viewEditor.setForegroundImageResource(R.drawable.content)

            btn1.setOnClickListener {
                viewEditor.setCropRatioForBackground(1f, 1f)
            }

            btn2.setOnClickListener {
                viewEditor.setCropRatioForBackground(2f, 3f)
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
                ivPreview.setImageBitmap(viewEditor.getResultBitmap().addWatermark(resources))
            }
        }
    }
}