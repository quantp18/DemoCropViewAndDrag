package com.example.democropviewanddrag.customview

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withMatrix
import androidx.core.graphics.withTranslation
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.drawToBitmap
import com.example.democropviewanddrag.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.content.withStyledAttributes

class CropImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), View.OnLayoutChangeListener {

    companion object {
        private const val DEFAULT_MAX_SCALE = 3
        private const val DEFAULT_ANIM_DURATION = 200L
        private const val DEFAULT_LINE_ANIM_DURATION = 200L
        private const val DEFAULT_CROP_MASK_COLOR = 0x99000000.toInt()
    }

    private var mAnimDuration = DEFAULT_ANIM_DURATION
    private var mScaleEnable: Boolean = true
    private var mMaxScale = DEFAULT_MAX_SCALE
    private val mBaseMatrix: Matrix = Matrix()
    private val mSuppMatrix: Matrix = Matrix()
    private val mDrawMatrix: Matrix = Matrix()
    private val mMatrixValues = FloatArray(9)
    private var mLastScaleFocusX: Float = 0f
    private var mLastScaleFocusY: Float = 0f
    private var mUpAnim: ValueAnimator? = null
    private val mCropOutRectF = RectF()
    private var mCropRatioWidth: Float = 3f
    private var mCropRatioHeight: Float = 2f
    private var mCropBackground: Int = DEFAULT_CROP_MASK_COLOR
    private var mCropRectPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private var mCropRectF = RectF()
    private var mBackgroundPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mCropRectBorderPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var mCropRectBorderWidth: Float = 0f
    private var mCropRectBorderColor: Int = Color.WHITE
    private var mCornerRadius = 20f
    private var mCropRectBorderRectF = RectF()
    private val mCropPointBorderPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var mCropPointBorderWidth = 5f
    private var mCropPointBorderColor: Int = Color.WHITE
    private val mFourCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cornerBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.border_item)
    }
    private var mShowCropLine = true
    private var mCropLinesWidth = 4f
    private val mCropSubLinesPath = Path()
    private val mCropLinesPathPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var mShowCropLinesPathAnim: ValueAnimator? = null
    private var mHideCropLinesPathAnim: ValueAnimator? = null
    private var mCropLinesAnimDuration = DEFAULT_LINE_ANIM_DURATION
    private val touchAreaSize = 80f // Tăng để dễ chạm
    private var showClipPath: Boolean = false

    private enum class DragCorner {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER_TOP, CENTER_LEFT, CENTER_RIGHT, CENTER_BOTTOM
    }

    private var activeDragCorner = DragCorner.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    val mCropRectFClone get() = RectF(mCropRectF)
    var onCropRectChangedListener: ((RectF) -> Unit)? = null

    private val mOnGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            mSuppMatrix.postTranslate(-distanceX, -distanceY)
            checkAndDisplayMatrix()
            return true
        }
    }

    private val mOnScaleGestureListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (mScaleEnable) {
                val curScale = getScale(mSuppMatrix)
                var scaleFactor = detector.scaleFactor
                val newScale = curScale * scaleFactor
                if (newScale > mMaxScale) {
                    scaleFactor = mMaxScale / curScale
                }
                mLastScaleFocusX = detector.focusX
                mLastScaleFocusY = detector.focusY
                mSuppMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                checkAndDisplayMatrix()
            }
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mLastScaleFocusX = detector.focusX
            mLastScaleFocusY = detector.focusY
            return true
        }
    }

    private val mGestureDetector = GestureDetectorCompat(context, mOnGestureListener)
    private val mScaleGestureDetector = ScaleGestureDetector(context, mOnScaleGestureListener)

    init {
        context.withStyledAttributes(attrs, R.styleable.CropImageView) {
            mShowCropLine = getBoolean(R.styleable.CropImageView_civ_show_crop_line, true)
            mCropRatioWidth = getFloat(R.styleable.CropImageView_civ_crop_ratio_width, 1f)
            mCropRatioHeight = getFloat(R.styleable.CropImageView_civ_crop_ratio_height, 1f)
            mCropLinesWidth = getDimension(R.styleable.CropImageView_civ_crop_line_width, 4f)
            mCropBackground =
                getColor(R.styleable.CropImageView_civ_crop_mask_color, DEFAULT_CROP_MASK_COLOR)
            mCropRectBorderWidth = getDimension(R.styleable.CropImageView_civ_crop_border_width, 4f)
            mCropRectBorderColor =
                getColor(R.styleable.CropImageView_civ_crop_border_color, Color.WHITE)
        }

        scaleType = ScaleType.MATRIX
        mCropRectPaint.xfermode = mXfermode
        mCropLinesPathPaint.apply {
            style = Paint.Style.FILL_AND_STROKE
            color = Color.WHITE
            strokeWidth = mCropLinesWidth
            alpha = 0
        }
        mCropRectBorderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = mCropRectBorderWidth
            color = mCropRectBorderColor
        }
        mCropPointBorderPaint.apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = mCropPointBorderWidth
            color = mCropPointBorderColor
            alpha = 0
        }
        mBackgroundPaint.apply {
            style = Paint.Style.FILL
            color = mCropBackground
        }
        mFourCornerPaint.alpha = 0
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        updateCropRect()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCropRect()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        Log.d("CropImageView", "Touch event: action=${event.action}, x=$x, y=$y")

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                mUpAnim?.cancel()
                if (mShowCropLine) showCliPath()
                activeDragCorner = detectTouchCorner(x, y)
                lastTouchX = x
                lastTouchY = y
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeDragCorner != DragCorner.NONE) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    resizeCropRect(dx, dy, activeDragCorner)
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeDragCorner != DragCorner.NONE) {
                    activeDragCorner = DragCorner.NONE
                    hideClipPath()
                    val newRatio = mCropRectBorderRectF.width() / mCropRectBorderRectF.height()
                    val (w, h) = floatToFraction(newRatio)
                    Log.d("CropImageView", "floatToFraction: w=$w, h=$h")
                    setCropRatio(w.toFloat(), h.toFloat())
                    return true
                }
                scaleAndTranslateToCenter()
                hideClipPath()
                mLastScaleFocusX = 0f
                mLastScaleFocusY = 0f
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }

        mGestureDetector.onTouchEvent(event)
        mScaleGestureDetector.onTouchEvent(event)
        return true
    }

    private fun floatToFraction(value: Float, maxDenominator: Int = 100): Pair<Int, Int> {
        var bestNumerator = 1
        var bestDenominator = 1
        var bestError = abs(value - bestNumerator.toFloat() / bestDenominator)

        for (denominator in 1..maxDenominator) {
            val numerator = (value * denominator).roundToInt()
            if (numerator == 0) continue
            val error = abs(value - numerator.toFloat() / denominator)
            if (error < bestError) {
                bestNumerator = numerator
                bestDenominator = denominator
                bestError = error
            }
        }
        return Pair(bestNumerator, bestDenominator)
    }

    private fun resizeCropRect(dx: Float, dy: Float, corner: DragCorner) {
        val newRect = RectF(mCropRectF)
        when (corner) {
            DragCorner.TOP_LEFT -> {
                newRect.left += dx
                newRect.top += dy
            }
            DragCorner.TOP_RIGHT -> {
                newRect.right += dx
                newRect.top += dy
            }
            DragCorner.BOTTOM_LEFT -> {
                newRect.left += dx
                newRect.bottom += dy
            }
            DragCorner.BOTTOM_RIGHT -> {
                newRect.right += dx
                newRect.bottom += dy
            }
            DragCorner.CENTER_TOP -> newRect.top += dy
            DragCorner.CENTER_LEFT -> newRect.left += dx
            DragCorner.CENTER_RIGHT -> newRect.right += dx
            DragCorner.CENTER_BOTTOM -> newRect.bottom += dy
            else -> return
        }

        val viewBounds = RectF(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            width - paddingRight.toFloat(),
            height - paddingBottom.toFloat()
        )
        newRect.intersect(viewBounds)

        if (newRect.width() < 50f || newRect.height() < 50f) return

        mCropRectF.set(newRect)
        mCropRectBorderRectF.set(newRect)
        mCropRectBorderRectF.inset(mCropRectBorderWidth / 2f, mCropRectBorderWidth / 2f)
        updateCropSubLines()
        onCropRectChangedListener?.invoke(mCropRectF)
        Log.d("CropImageView", "Crop rect updated: $mCropRectF")
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        val backgroundRect = RectF(0f, 0f, width.toFloat() - 10, height.toFloat())
        canvas.drawRoundRect(backgroundRect, mCornerRadius, mCornerRadius, mBackgroundPaint)
        canvas.drawRoundRect(mCropRectF, mCornerRadius, mCornerRadius, mCropRectPaint)
        canvas.restoreToCount(layer)
        if (mShowCropLine) {
            canvas.drawPath(mCropSubLinesPath, mCropLinesPathPaint)
        }
        canvas.drawRoundRect(mCropRectBorderRectF, mCornerRadius, mCornerRadius, mCropRectBorderPaint)
        drawEdgeLines(canvas)
    }

    private fun drawEdgeLines(canvas: Canvas) {
        val rect = mCropRectF
        val lineThickness = mCropPointBorderWidth

        canvas.drawRoundRect(
            RectF(rect.centerX() - 30f, rect.top - lineThickness, rect.centerX() + 30f, rect.top + lineThickness),
            lineThickness, lineThickness, mCropPointBorderPaint
        )
        canvas.drawRoundRect(
            RectF(rect.centerX() - 30f, rect.bottom - lineThickness, rect.centerX() + 30f, rect.bottom + lineThickness),
            lineThickness, lineThickness, mCropPointBorderPaint
        )
        canvas.drawRoundRect(
            RectF(rect.left - lineThickness, rect.centerY() - 30f, rect.left + lineThickness, rect.centerY() + 30f),
            lineThickness, lineThickness, mCropPointBorderPaint
        )
        canvas.drawRoundRect(
            RectF(rect.right - lineThickness, rect.centerY() - 30f, rect.right + lineThickness, rect.centerY() + 30f),
            lineThickness, lineThickness, mCropPointBorderPaint
        )
        drawCornerBitmaps(canvas, mCropRectBorderRectF)
    }

    private fun drawCornerBitmaps(canvas: Canvas, rect: RectF) {
        val cornerSize = 60
        val scaledBitmap = cornerBitmap.scale(cornerSize, cornerSize)
        canvas.drawBitmap(scaledBitmap, rect.left - 10f, rect.top - 10f, mFourCornerPaint)
        canvas.withTranslation(rect.right - scaledBitmap.width + 10f, rect.top - 10f) {
            rotate(90f)
            drawBitmap(scaledBitmap, 0f, -scaledBitmap.height.toFloat(), mFourCornerPaint)
        }
        canvas.withTranslation(rect.left - 10f, rect.bottom - scaledBitmap.height + 10) {
            rotate(-90f)
            drawBitmap(scaledBitmap, -scaledBitmap.width.toFloat(), 0f, mFourCornerPaint)
        }
        canvas.withTranslation(rect.right - scaledBitmap.width + 10, rect.bottom - scaledBitmap.height + 10) {
            rotate(180f)
            drawBitmap(scaledBitmap, -scaledBitmap.width.toFloat(), -scaledBitmap.height.toFloat(), mFourCornerPaint)
        }
    }

    private fun hideBorderImmediate() {
        mCropLinesPathPaint.alpha = 0
        mCropPointBorderPaint.alpha = 0
        mFourCornerPaint.alpha = 0
    }

    fun setCornerRadius(radius: Float) {
        mCornerRadius = radius
        invalidate()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        update()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        update()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        update()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        update()
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val change = super.setFrame(l, t, r, b)
        if (change) {
            update()
        }
        return change
    }

    override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix()
        }
    }

    private fun updateCropRect() {
        val viewWidth = (measuredWidth - paddingLeft - paddingRight).toFloat()
        val viewHeight = (measuredHeight - paddingTop - paddingBottom).toFloat()
        if (viewWidth <= 0 || viewHeight <= 0 || mCropRatioWidth <= 0 || mCropRatioHeight <= 0) {
            Log.w("CropImageView", "Invalid dimensions or crop ratio: viewWidth=$viewWidth, viewHeight=$viewHeight, ratio=$mCropRatioWidth:$mCropRatioHeight")
            return
        }

        val rectWidth: Float
        val rectHeight: Float
        if (mCropRatioWidth > mCropRatioHeight) {
            rectWidth = min(viewWidth * 0.98f, viewWidth) // Giới hạn để tránh tràn
            rectHeight = rectWidth / mCropRatioWidth * mCropRatioHeight
        } else {
            rectHeight = min(viewHeight * 0.98f, viewHeight)
            rectWidth = rectHeight / mCropRatioHeight * mCropRatioWidth
        }

        // Đảm bảo rectWidth và rectHeight không vượt quá view bounds
        val finalWidth = min(rectWidth, viewWidth)
        val finalHeight = min(rectHeight, viewHeight)

        val deltaX = (viewWidth - finalWidth) / 2f + paddingLeft
        val deltaY = (viewHeight - finalHeight) / 2f + paddingTop

        mCropOutRectF.set(deltaX, deltaY, deltaX + finalWidth, deltaY + finalHeight)
        mCropRectF.set(mCropOutRectF)
        mCropRectBorderRectF.set(mCropOutRectF)
        mCropRectBorderRectF.inset(mCropRectBorderWidth / 2f, mCropRectBorderWidth / 2f)

        updateCropSubLines()
        onCropRectChangedListener?.invoke(mCropRectF)
        Log.d("CropImageView", "Updated crop rect: $mCropRectF, ratio=$mCropRatioWidth:$mCropRatioHeight")
    }

    private fun updateBaseMatrix() {
        if (drawable == null) {
            return
        }
        updateCropRect()

        val viewWidth = mCropRectF.width()
        val viewHeight = mCropRectF.height()
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val widthScale = viewWidth / drawableWidth
        val heightScale = viewHeight / drawableHeight
        val scale = max(widthScale, heightScale) // Sử dụng max để đảm bảo ảnh vừa khung
        val deltaX = (viewWidth - drawableWidth * scale) / 2f + mCropRectF.left - paddingLeft
        val deltaY = (viewHeight - drawableHeight * scale) / 2f + mCropRectF.top - paddingTop
        mBaseMatrix.reset()
        mBaseMatrix.postScale(scale, scale)
        mBaseMatrix.postTranslate(deltaX, deltaY)
        resetMatrix()
    }

    private fun scaleAndTranslateToCenter() {
        if (drawable == null) {
            return
        }
        mUpAnim?.cancel()
        val displayRectF = RectF()
        displayRectF.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        getDrawMatrix().mapRect(displayRectF)

        val baseRect = RectF()
        baseRect.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        mBaseMatrix.mapRect(baseRect)

        val oldScale = getScale(mSuppMatrix)
        if (oldScale >= 1) {
            val rw = displayRectF.width()
            val rh = displayRectF.height()
            val viewWidth = mCropRectF.width()
            val viewHeight = mCropRectF.height()
            var deltaX = 0f
            var deltaY = 0f
            if (rw <= viewWidth) {
                deltaX = (viewWidth - rw) / 2f - displayRectF.left
            }
            if (rh <= viewHeight) {
                deltaY = (viewHeight - rh) / 2f - displayRectF.top
            }
            if (rw >= viewWidth && displayRectF.left > mCropRectF.left) {
                deltaX = mCropRectF.left - displayRectF.left - paddingLeft
            }
            if (rw >= viewWidth && displayRectF.right < mCropRectF.right) {
                deltaX = mCropRectF.right - displayRectF.right - paddingLeft
            }
            if (rh >= viewHeight && displayRectF.top > mCropRectF.top) {
                deltaY = mCropRectF.top - displayRectF.top - paddingTop
            }
            if (rh >= viewHeight && displayRectF.bottom < mCropRectF.bottom) {
                deltaY = mCropRectF.bottom - displayRectF.bottom - paddingTop
            }
            if (deltaX == 0f && deltaY == 0f) {
                return
            }
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = mAnimDuration
                interpolator = LinearInterpolator()
                addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
                    private var lastValue: Float = 0f
                    override fun onAnimationUpdate(animation: ValueAnimator) {
                        val value = animation.animatedValue as Float
                        var diff = value - lastValue
                        if (diff < 0f) {
                            diff = 0f
                        }
                        val transX = deltaX * diff
                        val transY = deltaY * diff
                        mSuppMatrix.postTranslate(transX, transY)
                        checkAndDisplayMatrix()
                        lastValue = value
                    }
                })
            }.also {
                mUpAnim = it
            }.start()
        } else {
            val startX = getValue(mSuppMatrix, Matrix.MTRANS_X)
            val startY = getValue(mSuppMatrix, Matrix.MTRANS_Y)
            val deltaScale = 1 - oldScale
            val baseCropDeltaX = (baseRect.width() - mCropRectF.width()) / 2f
            val baseCropDeltaY = (baseRect.height() - mCropRectF.height()) / 2f
            val endX = if (displayRectF.left > mCropRectF.left) {
                baseCropDeltaX
            } else if (displayRectF.right < mCropRectF.right) {
                -baseCropDeltaX
            } else {
                startX
            }
            val endY = if (displayRectF.top > mCropRectF.top) {
                baseCropDeltaY
            } else if (displayRectF.bottom < mCropRectF.bottom) {
                -baseCropDeltaY
            } else {
                startY
            }
            val deltaX = endX - startX
            val deltaY = endY - startY
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = mAnimDuration
                interpolator = LinearInterpolator()
                addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
                    private var lastValue: Float = 0f
                    override fun onAnimationUpdate(animation: ValueAnimator) {
                        val value = animation.animatedValue as Float
                        val scale = oldScale + value * deltaScale
                        val transX = startX + value * deltaX
                        val transY = startY + value * deltaY
                        mSuppMatrix.reset()
                        mSuppMatrix.postScale(scale, scale)
                        mSuppMatrix.postTranslate(transX, transY)
                        checkAndDisplayMatrix()
                        lastValue = value
                    }
                })
            }.also {
                mUpAnim = it
            }.start()
        }
    }

    private fun update() {
        if (mSuppMatrix != null) {
            if (mScaleEnable) {
                updateBaseMatrix()
            } else {
                resetMatrix()
            }
        }
    }

    private fun getDrawMatrix(): Matrix {
        mDrawMatrix.set(mBaseMatrix)
        mDrawMatrix.postConcat(mSuppMatrix)
        return mDrawMatrix
    }

    private fun resetMatrix() {
        mSuppMatrix.reset()
        checkAndDisplayMatrix()
    }

    private fun checkAndDisplayMatrix() {
        imageMatrix = getDrawMatrix()
    }

    private fun getScale(matrix: Matrix): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[Matrix.MSCALE_Y]
    }

    private fun getValue(matrix: Matrix, valueType: Int): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[valueType]
    }

    private fun showCliPath() {
        showClipPath = true
        mShowCropLinesPathAnim?.cancel()
        mHideCropLinesPathAnim?.cancel()
        val alpha = mCropLinesPathPaint.alpha
        if (alpha == 255) {
            return
        }
        ValueAnimator.ofInt(alpha, 255).apply {
            duration = mCropLinesAnimDuration
            interpolator = LinearInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                mCropLinesPathPaint.alpha = value
                mFourCornerPaint.alpha = value
                mCropPointBorderPaint.alpha = value
                invalidate()
            }
        }.also {
            mShowCropLinesPathAnim = it
        }.start()
    }

    private fun hideClipPath() {
        showClipPath = false
        mShowCropLinesPathAnim?.cancel()
        mHideCropLinesPathAnim?.cancel()
        val alpha = mCropLinesPathPaint.alpha
        if (alpha == 0) {
            return
        }
        ValueAnimator.ofInt(alpha, 0).apply {
            duration = mCropLinesAnimDuration
            interpolator = LinearInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                mCropLinesPathPaint.alpha = value
                mFourCornerPaint.alpha = value
                mCropPointBorderPaint.alpha = value
                invalidate()
            }
        }.also {
            mHideCropLinesPathAnim = it
        }.start()
    }

    fun toggleClipPath() {
        showClipPath = !showClipPath
        if (showClipPath) {
            showCliPath()
        } else {
            hideClipPath()
        }
    }

    fun setCropRatio(width: Float, height: Float) {
        mCropRatioWidth = width
        mCropRatioHeight = height
        updateCropRect()
        updateBaseMatrix()
    }

    fun setCropRatioOriginal(): Pair<Int, Int> {
        val drawableWidth = drawable?.intrinsicWidth ?: 1
        val drawableHeight = drawable?.intrinsicHeight ?: 1
        mCropRatioWidth = drawableWidth.toFloat()
        mCropRatioHeight = drawableHeight.toFloat()
        updateCropRect()
        updateBaseMatrix()
        return Pair(drawableWidth, drawableHeight)
    }

    fun getAccurateCropBitmap(haveBorder: Boolean = false, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        if (drawable == null) {
            Log.w("CropImageView", "Drawable is null")
            return null
        }

        val currentBorder = mCornerRadius
        val currentAlpha = mCropRectBorderPaint.alpha
        if (showClipPath) {
            hideBorderImmediate()
            hideClipPath()
        }
        if (!haveBorder) {
            mCropRectBorderPaint.alpha = 0
            setCornerRadius(0f)
        }

        // Tạo bitmap từ view
        val fullBitmap = try {
            this.drawToBitmap(config)
        } catch (e: OutOfMemoryError) {
            Log.e("CropImageView", "Out of memory while creating bitmap", e)
            return null
        } catch (e: Exception) {
            Log.e("CropImageView", "Error creating bitmap", e)
            return null
        } ?: return null

        // Tính toán tỷ lệ giữa bitmap và view
        val scaleX = fullBitmap.width.toFloat() / width.toFloat()
        val scaleY = fullBitmap.height.toFloat() / height.toFloat()
        Log.d("CropImageView", "Scale factors: scaleX=$scaleX, scaleY=$scaleY")

        // Tính toán tọa độ và kích thước vùng crop trên bitmap
        var left = (mCropRectF.left * scaleX).toInt()
        var top = (mCropRectF.top * scaleY).toInt()
        var width = (mCropRectF.width() * scaleX).toInt()
        var height = (mCropRectF.height() * scaleY).toInt()

        // Giới hạn tọa độ và kích thước để tránh vượt quá bitmap
        left = max(0, min(left, fullBitmap.width - 1))
        top = max(0, min(top, fullBitmap.height - 1))
        width = min(width, fullBitmap.width - left)
        height = min(height, fullBitmap.height - top)

        if (width <= 0 || height <= 0) {
            Log.e("CropImageView", "Invalid crop dimensions: width=$width, height=$height")
            return null
        }

        // Cắt bitmap
        val croppedBitmap = try {
            Bitmap.createBitmap(fullBitmap, left, top, width, height)
        } catch (e: IllegalArgumentException) {
            Log.e("CropImageView", "Failed to crop bitmap: left=$left, top=$top, width=$width, height=$height", e)
            return null
        }

        // Khôi phục trạng thái
        if (!haveBorder) {
            mCropRectBorderPaint.alpha = currentAlpha
            setCornerRadius(currentBorder)
        }

        return croppedBitmap
    }

    private fun updateCropSubLines() {
        mCropSubLinesPath.reset()
        val widthLineLength = mCropRectF.width() / 3f
        val heightLineLength = mCropRectF.height() / 3f

        mCropSubLinesPath.moveTo(mCropRectF.left + widthLineLength, mCropRectF.top)
        mCropSubLinesPath.lineTo(mCropRectF.left + widthLineLength, mCropRectF.bottom)
        mCropSubLinesPath.moveTo(mCropRectF.left + widthLineLength * 2, mCropRectF.top)
        mCropSubLinesPath.lineTo(mCropRectF.left + widthLineLength * 2, mCropRectF.bottom)
        mCropSubLinesPath.moveTo(mCropRectF.left, mCropRectF.top + heightLineLength)
        mCropSubLinesPath.lineTo(mCropRectF.right, mCropRectF.top + heightLineLength)
        mCropSubLinesPath.moveTo(mCropRectF.left, mCropRectF.top + heightLineLength * 2)
        mCropSubLinesPath.lineTo(mCropRectF.right, mCropRectF.top + heightLineLength * 2)
    }

    private fun detectTouchCorner(x: Float, y: Float): DragCorner {
        val rect = mCropRectF
        val touchSize = touchAreaSize

        if (x >= rect.left - touchSize && x <= rect.left + touchSize &&
            y >= rect.top - touchSize && y <= rect.top + touchSize
        ) {
            return DragCorner.TOP_LEFT
        }
        if (x >= rect.right - touchSize && x <= rect.right + touchSize &&
            y >= rect.top - touchSize && y <= rect.top + touchSize
        ) {
            return DragCorner.TOP_RIGHT
        }
        if (x >= rect.left - touchSize && x <= rect.left + touchSize &&
            y >= rect.bottom - touchSize && y <= rect.bottom + touchSize
        ) {
            return DragCorner.BOTTOM_LEFT
        }
        if (x >= rect.right - touchSize && x <= rect.right + touchSize &&
            y >= rect.bottom - touchSize && y <= rect.bottom + touchSize
        ) {
            return DragCorner.BOTTOM_RIGHT
        }
        if (y >= rect.top - touchSize && y <= rect.top + touchSize &&
            x >= rect.left && x <= rect.right
        ) {
            return DragCorner.CENTER_TOP
        }
        if (y >= rect.bottom - touchSize && y <= rect.bottom + touchSize &&
            x >= rect.left && x <= rect.right
        ) {
            return DragCorner.CENTER_BOTTOM
        }
        if (x >= rect.left - touchSize && x <= rect.left + touchSize &&
            y >= rect.top && y <= rect.bottom
        ) {
            return DragCorner.CENTER_LEFT
        }
        if (x >= rect.right - touchSize && x <= rect.right + touchSize &&
            y >= rect.top && y <= rect.bottom
        ) {
            return DragCorner.CENTER_RIGHT
        }
        return DragCorner.NONE
    }
    // Trong CropImageView.kt
    fun getCropRect(): RectF {
        return RectF(mCropRectF)
    }
    fun getCropBorderRect(): RectF {
        return RectF(mCropRectBorderRectF)
    }
}