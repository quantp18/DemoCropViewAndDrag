package com.example.democropviewanddrag.customview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.example.democropviewanddrag.R
import kotlin.math.min

/**
 * Custom view combining Background cropping and Foreground transform (drag/zoom/rotate).
 * - BG: set, crop by ratio
 * - FG: set, drag/zoom/rotate with limit and null-guards
 * - Provides methods to get cropped BG, transformed FG, and merged image
 */
class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- Background ---
    private var bgBitmap: Bitmap? = null
    private val bgMatrix = Matrix()
    private var cropRatioX = 0f
    private var cropRatioY = 0f
    private var cropRect = RectF()
    private val cropPaint = Paint().apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }

    // --- Foreground ---
    private var fgBitmap: Bitmap? = null
    private val fgMatrix = Matrix()
    private var fgScale = 1f
    private var fgRotation = 0f
    private var fgPosX = 0f
    private var fgPosY = 0f
    private var minFgScale = 0.5f
    private var maxFgScale = 3f

    // Icons
    private val rotateIcon = ContextCompat.getDrawable(context, R.drawable.ic_rotate)!!
    private val scaleIcon = ContextCompat.getDrawable(context, R.drawable.ic_zoom)!!
    private val iconSize = resources.getDimension(R.dimen.editor_icon_size)

    // Gesture detectors
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private enum class Mode { NONE, DRAG, ROTATE, SCALE }
    private var mode = Mode.NONE

    // --- Public API ---

    /** Set background bitmap and reset crop to full */
    fun setBackground(bitmap: Bitmap) {
        bgBitmap = bitmap
        // Fit center
        bgMatrix.reset()
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bW = bitmap.width.toFloat()
        val bH = bitmap.height.toFloat()
        val scale = min(viewW / bW, viewH / bH)
        val dx = (viewW - bW * scale) / 2f
        val dy = (viewH - bH * scale) / 2f
        bgMatrix.setScale(scale, scale)
        bgMatrix.postTranslate(dx, dy)
        // Default crop = full image
        cropRatioX = 0f; cropRatioY = 0f
        cropRect.set(dx, dy, dx + bW * scale, dy + bH * scale)
        invalidate()
    }

    /** Set crop ratio (e.g. 1f, 1f for square) */
    fun setCropRatio(ratioX: Float, ratioY: Float) {
        require(ratioX > 0 && ratioY > 0) { "Crop ratio must be positive" }
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        // Compute cropRect centered
        val targetW = viewW
        val targetH = viewW * (ratioY / ratioX)
        val finalH = if (targetH <= viewH) targetH else viewH
        val finalW = finalH * (ratioX / ratioY)
        val left = (viewW - finalW) / 2f
        val top = (viewH - finalH) / 2f
        cropRect.set(left, top, left + finalW, top + finalH)
        cropRatioX = ratioX; cropRatioY = ratioY
        invalidate()
    }

    /** Set foreground bitmap and reset transform */
    fun setForeground(bitmap: Bitmap) {
        fgBitmap = bitmap
        fgScale = 1f
        fgRotation = 0f
        // Center FG
        fgPosX = (width - bitmap.width) / 2f
        fgPosY = (height - bitmap.height) / 2f
        updateFgMatrix()
        invalidate()
    }

    /** Get cropped background bitmap */
    fun getCroppedBackground(): Bitmap? {
        val bmp = bgBitmap ?: return null
        val inv = Matrix()
        bgMatrix.invert(inv)
        val src = RectF(cropRect)
        inv.mapRect(src)
        return Bitmap.createBitmap(
            bmp,
            src.left.toInt(), src.top.toInt(),
            src.width().toInt(), src.height().toInt()
        )
    }

    /** Get transformed foreground bitmap */
    fun getTransformedForeground(): Bitmap? {
        val bmp = fgBitmap ?: return null
        // Determine FG draw bounds
        val bounds = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        fgMatrix.mapRect(bounds)
        val out = Bitmap.createBitmap(
            bounds.width().toInt(), bounds.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(out)
        // Translate canvas so FG at 0,0
        c.translate(-bounds.left, -bounds.top)
        c.drawBitmap(bmp, fgMatrix, Paint(Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Get merged BG+FG */
    fun getMergedBitmap(): Bitmap? {
        val bg = getCroppedBackground() ?: return null
        val merged = Bitmap.createBitmap(bg.width, bg.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(merged)
        c.drawBitmap(bg, 0f, 0f, null)
        // Draw FG relative to cropRect top-left
        fgMatrix.postTranslate(-cropRect.left, -cropRect.top)
        c.drawBitmap(fgBitmap!!, fgMatrix, Paint(Paint.ANTI_ALIAS_FLAG))
        return merged
    }

    // --- Touch & Gesture Handling ---

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (fgBitmap == null) return false // No FG => ignore
        scaleDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x; lastTouchY = ev.y
                mode = when {
                    isOnRotateIcon(ev.x, ev.y) -> Mode.ROTATE
                    isOnScaleIcon(ev.x, ev.y) -> Mode.SCALE
                    isOnFg(ev.x, ev.y) -> Mode.DRAG
                    else -> Mode.NONE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    Mode.DRAG -> {
                        val dx = ev.x - lastTouchX
                        val dy = ev.y - lastTouchY
                        fgPosX += dx; fgPosY += dy
                        lastTouchX = ev.x; lastTouchY = ev.y
                        updateFgMatrix(); invalidate()
                    }
                    Mode.ROTATE -> {
                        // compute angle delta
                        val cx = fgPosX + fgBitmap!!.width * fgScale / 2f
                        val cy = fgPosY + fgBitmap!!.height * fgScale / 2f
                        val prevAngle = Math.atan2(
                            (lastTouchY - cy).toDouble(), (lastTouchX - cx).toDouble()
                        )
                        val currAngle = Math.atan2(
                            (ev.y - cy).toDouble(), (ev.x - cx).toDouble()
                        )
                        val delta = Math.toDegrees(currAngle - prevAngle).toFloat()
                        fgRotation = (fgRotation + delta) % 360
                        lastTouchX = ev.x; lastTouchY = ev.y
                        updateFgMatrix(); invalidate()
                    }
                    else -> { /* handled by scale detector */ }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = Mode.NONE
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (mode == Mode.NONE) mode = Mode.SCALE
            if (mode == Mode.SCALE) {
                fgScale *= detector.scaleFactor
                fgScale = fgScale.coerceIn(minFgScale, maxFgScale)
                updateFgMatrix(); invalidate()
            }
            return true
        }
    }

    // --- Helpers ---
    private fun updateFgMatrix() {
        fgMatrix.reset()
        fgMatrix.postTranslate(fgPosX, fgPosY)
        fgMatrix.postScale(fgScale, fgScale, fgPosX, fgPosY)
        fgMatrix.postRotate(fgRotation, fgPosX + fgBitmap!!.width*fgScale/2f,
            fgPosY + fgBitmap!!.height*fgScale/2f)
    }

    private fun isOnFg(x: Float, y: Float): Boolean {
        val inv = Matrix()
        if (!fgMatrix.invert(inv)) return false
        val pts = floatArrayOf(x, y)
        inv.mapPoints(pts)
        return pts[0] in 0f..(fgBitmap!!.width.toFloat()) && pts[1] in 0f..(fgBitmap!!.height.toFloat())
    }

    private fun isOnRotateIcon(x: Float, y: Float): Boolean {
        val cx = fgPosX
        val cy = fgPosY
        return RectF(cx, cy, cx+iconSize, cy+iconSize).contains(x, y)
    }

    private fun isOnScaleIcon(x: Float, y: Float): Boolean {
        val cx = fgPosX + fgBitmap!!.width*fgScale - iconSize
        val cy = fgPosY + fgBitmap!!.height*fgScale - iconSize
        return RectF(cx, cy, cx+iconSize, cy+iconSize).contains(x, y)
    }

    // --- Drawing ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw BG
        bgBitmap?.let {
            canvas.drawBitmap(it, bgMatrix, null)
        }
        // Draw crop overlay
        if (cropRatioX>0 && cropRatioY>0) {
            // darken outside cropRect
            canvas.save()
            canvas.clipRect(cropRect, Region.Op.DIFFERENCE)
            canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(), cropPaint)
            canvas.restore()
        }
        // Draw FG
        fgBitmap?.let {
            canvas.drawBitmap(it, fgMatrix, Paint(Paint.ANTI_ALIAS_FLAG))
            // Draw border
            val border = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 4f; color=Color.GREEN
            }
            val bounds = RectF(0f,0f,it.width.toFloat(),it.height.toFloat())
            fgMatrix.mapRect(bounds)
            canvas.drawRect(bounds, border)
            // Draw icons
            rotateIcon.setBounds(
                bounds.left.toInt(), bounds.top.toInt(),
                (bounds.left+iconSize).toInt(), (bounds.top+iconSize).toInt()
            )
            rotateIcon.draw(canvas)
            scaleIcon.setBounds(
                (bounds.right-iconSize).toInt(), (bounds.bottom-iconSize).toInt(),
                bounds.right.toInt(), bounds.bottom.toInt()
            )
            scaleIcon.draw(canvas)
        }
    }
}
