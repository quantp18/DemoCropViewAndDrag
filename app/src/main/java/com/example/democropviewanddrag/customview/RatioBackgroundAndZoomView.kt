package com.example.democropviewanddrag.customview

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import com.example.democropviewanddrag.R
import com.example.democropviewanddrag.customview.v2.CropZoomView
import com.example.democropviewanddrag.databinding.LayoutRatioBackgroundWithZoomviewBinding

class RatioBackgroundAndZoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var binding: LayoutRatioBackgroundWithZoomviewBinding? = null
    private var backgroundImageView: CropImageView? = null
    private var foregroundImageView: CropZoomView? = null

    init {
        initView()
        alignZoomImageViewToCropArea()
    }

    private fun initView() {
        binding = LayoutRatioBackgroundWithZoomviewBinding.inflate(LayoutInflater.from(context), this, true)
        backgroundImageView = binding!!.cropView
        foregroundImageView = binding!!.ivRotate
    }

    private fun alignZoomImageViewToCropArea() {
        backgroundImageView?.apply {
            viewTreeObserver?.addOnGlobalLayoutListener {
                val cropRect = mCropRectFClone
                if (cropRect.width() > 0 && cropRect.height() > 0) {
                    // Cập nhật kích thước và vị trí của CropZoomView
                    val layoutParams =
                        foregroundImageView?.layoutParams ?: return@addOnGlobalLayoutListener
                    layoutParams.width = cropRect.width().toInt()
                    layoutParams.height = cropRect.height().toInt()
                    foregroundImageView?.layoutParams = layoutParams

                    // Căn chỉnh vị trí của CropZoomView
                    foregroundImageView?.x = cropRect.left
                    foregroundImageView?.y = cropRect.top
                }
            }
        }
    }

    /**
     * Set image for background
     * */
    fun setBackgroundImageResource(resId: Int, onError: (Exception) -> Unit = {}) {
        runCatchException({ backgroundImageView!!.setImageResource(resId) }, onError)
    }

    fun setCropRatioForBackground(width: Float, height: Float, onError: (Exception) -> Unit = {}) {
        runCatchException({ backgroundImageView!!.setCropRatio(width, height) }, onError)
    }

    /**
     * Set image for foreground/content
     * */
    fun setForegroundImageResource(resId: Int, onError: (Exception) -> Unit = {}) {
        runCatchException({ foregroundImageView!!.setImageFromResource(resId) }, onError)
    }


    private fun runCatchException(block: () -> Unit = { }, onError: (Exception) -> Unit = {}) {
        try {
            block()
        } catch (e: OutOfMemoryError) {
            onError(Exception("Out of memory!"))
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Handle Result
     **/

    /**Merge background image with foreground image*/
    fun getResultBitmap(): Bitmap? {
        val backgroundBitmap = backgroundImageView?.getAccurateCropBitmap() ?: return null
        val foregroundBitmap =
            foregroundImageView?.getBitmap(backgroundBitmap.width, backgroundBitmap.height)
                ?: return null
        val resultBitmap = createBitmap(backgroundBitmap.width, backgroundBitmap.height)

        return resultBitmap.apply {
            val canvas = Canvas(this)
            canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
            canvas.drawBitmap(foregroundBitmap, 0f, 0f, null)
        }
    }
}

/** Add watermark for result bitmap*/
fun Bitmap?.addWatermark(
    resources: Resources,
    resId: Int = R.drawable.bg_watermark
): Bitmap? {
    val originalBitmap = this ?: return null

    return try {
        val resultBitmap = createBitmap(originalBitmap.width, originalBitmap.height)

        val watermark = BitmapFactory.decodeResource(resources, resId)
        if (watermark == null) {
            Log.e("Watermark", "Invalid watermark resource")
            return null
        }

        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)
        canvas.drawBitmap(
            watermark,
            (originalBitmap.width - watermark.width).toFloat(),
            (originalBitmap.height - watermark.height).toFloat(),
            null
        )

        resultBitmap
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}
