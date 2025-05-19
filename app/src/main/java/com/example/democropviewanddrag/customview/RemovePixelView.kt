package com.example.democropviewanddrag.customview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.graphics.*
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RemovePixelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var paintingListener: OnPaintingListener? = null

    private var imageBitmap: Bitmap? = null
    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null
    private val currentPath = Path()
    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var lassoPath: Path? = null

    private val screenPaint = Paint().apply {
        color = "#70CD2C2C".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val bitmapPaint = Paint().apply {
        color = "#70CD2C2C".toColorInt()
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val lassoLivePaint = Paint().apply {
        color = "#70CD2C2C".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f) // Nét đứt
    }


    private val lassoPoints = mutableListOf<PointF>()
    private val lassoSpacingPx = 3 * resources.displayMetrics.density
    private var currentTool: ToolMode = ToolMode.BRUSH
    private val lassoActions = mutableListOf<LassoAction>()

    fun setToolMode(mode: ToolMode) {
        currentTool = mode
    }

    fun setOnPaintingListener(listener: OnPaintingListener) {
        this.paintingListener = listener
    }

    // Lớp để lưu trữ thông tin một đường vẽ
    private data class DrawAction(val path: Path, val color: Int, val strokeWidth: Float)
    private data class LassoAction(val path: Path, val fillColor: Int, val strokeColor: Int)


    // Danh sách lưu trữ lịch sử vẽ
    private val undoStack = mutableListOf<DrawAction>()
    private val redoStack = mutableListOf<DrawAction>()

    private var scale: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    private var inverseMatrix: Matrix = Matrix()

    val enableUndo: Boolean get() = undoStack.isNotEmpty()
    val enableRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setImageBitmap(bitmap: Bitmap) {
        imageBitmap = bitmap

        if (drawingBitmap == null || drawingBitmap!!.width  != bitmap.width || drawingBitmap!!.height != bitmap.height) {
            drawingBitmap?.takeIf { !it.isRecycled }?.recycle()
            drawingBitmap = createBitmap(bitmap.width, bitmap.height)
            drawingCanvas = Canvas(drawingBitmap!!)
        }
        drawingCanvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC)
        updateScaleAndOffset(width, height)
        postInvalidateOnAnimation()
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                paintingListener?.onStartDrawing()
                currentPath.moveTo(x, y)
                previousX = x
                previousY = y
            }

            MotionEvent.ACTION_MOVE -> {
                paintingListener?.onDrawing()
                val midX = (x + previousX) / 2
                val midY = (y + previousY) / 2
                currentPath.quadTo(previousX, previousY, midX, midY)
                previousX = x
                previousY = y
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                paintingListener?.onEndDrawing()

                when (currentTool) {
                    ToolMode.BRUSH -> {
                        val transformedPath = Path(currentPath)
                        transformedPath.transform(inverseMatrix)
                        undoStack.add(DrawAction(Path(transformedPath), bitmapPaint.color, bitmapPaint.strokeWidth))
                        redoStack.clear()
                        drawingCanvas?.drawPath(transformedPath, bitmapPaint)
                    }

                    ToolMode.LASSO -> {
                        // Đóng vùng lasso
                        currentPath.close()
                        val transformedPath = Path(currentPath)
                        transformedPath.transform(inverseMatrix)

                        // Fill vùng bên trong lasso lên drawingCanvas
                        val fillPaint = Paint().apply {
                            color = "#70CD2C2C".toColorInt() // Dùng màu trắng để tạo mask
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        drawingCanvas?.drawPath(transformedPath, fillPaint)
                        undoStack.add(DrawAction(Path(transformedPath), Color.WHITE, 0f)) // lưu để undo

                        // Bạn có thể lưu riêng danh sách LassoAction nếu muốn undo lasso riêng
                        redoStack.clear()
                    }
                }

                currentPath.reset()
                invalidate()
            }
        }

        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        imageBitmap?.let {
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(offsetX, offsetY)
            }

            canvas.drawBitmap(it, matrix, null)
            drawingBitmap?.let { db -> canvas.drawBitmap(db, matrix, null) }

            // Vẽ đường đang vẽ
            if (!currentPath.isEmpty) {
                val paintToUse = when (currentTool) {
                    ToolMode.BRUSH -> screenPaint
                    ToolMode.LASSO -> lassoLivePaint
                }
                canvas.drawPath(currentPath, paintToUse)
            }
        }
    }


    //    fun getMaskBitmap(): Bitmap? = drawingBitmap
    fun isPointInLasso(x: Float, y: Float): Boolean {
        lassoPath?.let { path ->
            val bounds = RectF()
            path.computeBounds(bounds, true)
            val region = Region()
            region.setPath(path, Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
            return region.contains(x.toInt(), y.toInt())
        }
        return false
    }

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
            val combinedBitmap = createBitmap(background.width, background.height)
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

    /**
     * Trả về bitmap nhị phân:
     * - Pixel vẽ: trắng (Color.WHITE)
     * - Pixel chưa vẽ: đen (Color.BLACK)
     */
    fun getMarkBitmap(): Bitmap? {
        val mask = drawingBitmap ?: return null
        val width = mask.width
        val height = mask.height
        val result = createBitmap(width, height)
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val alpha = pixels[i] ushr 24
            pixels[i] = if (alpha != 0) Color.WHITE else Color.BLACK
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }


    fun getBitmap(): Bitmap? {
        return imageBitmap
    }

    suspend fun clearDrawing() {
        withContext(Dispatchers.Default){
            undoStack.clear()
            redoStack.clear()
            lassoPath = null
            drawingBitmap?.eraseColor(Color.TRANSPARENT)
        }
        withContext(Dispatchers.Main){
            invalidate()
        }
    }
}

enum class ToolMode {
    BRUSH,
    LASSO
}

interface OnPaintingListener {
    fun onStartDrawing()
    fun onDrawing()
    fun onEndDrawing()
}
