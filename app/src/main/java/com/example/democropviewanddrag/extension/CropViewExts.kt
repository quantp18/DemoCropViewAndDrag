package com.example.democropviewanddrag.extension

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.democropviewanddrag.R
import com.example.democropviewanddrag.customview.CropImageView

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

fun CropImageView.setForegroundImageUrl(
    url: String,
    onLoading: () -> Unit = {},
    onError: (Exception) -> Unit = {}
) {
    Glide.with(context)
        .asBitmap()
        .load(url)
        .into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                this@setForegroundImageUrl.setImageBitmap(resource)
            }

            override fun onLoadCleared(placeholder: Drawable?) {}

            override fun onLoadStarted(placeholder: Drawable?) {
                onLoading()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                onError(Exception("Failed to load image"))
            }
        })
}


fun ImageView.setForegroundImageUrl(
    url: String,
    onLoading: () -> Unit = {},
    onError: (Exception) -> Unit = {}
) {
    Glide.with(context)
        .asBitmap()
        .load(url)
        .into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                this@setForegroundImageUrl.setImageBitmap(resource)
            }

            override fun onLoadCleared(placeholder: Drawable?) {}

            override fun onLoadStarted(placeholder: Drawable?) {
                onLoading()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                onError(Exception("Failed to load image"))
            }
        })
}



