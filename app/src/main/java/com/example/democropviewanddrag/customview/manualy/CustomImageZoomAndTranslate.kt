package com.example.democropviewanddrag.customview.manualy

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.atan2
import kotlin.math.sqrt


class CustomImageZoomAndTranslate @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), View.OnTouchListener {
    private val TAG: String = "Touch"

    // These matrices will be used to move and zoom image
    private var matrix: Matrix = Matrix()
    private var savedMatrix: Matrix = Matrix()
    private var d = 0f
    private var newRot = 0f
    private var lastEvent: FloatArray = floatArrayOf()

    // We can be in one of these 3 states
    companion object {
        const val NONE: Int = 0
        const val DRAG: Int = 1
        const val ZOOM: Int = 2
    }

    var mode: Int = NONE

    // Remember some things for zooming
    var start: PointF = PointF()
    var mid: PointF = PointF()
    var oldDist: Float = 1f
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val view = v as ImageView
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                start[event.x] = event.y
                mode = DRAG
                lastEvent = FloatArray(4)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                    mode = ZOOM
                }
                lastEvent = FloatArray(4)
                lastEvent[0] = event.getX(0)
                lastEvent[1] = event.getX(1)
                lastEvent[2] = event.getY(0)
                lastEvent[3] = event.getY(1)
                d = rotation(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                lastEvent = FloatArray(4)
            }

            MotionEvent.ACTION_MOVE -> if (mode === DRAG) {
                matrix.set(savedMatrix)
                val dx = event.x - start.x
                val dy = event.y - start.y
                matrix.postTranslate(dx, dy)
            } else if (mode === ZOOM) {
                val newDist = spacing(event)
                if (newDist > 10f) {
                    matrix.set(savedMatrix)
                    val scale = (newDist / oldDist)
                    matrix.postScale(scale, scale, mid.x, mid.y)
                }
                if (lastEvent != null && event.pointerCount === 2) {
                    newRot = rotation(event)
                    val r: Float = newRot - d
                    val values = FloatArray(9)
                    matrix.getValues(values)
                    val tx = values[0]
                    val ty = values[4]
                    val sx = values[8]
                    val xc = (view.width / 2) * sx
                    val yc = (view.height / 2) * sx
                    matrix.postRotate(r, tx + xc, ty + yc)
                }
            }
        }

        view.imageMatrix = matrix
        return true
    }

    private fun rotation(event: MotionEvent): Float {
        val delta_x = (event.getX(0) - event.getX(1)).toDouble()
        val delta_y = (event.getY(0) - event.getY(1)).toDouble()
        val radians = atan2(delta_y, delta_x)

        return Math.toDegrees(radians).toFloat()
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y)
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point[x / 2] = y / 2
    }


    /** Show an event in the LogCat view, for debugging  */
    private fun dumpEvent(event: MotionEvent) {
        val names = arrayOf(
            "DOWN", "UP", "MOVE", "CANCEL", "OUTSIDE",
            "POINTER_DOWN", "POINTER_UP", "7?", "8?", "9?"
        )
        val sb = StringBuilder()
        val action = event.action
        val actionCode = action and MotionEvent.ACTION_MASK
        sb.append("event ACTION_").append(names[actionCode])
        if (actionCode == MotionEvent.ACTION_POINTER_DOWN
            || actionCode == MotionEvent.ACTION_POINTER_UP
        ) {
            sb.append("(pid ").append(
                action shr MotionEvent.ACTION_POINTER_ID_SHIFT
            )
            sb.append(")")
        }

        sb.append("[")

        for (i in 0..<event.pointerCount) {
            sb.append("#").append(i)
            sb.append("(pid ").append(event.getPointerId(i))
            sb.append(")=").append(event.getX(i).toInt())
            sb.append(",").append(event.getY(i).toInt())
            if (i + 1 < event.pointerCount) sb.append(";")
        }

        sb.append("]")
        Log.d(TAG, sb.toString())
    }
}