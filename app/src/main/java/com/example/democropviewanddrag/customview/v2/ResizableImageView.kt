package com.example.democropviewanddrag.customview.v2

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import java.lang.Math.sqrt

class ResizableImageView(context: Context, attrs: AttributeSet) : androidx.appcompat.widget.AppCompatImageView(context, attrs) {

    private var mLastTouchX: Float = 0f
    private var mLastTouchY: Float = 0f
    private var mLastHeight: Int = 0
    private var mLastWidth: Int = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.action
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mLastTouchX = event.x
                mLastTouchY = event.y
                mLastHeight = this.height
                mLastWidth = this.width
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - mLastTouchX
                val deltaY = event.y - mLastTouchY
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY.toDouble()).toFloat()

                val scale = distance / 217 // Adjust this value as needed
                println(scale)
                var newWidth = (150 * scale).toInt()
                var newHeight = (150 * scale).toInt()

                if( newWidth < 80)
                    newWidth = 80
                if( newWidth > 750)
                    newWidth = 750
                if (newHeight < 80)
                    newHeight = 80
                if( newHeight > 750)
                    newHeight = 750
                layoutParams.width = newWidth
                layoutParams.height = newHeight
                requestLayout()
            }
        }
        return true
    }
}