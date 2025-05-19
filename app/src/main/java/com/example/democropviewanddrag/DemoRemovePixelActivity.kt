package com.example.democropviewanddrag

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.democropviewanddrag.customview.ToolMode
import com.example.democropviewanddrag.databinding.ActivityDemoRemovePixelBinding

class DemoRemovePixelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDemoRemovePixelBinding

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDemoRemovePixelBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // Thiết lập ảnh (ví dụ: lấy từ resources)
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.content)
        binding.removePixelView.setImageBitmap(bitmap)

        // Khi người dùng nhấn nút để gửi mặt nạ
//        binding.button.setOnClickListener {
//            val maskBitmap = binding.removePixelView.getMaskBitmap()
//            Log.e("TAG", "onCreate: ${maskBitmap == null}")
//            maskBitmap?.let {
//                // Gửi maskBitmap đến backend (ví dụ: qua API)
//            }
//        }

        binding.removePixelView.setBrushSize(10f)

        // Lắng nghe thay đổi từ SeekBar
        binding.brushSizeSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val brushSize = progress.toFloat()
                binding.removePixelView.setBrushSize(brushSize)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        var currentModeTool = ToolMode.BRUSH

        // Thay đổi màu bút vẽ
        binding.toggleOption.setOnClickListener {
            currentModeTool = if (currentModeTool == ToolMode.BRUSH) ToolMode.LASSO else ToolMode.BRUSH
            binding.removePixelView.setToolMode(currentModeTool)
        }
        binding.blueButton.setOnClickListener { binding.removePixelView.setBrushColor(Color.BLUE) }
        binding.greenButton.setOnClickListener { binding.removePixelView.setBrushColor(Color.GREEN) }

        // Undo và Redo
        binding.undoButton.setOnClickListener { binding.removePixelView.undo() }
        binding.redoButton.setOnClickListener { binding.removePixelView.redo() }


        binding.undoButton.setOnLongClickListener {
            binding.imgContent.setImageBitmap(binding.removePixelView.getMarkBitmap())
            true
        }
    }
}