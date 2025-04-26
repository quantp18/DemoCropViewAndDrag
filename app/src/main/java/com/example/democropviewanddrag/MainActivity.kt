package com.example.democropviewanddrag

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding
import com.example.democropviewanddrag.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.btnCrop.setOnClickListener {
//            binding.cropView.getCropBitmap(512)
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
//            binding.ivPreview.setImageBitmap(binding.cropView.getCropBitmap())
        }

    }
}