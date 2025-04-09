package com.example.democropviewanddrag.customview.v3

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewTreeObserver
import androidx.appcompat.widget.AppCompatImageView

class RotateScaleZoomTranslateImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var mDetector: MatrixGestureDetector
    private val mMatrix = Matrix()

    init {
        scaleType = ScaleType.MATRIX
        mDetector = MatrixGestureDetector(object : MatrixGestureDetector.OnMatrixChangeListener {


            override fun onMatrixChanged(matrix: Matrix?) {
                mMatrix.set(matrix)
                imageMatrix = mMatrix
            }
        })

        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                centerImage()
                viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun centerImage() {
        val drawable = drawable ?: return

        val viewWidth = width
        val viewHeight = height
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight

        val scaleX = viewWidth.toFloat() / drawableWidth
        val scaleY = viewHeight.toFloat() / drawableHeight
        val scale = Math.min(scaleX, scaleY)

        val translateX = (viewWidth - drawableWidth * scale) / 2f
        val translateY = (viewHeight - drawableHeight * scale) / 2f

        mMatrix.setScale(scale, scale)
        mMatrix.postTranslate(translateX, translateY)
        imageMatrix = mMatrix

        mDetector.setMatrix(mMatrix)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return mDetector.onTouchEvent(event)
    }

    // Add method for 90-degree rotation
    fun rotate90Degrees() {
        mMatrix.postRotate(90f, width / 2f, height / 2f)
        imageMatrix = mMatrix
        mDetector.setMatrix(mMatrix)
    }

    // Add method for zooming (e.g., toggle between 1x and 2x)
    fun toggleZoom() {
        val values = FloatArray(9)
        mMatrix.getValues(values)
        val currentScale = values[Matrix.MSCALE_X]
        val newScale = if (currentScale > 1f) 1f else 2f
        mMatrix.postScale(newScale / currentScale, newScale / currentScale, width / 2f, height / 2f)
        imageMatrix = mMatrix
        mDetector.setMatrix(mMatrix)
    }
}