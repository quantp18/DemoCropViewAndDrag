package com.example.democropviewanddrag.customview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.graphics.*
import android.view.MotionEvent

class RemovePixelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageBitmap: Bitmap? = null
    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null
    private val currentPath = Path()
    private var previousX: Float = 0f
    private var previousY: Float = 0f

    private val screenPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val bitmapPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Lớp để lưu trữ thông tin một đường vẽ
    private data class DrawAction(val path: Path, val color: Int, val strokeWidth: Float)

    // Danh sách lưu trữ lịch sử vẽ
    private val undoStack = mutableListOf<DrawAction>()
    private val redoStack = mutableListOf<DrawAction>()

    private var scale: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    private var inverseMatrix: Matrix = Matrix()

    fun setImageBitmap(bitmap: Bitmap) {
        imageBitmap = bitmap
        drawingBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        drawingCanvas = Canvas(drawingBitmap!!)
        drawingBitmap!!.eraseColor(Color.TRANSPARENT)
        updateScaleAndOffset(width, height)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (imageBitmap != null) {
            updateScaleAndOffset(w, h)
        }
    }

    private fun updateScaleAndOffset(viewWidth: Int, viewHeight: Int) {
        imageBitmap?.let {
            scale = minOf(viewWidth.toFloat() / it.width, viewHeight.toFloat() / it.height)
            val scaledWidth = it.width * scale
            val scaledHeight = it.height * scale
            offsetX = (viewWidth - scaledWidth) / 2
            offsetY = (viewHeight - scaledHeight) / 2
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(offsetX, offsetY)
            }
            matrix.invert(inverseMatrix)
            bitmapPaint.strokeWidth = screenPaint.strokeWidth / scale
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.moveTo(x, y)
                previousX = x
                previousY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val midX = (x + previousX) / 2
                val midY = (y + previousY) / 2
                currentPath.quadTo(previousX, previousY, midX, midY)
                previousX = x
                previousY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val transformedPath = Path(currentPath)
                transformedPath.transform(inverseMatrix)
                // Lưu đường vẽ vào undoStack
                undoStack.add(DrawAction(Path(transformedPath), bitmapPaint.color, bitmapPaint.strokeWidth))
                redoStack.clear() // Xóa redoStack khi có thao tác vẽ mới
                drawingCanvas?.drawPath(transformedPath, bitmapPaint)
                currentPath.reset()
                invalidate()
            }
        }
        return true
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        imageBitmap?.let {
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(offsetX, offsetY)
            }
            canvas.drawBitmap(it, matrix, null)
            drawingBitmap?.let { db -> canvas.drawBitmap(db, matrix, null) }
            canvas.drawPath(currentPath, screenPaint)
        }
    }

    fun getMaskBitmap(): Bitmap? = drawingBitmap

    fun setBrushSize(size: Float) {
        screenPaint.strokeWidth = size
        bitmapPaint.strokeWidth = size / scale
    }

    fun setBrushColor(color: Int) {
        screenPaint.color = color
        bitmapPaint.color = color
    }

    /**
     * Thực hiện thao tác Undo
     */
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val lastAction = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(lastAction)
            redrawCanvas()
        }
    }

    /**
     * Thực hiện thao tác Redo
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val lastAction = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(lastAction)
            redrawCanvas()
        }
    }

    /**
     * Vẽ lại toàn bộ các đường vẽ trong undoStack lên drawingBitmap
     */
    private fun redrawCanvas() {
        drawingBitmap?.eraseColor(Color.TRANSPARENT)
        drawingCanvas?.let { canvas ->
            for (action in undoStack) {
                bitmapPaint.color = action.color
                bitmapPaint.strokeWidth = action.strokeWidth
                canvas.drawPath(action.path, bitmapPaint)
            }
        }
        invalidate()
    }

    /**
     * Lấy bitmap kết hợp giữa ảnh nền và mặt nạ (các nét vẽ)
     * @return Bitmap kết hợp, hoặc null nếu không có ảnh nền
     */
    fun getCombinedBitmap(): Bitmap? {
        imageBitmap?.let { background ->
            // Tạo bitmap mới với cùng kích thước và cấu hình như ảnh nền
            val combinedBitmap = Bitmap.createBitmap(
                background.width,
                background.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(combinedBitmap)

            // Vẽ ảnh nền lên bitmap mới
            canvas.drawBitmap(background, 0f, 0f, null)

            // Vẽ mặt nạ (các nét vẽ) lên trên ảnh nền
            drawingBitmap?.let { mask ->
                canvas.drawBitmap(mask, 0f, 0f, null)
            }

            return combinedBitmap
        }
        return null
    }
}