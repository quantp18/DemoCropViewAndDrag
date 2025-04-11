package com.example.democropviewanddrag.customview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.democropviewanddrag.R
import kotlin.math.atan2
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.toColorInt

class CropZoomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var backgroundRect = RectF(100f, 100f, 300f, 300f) // Vùng chứa ảnh
    private var imageBitmap: Bitmap? = null // Ảnh hiển thị
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
    private var newBitmap : Bitmap? = null // Ảnh biến đổi

    private var zoomStartX = 0f
    private var initialMatrixScale = 1f

    // Ngưỡng di chuyển xác định là click (tùy chỉnh nếu cần)
    private val CLICK_THRESHOLD = 10f

    // Biến lưu vị trí ban đầu tại ACTION_DOWN
    private var downX = 0f
    private var downY = 0f

    init {
        setWillNotDraw(false)
        // Load icon từ drawable (thay bằng icon của bạn)
        rotateIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_rotate)
        zoomIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_zoom)
    }

    // Đặt ảnh từ resource
    fun setImageFromResource(resourceId: Int) {
        imageBitmap = BitmapFactory.decodeResource(resources, resourceId)
        imageBitmap?.let { bitmap ->
            // Tính toán tỷ lệ của ảnh
            val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            // Điều chỉnh backgroundRect để có cùng tỷ lệ với ảnh
            // Giữ chiều cao, điều chỉnh chiều rộng
            val newWidth = backgroundRect.height() * imageRatio
            backgroundRect.right = backgroundRect.left + newWidth

            // Thiết lập ma trận để ảnh vừa với backgroundRect
            imageMatrix.reset()
            val scale = minOf(backgroundRect.width() / bitmap.width, backgroundRect.height() / bitmap.height)
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(backgroundRect.left, backgroundRect.top)

            // Cập nhật giao diện
            invalidate()
        }
    }

    fun setImageBitmap(bm: Bitmap?) {
        imageBitmap = bm
        imageBitmap?.let { bitmap ->
            // Tính toán tỷ lệ của ảnh
            val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            // Điều chỉnh backgroundRect để có cùng tỷ lệ với ảnh
            // Giữ chiều cao, điều chỉnh chiều rộng
            val newWidth = backgroundRect.height() * imageRatio
            backgroundRect.right = backgroundRect.left + newWidth

            // Thiết lập ma trận để ảnh vừa với backgroundRect
            imageMatrix.reset()
            val scale = minOf(backgroundRect.width() / bitmap.width, backgroundRect.height() / bitmap.height)
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(backgroundRect.left, backgroundRect.top)

            // Cập nhật giao diện
            invalidate()
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Vẽ ảnh
        imageBitmap?.let {
            newBitmap = it
            canvas.drawBitmap(it, imageMatrix, imagePaint)
        }

        // Vẽ border và icon khi được chọn
        if (isSelected) {
            canvas.drawRect(backgroundRect, borderPaint)

            // Vẽ icon rotate (top-left)

            rotateIcon?.let {
                val rotateRect = RectF(
                    backgroundRect.left - iconSize / 2, backgroundRect.top - iconSize / 2,
                    backgroundRect.left + iconSize / 2, backgroundRect.top + iconSize / 2
                )

                canvas.drawBitmap(it, null, rotateRect, Paint().apply { color = Color.YELLOW })
            }

            // Vẽ icon zoom (bottom-right)
            zoomIcon?.let {
                val zoomRect = RectF(
                    backgroundRect.right - iconSize / 2, backgroundRect.bottom - iconSize / 2,
                    backgroundRect.right + iconSize / 2, backgroundRect.bottom + iconSize / 2
                )

                canvas.drawBitmap(it, null, zoomRect, Paint().apply { color = Color.YELLOW })
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
                        // Lưu tọa độ ban đầu của ngón tay
                        zoomStartX = x
                        // Giả sử bạn có lưu trạng thái scale hiện tại của ma trận ảnh
                        initialMatrixScale = getCurrentScaleFromMatrix(imageMatrix)
                    }
                    backgroundRect.contains(x, y) -> {
                        isDraggingView = true
                        dragStartX = x - backgroundRect.left
                        dragStartY = y - backgroundRect.top
                    }
                    else -> {
                        // Nếu chạm ngoài vùng hợp lệ thì không chọn
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
                    invalidate()
                } else if (isZooming) {
                    // Tính khoảng cách di chuyển từ vị trí ban đầu
                    val deltaX = x - zoomStartX
                    // Tỷ lệ zoom ban đầu dựa trên khoảng cách di chuyển (điều chỉnh hệ số nếu cần)
                    val scaleFactor = 1f + (deltaX / 100f)
                    // Tính toán scale mới dựa trên scale ban đầu của ma trận
                    val finalScaleFactor = initialMatrixScale * scaleFactor
                    // Lấy hệ số scale hiện tại (nếu cần để tính tỉ lệ update tiếp theo)
                    val currentScale = getCurrentScaleFromMatrix(imageMatrix)
                    // Áp dụng scale dựa trên tỉ lệ so với giá trị hiện tại
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
                    backgroundRect.offset(dx, dy)
                    imageMatrix.postTranslate(dx, dy)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
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

        return backgroundRect.contains(x, y) || isInRotateIcon(x, y) || isInZoomIcon(x, y)
    }

    private fun getCurrentScaleFromMatrix(matrix: Matrix): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        // MSCALE_X = values[0], MSKEW_Y = values[3]
        return sqrt(values[0].toDouble().pow(2.0) + values[3].toDouble().pow(2.0)).toFloat()
    }

    // Kiểm tra vị trí click có nằm trong icon rotate không
    private fun isInRotateIcon(x: Float, y: Float): Boolean {
        val rotateRect = RectF(
            backgroundRect.left - iconSize / 2, backgroundRect.top - iconSize / 2,
            backgroundRect.left + iconSize / 2, backgroundRect.top + iconSize / 2
        )
        return rotateRect.contains(x, y)
    }

    // Kiểm tra vị trí click có nằm trong icon zoom không
    private fun isInZoomIcon(x: Float, y: Float): Boolean {
        val zoomRect = RectF(
            backgroundRect.right - iconSize / 2, backgroundRect.bottom - iconSize / 2,
            backgroundRect.right + iconSize / 2, backgroundRect.bottom + iconSize / 2
        )
        return zoomRect.contains(x, y)
    }

    private fun updateBackgroundRect() {
        // Lấy kích thước gốc của ảnh
        val rect = RectF(0f, 0f, imageBitmap?.width?.toFloat() ?: 0f, imageBitmap?.height?.toFloat() ?: 0f)
        // Áp dụng ma trận để tính kích thước mới
        imageMatrix.mapRect(rect)
        // Cập nhật backgroundRect
        backgroundRect.set(rect)
        // Yêu cầu vẽ lại view
//        invalidate()
    }

    // Tính góc xoay dựa trên vị trí chạm
    private fun calculateRotationAngle(x: Float, y: Float): Float {
        val centerX = backgroundRect.centerX()
        val centerY = backgroundRect.centerY()
        val dx = x - centerX
        val dy = y - centerY
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    fun getBitmap(width : Int? = null, height : Int? = null): Bitmap? {
        imageBitmap?.let { bitmap ->
            // Tạo bitmap mới với kích thước gốc của ảnh
            val resultBitmap = createBitmap(
                width ?: (bitmap.width + 60),
                height ?: (bitmap.height + 30)
            )
            val canvas = Canvas(resultBitmap)

            // Áp dụng ma trận biến đổi lên canvas
            canvas.setMatrix(imageMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)

            return resultBitmap
        }
        return null
    }

    fun getBitmapPosition(): RectF {
        val rect = RectF(backgroundRect)
        imageMatrix.mapRect(rect)
        return rect
    }
}