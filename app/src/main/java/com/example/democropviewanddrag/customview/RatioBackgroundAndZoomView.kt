package com.example.democropviewanddrag.customview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import com.example.democropviewanddrag.databinding.LayoutRatioBackgroundWithZoomviewBinding
import com.example.democropviewanddrag.extension.trimTransparent

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
        binding = LayoutRatioBackgroundWithZoomviewBinding.inflate(
            LayoutInflater.from(context),
            this,
            true
        )
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
        safeRun({ backgroundImageView!!.setImageResource(resId) }, onError)
    }

    fun setBackgroundImageFile(path: String, onError: (Exception) -> Unit = {}) {
        safeRun({
            val bitmap = BitmapFactory.decodeFile(path)
            backgroundImageView?.setImageBitmap(bitmap)
        }, onError)
    }

    fun setBackgroundImageUri(uri: Uri, onError: (Exception) -> Unit = {}) {
        safeRun({
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            backgroundImageView?.setImageBitmap(bitmap)
        }, onError)
    }

    fun setCropRatioForBackground(width: Float, height: Float, onError: (Exception) -> Unit = {}) {
        safeRun({ backgroundImageView!!.setCropRatio(width, height) }, onError)
    }

    fun setCropRatioOriginal(onError: (Exception) -> Unit = {}) : Pair<Int, Int>? {
        return try {
            backgroundImageView!!.setCropRatioOriginal()
        }catch (e: OutOfMemoryError){
            e.printStackTrace()
            onError(Exception("Out of memory"))
            null
        }catch (e: Exception){
            e.printStackTrace()
            onError(e)
            null
        }
    }

    fun setBackgroundRadius(radius: Float, onError: (Exception) -> Unit = {}) {
        safeRun({ backgroundImageView!!.setCornerRadius(radius) }, onError)
    }

    fun showGridClipPath(onError: (Exception) -> Unit = {}) {
        safeRun({ backgroundImageView!!.toggleClipPath() }, onError)
    }

    /**
     * Set image for foreground/content
     * */
    fun setForegroundImageResource(resId: Int, onError: (Exception) -> Unit = {}) {
        safeRun({ foregroundImageView!!.setImageFromResource(resId) }, onError)
    }

    fun setForegroundImageFile(path: String, onError: (Exception) -> Unit = {}) {
        safeRun({
            val bitmap = BitmapFactory.decodeFile(path)
            foregroundImageView!!.setImageBitmap(bitmap)
        }, onError)
    }

    fun setForegroundImageUri(uri: Uri, onError: (Exception) -> Unit = {}) {
        safeRun({
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            foregroundImageView!!.setImageBitmap(bitmap)
        }, onError)
    }


    private fun safeRun(block: () -> Unit = { }, onError: (Exception) -> Unit = {}) {
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

    /**Get background bitmap */
    fun getBackgroundBitmap(haveBorder: Boolean = false): Bitmap? {
        return backgroundImageView?.getAccurateCropBitmap(haveBorder)
    }

    /**Get foreground bitmap */
    fun getForegroundBitmap(width: Int? = null, height: Int? = null): Bitmap? {
        return foregroundImageView?.getBitmap(width, height)
    }

    /**Merge background image with foreground image*/
    fun getResultBitmap(haveBorder: Boolean = false): Bitmap? {
        return try {
            val backgroundBitmap = try {
                getBackgroundBitmap(haveBorder)?.trimTransparent()
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                getBackgroundBitmap(haveBorder)
            } ?: return null

            val foregroundBitmap =
                getForegroundBitmap(backgroundBitmap.width, backgroundBitmap.height) ?: return null
            val resultBitmap = createBitmap(backgroundBitmap.width, backgroundBitmap.height)

            resultBitmap.apply {
                val canvas = Canvas(this)
                canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
                canvas.drawBitmap(foregroundBitmap, 0f, 0f, null)
            }

            resultBitmap
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
