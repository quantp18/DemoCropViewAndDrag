package com.example.democropviewanddrag.customview

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.drawToBitmap
import com.example.democropviewanddrag.R
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withMatrix
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), OnLayoutChangeListener {

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

    private val mCropRectBorderPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var mCropRectBorderWidth: Float = 0f
    private var mCropRectBorderColor: Int = Color.WHITE
    private var mCornerRadius = 20f  // Giá trị bán kính bo góc, có thể thay đổi
    private var mCropRectBorderRectF = RectF()

    private var mShowCropLine = true
    private var mCropLinesWidth = 4f

    private val mCropSubLinesPath = Path()
    private val mCropLinesPathPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var mShowCropLinesPathAnim: ValueAnimator? = null
    private var mHideCropLinesPathAnim: ValueAnimator? = null
    private var mCropLinesAnimDuration = DEFAULT_LINE_ANIM_DURATION

    private val touchAreaSize = 40f  // vùng nhạy để bắt drag (có thể tùy chỉnh)

    private enum class DragCorner {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private var activeDragCorner = DragCorner.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f


    val mCropRectFClone get() = RectF(mCropRectF)

    private val mOnGestureListener = object : SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            mSuppMatrix.postTranslate(-distanceX, -distanceY)
            checkAndDisplayMatrix()
            return true
        }
    }
    private val mOnScaleGestureListener = object : SimpleOnScaleGestureListener() {
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
        val ta = context.obtainStyledAttributes(attrs, R.styleable.CropImageView)
        mShowCropLine = ta.getBoolean(R.styleable.CropImageView_civ_show_crop_line, true)
        mCropRatioWidth = ta.getFloat(R.styleable.CropImageView_civ_crop_ratio_width, 1f)
        mCropRatioHeight = ta.getFloat(R.styleable.CropImageView_civ_crop_ratio_height, 1f)
        mCropLinesWidth = ta.getDimension(R.styleable.CropImageView_civ_crop_line_width, 4f)
        mCropBackground = ta.getColor(R.styleable.CropImageView_civ_crop_mask_color, DEFAULT_CROP_MASK_COLOR)
        mCropRectBorderWidth = ta.getDimension(R.styleable.CropImageView_civ_crop_border_width, 2f)
        mCropRectBorderColor = ta.getColor(R.styleable.CropImageView_civ_crop_border_color, Color.WHITE)
        ta.recycle()

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
    }


    //region Rewrite method

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

                    // Lấy tỷ lệ mới từ crop rect sau khi kéo
                    val newRatio = mCropRectBorderRectF.width() / mCropRectBorderRectF.height()
                    val (w, h) = floatToFraction(newRatio)

                    Log.e("TAG", "floatToFraction: w: $w - h : $h", )
                    // Cập nhật tỷ lệ mới
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

    fun floatToFraction(value: Float, maxDenominator: Int = 100): Pair<Int, Int> {
        var bestNumerator = 1
        var bestDenominator = 1
        var bestError = Math.abs(value - bestNumerator.toFloat() / bestDenominator)

        for (denominator in 1..maxDenominator) {
            val numerator = Math.round(value * denominator)
            val error = Math.abs(value - numerator.toFloat() / denominator)
            if (error < bestError) {
                bestNumerator = numerator
                bestDenominator = denominator
                bestError = error
            }
        }

        return Pair(bestNumerator, bestDenominator)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawColor(mCropBackground)
        canvas.drawRoundRect(mCropRectF, mCornerRadius, mCornerRadius, mCropRectPaint)
        canvas.restoreToCount(layer)
        if (mShowCropLine) {
            canvas.drawPath(mCropSubLinesPath, mCropLinesPathPaint)
        }
        // Vẽ border với các góc bo tròn thay vì vẽ hình chữ nhật thường
        canvas.drawRoundRect(mCropRectBorderRectF, mCornerRadius, mCornerRadius, mCropRectBorderPaint)
    }

    fun setCornerRadius(radius : Float) {
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

    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix()
        }
    }
    //endregion

    private fun updateCropRect() {
        val viewWidth = measuredWidth - paddingLeft - paddingRight
        val viewHeight = measuredHeight - paddingTop - paddingBottom
        if (viewWidth == 0 || viewHeight == 0 || mCropRatioWidth == 0.0f || mCropRatioHeight == 0f) {
            return
        }
        val rectWidth: Float
        val rectHeight: Float
        if (mCropRatioWidth > mCropRatioHeight) {
            rectWidth = viewWidth.toFloat()
            rectHeight = viewWidth / mCropRatioWidth * mCropRatioHeight
        } else if (mCropRatioWidth < mCropRatioHeight) {
            rectHeight = viewHeight.toFloat()
            rectWidth = viewHeight / mCropRatioHeight * mCropRatioWidth
        } else {
            rectWidth = viewWidth.coerceAtMost(viewHeight).toFloat()
            rectHeight = rectWidth
        }
        val deltaX = (viewWidth - rectWidth) / 2f + paddingLeft
        val deltaY = (viewHeight - rectHeight) / 2f + paddingTop

        mCropOutRectF.set(deltaX, deltaY, deltaX + rectWidth, deltaY + rectHeight)
        mCropRectF.set(mCropOutRectF)

        mCropRectBorderRectF.set(mCropOutRectF)
        mCropRectBorderRectF.inset(mCropRectBorderWidth / 2f, mCropRectBorderWidth / 2f)

        mCropSubLinesPath.reset()
        val widthLineLength = rectWidth / 3f
        val heightLineLength = rectHeight / 3f

        mCropSubLinesPath.moveTo(mCropRectF.left + widthLineLength, mCropRectF.top)
        mCropSubLinesPath.lineTo(mCropRectF.left + widthLineLength, mCropRectF.bottom)
        mCropSubLinesPath.moveTo(mCropRectF.left + widthLineLength * 2, mCropRectF.top)
        mCropSubLinesPath.lineTo(mCropRectF.left + widthLineLength * 2, mCropRectF.bottom)

        mCropSubLinesPath.moveTo(mCropRectF.left, mCropRectF.top + heightLineLength)
        mCropSubLinesPath.lineTo(mCropRectF.right, mCropRectF.top + heightLineLength)
        mCropSubLinesPath.moveTo(mCropRectF.left, mCropRectF.top + heightLineLength * 2)
        mCropSubLinesPath.lineTo(mCropRectF.right, mCropRectF.top + heightLineLength * 2)
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
        val scale = widthScale.coerceAtLeast(heightScale)
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
                addUpdateListener(object : AnimatorUpdateListener {
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
                addUpdateListener(object : AnimatorUpdateListener {
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
        //This must be empty. When this method is called, the class has not yet been initialized.
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

    private fun getImageViewWidth(): Int {
        return width - paddingLeft - paddingRight
    }

    private fun getImageViewHeight(): Int {
        return height - paddingTop - paddingBottom
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
                invalidate()
            }
        }.also {
            mShowCropLinesPathAnim = it
        }.start()
    }

    private fun hideClipPath() {
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
                invalidate()
            }
        }.also {
            mHideCropLinesPathAnim = it
        }.start()
    }

    fun setCropRatio(width: Float, height: Float) {
        // Tính tỷ lệ mới dựa trên đầu vào (có thể là kết quả của floatToFraction)
        val computedRatio = width / height  // ví dụ: nếu floatToFraction trả về (76, 71) thì computedRatio ~ 1.070
        val targetRatio = 5f / 4f            // 5:4 ~ 1.25

        // Nếu computedRatio gần với targetRatio (ví dụ sai lệch không quá 15%), snap về targetRatio.
        val finalWidth: Float
        val finalHeight: Float
        if (abs(computedRatio - targetRatio) < 0.15f) {
            finalWidth = 5f
            finalHeight = 4f
        } else {
            finalWidth = width
            finalHeight = height
        }

        // Lưu lại tỷ lệ crop mới
        mCropRatioWidth = finalWidth
        mCropRatioHeight = finalHeight

        // Giữ nguyên trung điểm của vùng crop hiện tại
        val centerX = mCropRectBorderRectF.centerX()
        val centerY = mCropRectBorderRectF.centerY()

        // Giữ lại kích thước hiện tại của vùng crop (nếu có) hoặc tính dựa trên mCropOutRectF
        var newWidth = mCropRectBorderRectF.width()
        var newHeight = newWidth * (finalHeight / finalWidth)

        // Nếu chiều cao tính được vượt quá giới hạn vùng crop tối đa thì điều chỉnh theo chiều cao
        if (newHeight > mCropOutRectF.height()) {
            newHeight = mCropOutRectF.height()
            newWidth = newHeight * (finalWidth / finalHeight)
        }

        // Tính biên mới dựa trên trung điểm, đảm bảo không vượt ra ngoài mCropOutRectF
        val newLeft = max(mCropOutRectF.left, centerX - newWidth / 2f)
        val newTop = max(mCropOutRectF.top, centerY - newHeight / 2f)
        val newRight = min(mCropOutRectF.right, centerX + newWidth / 2f)
        val newBottom = min(mCropOutRectF.bottom, centerY + newHeight / 2f)

        mCropRectBorderRectF.set(newLeft, newTop, newRight, newBottom)
        mCropRectF.set(mCropRectBorderRectF)

        updateBaseMatrix()
        invalidate()
    }


    fun setShowCropLine(show: Boolean) {
        mShowCropLine = show
        invalidate()
    }

    fun setCropLineWidth(width: Float) {
        mCropLinesWidth = width
        mCropLinesPathPaint.strokeWidth = width
        invalidate()
    }

    fun setCropMaskColor(color: Int) {
        mCropBackground = color
        invalidate()
    }

    fun getCropBitmap(baseWidth: Int = 0, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        if (drawable == null) {
            return null
        }
        val width = mCropRectF.width().toInt()
        val height = mCropRectF.height().toInt()
        if (width == 0 || height == 0) {
            return null
        }
        var canvasScale = 1.0f
        var bitmapWidth = width
        var bitmapHeight = height

        if (baseWidth != 0) {
            if (width < height) {
                canvasScale = baseWidth * 1.0f / width
                bitmapWidth = baseWidth
                bitmapHeight = (height * canvasScale).roundToInt()
            } else {
                canvasScale = baseWidth * 1.0f / height
                bitmapWidth = (width * canvasScale).roundToInt()
                bitmapHeight = baseWidth
            }
        }
        val bitmap = createBitmap(bitmapWidth, bitmapHeight, config)
        val canvas = Canvas(bitmap)
        canvas.scale(canvasScale, canvasScale)
        canvas.translate(-mCropRectF.left, -mCropRectF.top)
        canvas.withMatrix(getDrawMatrix()) {
            drawable.draw(this)
        }
        return bitmap
    }

    fun getAccurateCropBitmap(haveBorder : Boolean = false, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        if (drawable == null) return null
        val currentBorder = mCornerRadius
        if (!haveBorder) setCornerRadius(0f)
        // Bước 1: Vẽ toàn bộ view thành bitmap
        val fullBitmap = try {
            this.drawToBitmap(config)
        }catch (e: OutOfMemoryError){
            e.printStackTrace()
            null
        }catch (e: Exception){
            e.printStackTrace()
            null
        } ?: return null

        // Bước 2: Xác định vùng crop chính xác trong bitmap của view
        val scaleX = fullBitmap.width.toFloat() / this.width
        val scaleY = fullBitmap.height.toFloat() / this.height

        val left = (mCropRectF.left * scaleX).toInt()
        val top = (mCropRectF.top * scaleY).toInt()
        val width = (mCropRectF.width() * scaleX).toInt()
        val height = (mCropRectF.height() * scaleY).toInt()

        if (left + width > fullBitmap.width || top + height > fullBitmap.height) {
            Log.e("Crop", "Out of bounds crop area")
            return null
        }
        if (!haveBorder) setCornerRadius(currentBorder)
        return Bitmap.createBitmap(fullBitmap, left, top, width, height)
    }

    private fun detectTouchCorner(x: Float, y: Float): DragCorner {
        val rect = mCropRectF
        val s = touchAreaSize

        return when {
            RectF(rect.left - s, rect.top - s, rect.left + s, rect.top + s).contains(x, y) -> DragCorner.TOP_LEFT
            RectF(rect.right - s, rect.top - s, rect.right + s, rect.top + s).contains(x, y) -> DragCorner.TOP_RIGHT
            RectF(rect.left - s, rect.bottom - s, rect.left + s, rect.bottom + s).contains(x, y) -> DragCorner.BOTTOM_LEFT
            RectF(rect.right - s, rect.bottom - s, rect.right + s, rect.bottom + s).contains(x, y) -> DragCorner.BOTTOM_RIGHT
            else -> DragCorner.NONE
        }
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
            else -> return
        }

        // Giới hạn cropRect không vượt ra ngoài view
        val viewBounds = RectF(paddingLeft.toFloat(), paddingTop.toFloat(), width - paddingRight.toFloat(), height - paddingBottom.toFloat())
        newRect.intersect(viewBounds)

        // Nếu width/height quá nhỏ thì bỏ
        if (newRect.width() < 100f || newRect.height() < 100f) return

        // Gán lại
        mCropRectF.set(newRect)
        mCropRectBorderRectF.set(newRect)
        mCropRectBorderRectF.inset(mCropRectBorderWidth / 2f, mCropRectBorderWidth / 2f)
        updateCropSubLines()
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

}