package com.example.democropviewanddrag.customview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.example.democropviewanddrag.R
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.graphics.toColorInt
import kotlin.math.pow

class CropZoomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var backgroundRect = RectF(100f, 100f, 600f, 600f) // Vùng chứa ảnh
    var imageCropBitmap: Bitmap? = null // Ảnh hiển thị
    private var imageMatrix = Matrix() // Ma trận biến đổi ảnh
    private val imagePaint = Paint()

    // Border và icon
    private var isSelected = false // Trạng thái được chọn
    private val borderPaint = Paint().apply {
        color = "#12C138".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private var rotateIcon: Bitmap? = null // Icon xoay
    private var zoomIcon: Bitmap? = null // Icon zoom
    private val iconSize = 50f // Kích thước icon

    // Trạng thái thao tác
    private var isDraggingView = false // Đang kéo thả view
    private var isRotating = false // Đang xoay
    private var isZooming = false // Đang zoom
    private var dragStartX = 0f // Tọa độ bắt đầu kéo
    private var dragStartY = 0f
    private var initialRotation = 0f // Góc xoay ban đầu
    private var initialScale = 1f // Tỷ lệ zoom ban đầu
    private var newBitmap: Bitmap? = null // Ảnh biến đổi

    private var zoomStartX = 0f
    private var initialMatrixScale = 1f

    // Ngưỡng di chuyển xác định là click
    private val CLICK_THRESHOLD = 10f

    // Biến lưu vị trí ban đầu tại ACTION_DOWN
    private var downX = 0f
    private var downY = 0f

    init {
        setWillNotDraw(false)
        rotateIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_rotate)
        zoomIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_zoom)
    }

    fun setImageFromResource(resourceId: Int) {
        imageCropBitmap = BitmapFactory.decodeResource(resources, resourceId)
        imageCropBitmap?.let { bitmap ->
            val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth = backgroundRect.height() * imageRatio
            backgroundRect.right = backgroundRect.left + newWidth
            imageMatrix.reset()
            val scale = minOf(backgroundRect.width() / bitmap.width, backgroundRect.height() / bitmap.height)
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(backgroundRect.left, backgroundRect.top)
            invalidate()
        }
    }

    fun setImageBitmap(bm: Bitmap?) {
        imageCropBitmap = bm
        imageCropBitmap?.let { bitmap ->
            val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth = backgroundRect.height() * imageRatio
            backgroundRect.right = backgroundRect.left + newWidth
            imageMatrix.reset()
            val scale = minOf(backgroundRect.width() / bitmap.width, backgroundRect.height() / bitmap.height)
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(backgroundRect.left, backgroundRect.top)
            invalidate()
        }
    }

    fun setBackgroundRect(rect: RectF) {
        // Lưu góc xoay hiện tại
        val currentValues = FloatArray(9)
        imageMatrix.getValues(currentValues)
        val currentScaleX = currentValues[Matrix.MSCALE_X]
        val currentRotation = Math.toDegrees(
            atan2(currentValues[Matrix.MSKEW_Y].toDouble(), currentScaleX.toDouble())
        ).toFloat()

        // Cập nhật backgroundRect và imageMatrix
        backgroundRect.set(rect)
        imageCropBitmap?.let { bitmap ->
            imageMatrix.reset()
            val scale = minOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
            val dx = rect.left + (rect.width() - bitmap.width * scale) / 2
            val dy = rect.top + (rect.height() - bitmap.height * scale) / 2
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(dx, dy)

            // Áp dụng lại góc xoay hiện tại
            imageMatrix.postRotate(currentRotation, backgroundRect.centerX(), backgroundRect.centerY())

            // Kiểm tra và scale để khớp với rect (tương tự logic khi zoom)
            val currentRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            imageMatrix.mapRect(currentRect)
            if (currentRect.width() > rect.width() || currentRect.height() > rect.height()) {
                val currentScale = getCurrentScaleFromMatrix(imageMatrix)
                val maxScaleX = rect.width() / currentRect.width()
                val maxScaleY = rect.height() / currentRect.height()
                val newScale = minOf(maxScaleX, maxScaleY)

                imageMatrix.postScale(
                    newScale, newScale,
                    backgroundRect.centerX(), backgroundRect.centerY()
                )
            }

            // Căn giữa lại trong rect
            updateBackgroundRect()
            val newRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            imageMatrix.mapRect(newRect)
            val dxCenter = rect.centerX() - newRect.centerX()
            val dyCenter = rect.centerY() - newRect.centerY()
            imageMatrix.postTranslate(dxCenter, dyCenter)
            updateBackgroundRect()
            invalidate()
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        imageCropBitmap?.let {
            newBitmap = it
            canvas.drawBitmap(it, imageMatrix, imagePaint)
        }
        if (isSelected) {
            canvas.drawRect(backgroundRect, borderPaint)
            rotateIcon?.let {
                val rotateRect = RectF(
                    backgroundRect.left - iconSize / 2, backgroundRect.top - iconSize / 2,
                    backgroundRect.left + iconSize / 2, backgroundRect.top + iconSize / 2
                )
                canvas.drawBitmap(it, null, rotateRect, Paint())
            }
            zoomIcon?.let {
                val zoomRect = RectF(
                    backgroundRect.right - iconSize / 2, backgroundRect.bottom - iconSize / 2,
                    backgroundRect.right + iconSize / 2, backgroundRect.bottom + iconSize / 2
                )
                canvas.drawBitmap(it, null, zoomRect, Paint())
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y

                when {
                    isInRotateIcon(x, y) -> {
                        isRotating = true
                        initialRotation = calculateRotationAngle(x, y)
                    }
                    isInZoomIcon(x, y) -> {
                        isZooming = true
                        zoomStartX = x
                        initialMatrixScale = getCurrentScaleFromMatrix(imageMatrix)
                    }
                    isTouchInsideImage(x, y) -> {
                        isDraggingView = true
                        dragStartX = x - backgroundRect.left
                        dragStartY = y - backgroundRect.top
                    }
                    else -> {
                        isSelected = false
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (isRotating) {
                    val newRotation = calculateRotationAngle(x, y)
                    val deltaRotation = newRotation - initialRotation
                    imageMatrix.postRotate(deltaRotation, backgroundRect.centerX(), backgroundRect.centerY())
                    initialRotation = newRotation
                    updateBackgroundRect()

                    // Lấy vùng crop từ CropImageView
                    val cropBorderRect = (parent as? ViewGroup)?.findViewById<CropImageView>(R.id.cropImageView)?.getCropBorderRect()

                    if (cropBorderRect != null) {
                        // Tính kích thước hiện tại của backgroundRect
                        val currentRect = RectF(0f, 0f, imageCropBitmap?.width?.toFloat() ?: 0f, imageCropBitmap?.height?.toFloat() ?: 0f)
                        imageMatrix.mapRect(currentRect)

                        // Kiểm tra nếu backgroundRect vượt quá cropBorderRect
                        if (currentRect.width() > cropBorderRect.width() || currentRect.height() > cropBorderRect.height()) {
                            val currentScale = getCurrentScaleFromMatrix(imageMatrix)
                            val maxScaleX = cropBorderRect.width() / currentRect.width()
                            val maxScaleY = cropBorderRect.height() / currentRect.height()
                            val newScale = minOf(maxScaleX, maxScaleY)

                            // Áp dụng scale để backgroundRect vừa với cropBorderRect
                            imageMatrix.postScale(
                                newScale, newScale,
                                backgroundRect.centerX(), backgroundRect.centerY()
                            )

                            // Căn giữa lại trong cropBorderRect
                            updateBackgroundRect()
                            val newRect = RectF(0f, 0f, imageCropBitmap?.width?.toFloat() ?: 0f, imageCropBitmap?.height?.toFloat() ?: 0f)
                            imageMatrix.mapRect(newRect)
                            val dx = cropBorderRect.centerX() - newRect.centerX()
                            val dy = cropBorderRect.centerY() - newRect.centerY()
                            imageMatrix.postTranslate(dx, dy)
                            updateBackgroundRect()
                        }
                    }
                    invalidate()
                } else if (isZooming) {
                    val deltaX = x - zoomStartX
                    val scaleFactor = 1f + (deltaX / 100f)
                    val finalScaleFactor = initialMatrixScale * scaleFactor
                    val currentScale = getCurrentScaleFromMatrix(imageMatrix)
                    imageMatrix.postScale(
                        finalScaleFactor / currentScale, finalScaleFactor / currentScale,
                        backgroundRect.centerX(), backgroundRect.centerY()
                    )
                    updateBackgroundRect()
                    invalidate()
                } else if (isDraggingView) {
                    val newLeft = x - dragStartX
                    val newTop = y - dragStartY
                    val dx = newLeft - backgroundRect.left
                    val dy = newTop - backgroundRect.top
                    val newRect = RectF(backgroundRect)
                    newRect.offset(dx, dy)

                    val cropBorderRect = (parent as? ViewGroup)?.findViewById<CropImageView>(R.id.cropImageView)?.getCropBorderRect()
                    var validDx = dx
                    var validDy = dy
                    if (cropBorderRect != null) {
                        if (newRect.left < cropBorderRect.left) {
                            validDx = cropBorderRect.left - backgroundRect.left
                        }
                        if (newRect.right > cropBorderRect.right) {
                            validDx = cropBorderRect.right - backgroundRect.right
                        }
                        if (newRect.top < cropBorderRect.top) {
                            validDy = cropBorderRect.top - backgroundRect.top
                        }
                        if (newRect.bottom > cropBorderRect.bottom) {
                            validDy = cropBorderRect.bottom - backgroundRect.bottom
                        }
                    }
                    backgroundRect.offset(validDx, validDy)
                    imageMatrix.postTranslate(validDx, validDy)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isZooming) {
                    // Lấy vùng crop từ CropImageView
                    val cropBorderRect = (parent as? ViewGroup)?.findViewById<CropImageView>(R.id.cropImageView)?.getCropBorderRect()

                    if (cropBorderRect != null) {
                        val currentRect = RectF(0f, 0f, imageCropBitmap?.width?.toFloat() ?: 0f, imageCropBitmap?.height?.toFloat() ?: 0f)
                        imageMatrix.mapRect(currentRect)

                        if (currentRect.width() > cropBorderRect.width() || currentRect.height() > cropBorderRect.height()) {
                            val currentScale = getCurrentScaleFromMatrix(imageMatrix)
                            val maxScaleX = cropBorderRect.width() / currentRect.width()
                            val maxScaleY = cropBorderRect.height() / currentRect.height()
                            val newScale = minOf(maxScaleX, maxScaleY)
                            imageMatrix.postScale(
                                newScale, newScale,
                                backgroundRect.centerX(), backgroundRect.centerY()
                            )
                            updateBackgroundRect()
                            val newRect = RectF(0f, 0f, imageCropBitmap?.width?.toFloat() ?: 0f, imageCropBitmap?.height?.toFloat() ?: 0f)
                            imageMatrix.mapRect(newRect)
                            val dx = cropBorderRect.centerX() - newRect.centerX()
                            val dy = cropBorderRect.centerY() - newRect.centerY()
                            imageMatrix.postTranslate(dx, dy)
                            updateBackgroundRect()
                            invalidate()
                        }
                    }

                }

                if (isDraggingView) {
                    val cropBorderRect = (parent as? ViewGroup)?.findViewById<CropImageView>(R.id.cropImageView)?.getCropBorderRect()
                    if (cropBorderRect != null) {
                        var dx = 0f
                        var dy = 0f
                        if (backgroundRect.left < cropBorderRect.left) {
                            dx = cropBorderRect.left - backgroundRect.left
                        } else if (backgroundRect.right > cropBorderRect.right) {
                            dx = cropBorderRect.right - backgroundRect.right
                        }
                        if (backgroundRect.top < cropBorderRect.top) {
                            dy = cropBorderRect.top - backgroundRect.top
                        } else if (backgroundRect.bottom > cropBorderRect.bottom) {
                            dy = cropBorderRect.bottom - backgroundRect.bottom
                        }

                        if (dx != 0f || dy != 0f) {
                            imageMatrix.postTranslate(dx, dy)
                            updateBackgroundRect()
                            invalidate()
                        }
                    }
                }

                isRotating = false
                isZooming = false
                isDraggingView = false

                val deltaX = abs(x - downX)
                val deltaY = abs(y - downY)
                if (deltaX < CLICK_THRESHOLD && deltaY < CLICK_THRESHOLD) {
                    isSelected = !isSelected
                    updateBackgroundRect()
                    invalidate()
                }
            }
        }

        return isTouchInsideImage(x, y) || isInRotateIcon(x, y) || isInZoomIcon(x, y)
    }

    private fun getCurrentScaleFromMatrix(matrix: Matrix): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return sqrt(values[0].toDouble().pow(2.0) + values[3].toDouble().pow(2.0)).toFloat()
    }

    private fun isInRotateIcon(x: Float, y: Float): Boolean {
        val rotateRect = RectF(
            backgroundRect.left - iconSize / 2, backgroundRect.top - iconSize / 2,
            backgroundRect.left + iconSize / 2, backgroundRect.top + iconSize / 2
        )
        return rotateRect.contains(x, y)
    }

    private fun isInZoomIcon(x: Float, y: Float): Boolean {
        val zoomRect = RectF(
            backgroundRect.right - iconSize / 2, backgroundRect.bottom - iconSize / 2,
            backgroundRect.right + iconSize / 2, backgroundRect.bottom + iconSize / 2
        )
        return zoomRect.contains(x, y)
    }

    private fun isTouchInsideImage(x: Float, y: Float): Boolean {
        val pts = floatArrayOf(x, y)
        val inv = Matrix()
        if (!imageMatrix.invert(inv)) return false
        inv.mapPoints(pts)
        val bmp = imageCropBitmap ?: return false
        return pts[0] in 0f..bmp.width.toFloat() && pts[1] in 0f..bmp.height.toFloat()
    }

    fun updateBackgroundRect() {
        val rect = RectF(0f, 0f, imageCropBitmap?.width?.toFloat() ?: 0f, imageCropBitmap?.height?.toFloat() ?: 0f)
        imageMatrix.mapRect(rect)
        backgroundRect.set(rect)
    }

    private fun calculateRotationAngle(x: Float, y: Float): Float {
        val centerX = backgroundRect.centerX()
        val centerY = backgroundRect.centerY()
        val dx = x - centerX
        val dy = y - centerY
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    fun getBitmap(width: Int? = null, height: Int? = null): Bitmap? {
        imageCropBitmap?.let { bitmap ->
            if (width == null && height == null) {
                return Bitmap.createBitmap(bitmap).apply {
                    Log.d("CropZoomView", "Returning original bitmap: ${bitmap.width}x${bitmap.height}")
                }
            }
            val resultBitmap = createBitmap(
                width ?: bitmap.width,
                height ?: bitmap.height,
                Bitmap.Config.ARGB_8888
            ).apply {
                eraseColor(Color.TRANSPARENT)
            }
            val canvas = Canvas(resultBitmap)
            canvas.setMatrix(imageMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)
            Log.d("CropZoomView", "Returning transformed bitmap: ${resultBitmap.width}x${resultBitmap.height}")
            return resultBitmap
        }
        Log.w("CropZoomView", "imageCropBitmap is null")
        return null
    }

    fun getBitmapPosition(): RectF {
        val rect = RectF(backgroundRect)
        imageMatrix.mapRect(rect)
        return rect
    }

    /**
     * Tạo ba ảnh: Background (BG), Foreground (FG), và ảnh merge từ CropImageView và CropZoomView,
     * khớp với tỷ lệ và vị trí như trong preview.
     * @return Triple chứa Bitmap cho BG, FG, và ảnh merge (có thể null nếu không tạo được).
     */
    fun getProcessedImages(): Triple<Bitmap?, Bitmap?, Bitmap?> {
        val cropImageView = (parent as? ViewGroup)?.findViewById<CropImageView>(R.id.cropImageView)
        val cropBorderRect = cropImageView?.getCropBorderRect()
            ?: return Triple(null, null, null)
        val bgView = cropImageView
        val bgBitmap = bgView.getAccurateCropBitmap()

        if (bgBitmap == null || imageCropBitmap == null) {
            return Triple(null, null, null)
        }
        val outputWidth = cropBorderRect.width().toInt()
        val outputHeight = cropBorderRect.height().toInt()
        // Tạo Bitmap cho BG (crop theo cropBorderRect)
        val bgOutput = createBitmap(outputWidth, outputHeight)
        val bgCanvas = Canvas(bgOutput)
        val bgMatrix = Matrix()
        // Dịch chuyển để cropBorderRect bắt đầu tại (0,0) trong bgOutput
//        bgMatrix.postTranslate(-cropBorderRect.left, -cropBorderRect.top)
        bgCanvas.drawBitmap(bgBitmap, bgMatrix, imagePaint)

        // Tạo Bitmap cho FG (crop theo cropBorderRect)
        val fgOutput = createBitmap(outputWidth, outputHeight)
        val fgCanvas = Canvas(fgOutput)
        val fgMatrix = Matrix(imageMatrix)
        // Dịch chuyển để cropBorderRect bắt đầu tại (0,0) trong fgOutput
        fgMatrix.postTranslate(-cropBorderRect.left, -cropBorderRect.top)
        fgCanvas.drawBitmap(imageCropBitmap!!, fgMatrix, imagePaint)

        // Tạo Bitmap cho ảnh merge
        val mergeOutput = createBitmap(outputWidth, outputHeight)
        val mergeCanvas = Canvas(mergeOutput)
        // Vẽ BG trước
        mergeCanvas.drawBitmap(bgOutput, 0f, 0f, imagePaint)
        // Vẽ FG lên trên
        mergeCanvas.drawBitmap(imageCropBitmap!!, fgMatrix, imagePaint)

        return Triple(bgOutput, fgOutput, mergeOutput)
    }
}