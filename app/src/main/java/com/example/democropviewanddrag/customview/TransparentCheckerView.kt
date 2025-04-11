package com.example.democropviewanddrag.customview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class TransparentCheckerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paintLight = Paint().apply { color = "#CCCCCC".toColorInt() }
    private val paintDark = Paint().apply { color = "#FFFFFF".toColorInt() }
    private val blockSize = 40  // Size of each square

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rows = height / blockSize + 1
        val cols = width / blockSize + 1

        for (row in 0..rows) {
            for (col in 0..cols) {
                val paint = if ((row + col) % 2 == 0) paintLight else paintDark
                canvas.drawRect(
                    (col * blockSize).toFloat(),
                    (row * blockSize).toFloat(),
                    ((col + 1) * blockSize).toFloat(),
                    ((row + 1) * blockSize).toFloat(),
                    paint
                )
            }
        }
    }
}
