package com.example.democropviewanddrag.extension

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.democropviewanddrag.R
import com.example.democropviewanddrag.customview.CropImageView
import com.example.democropviewanddrag.customview.PositionWatermark
import com.example.democropviewanddrag.model.PaddingWatermark

/** Add watermark for result bitmap*/
fun Bitmap?.addWatermark(
    resources: Resources,
    padding: PaddingWatermark = PaddingWatermark(),
    position: PositionWatermark = PositionWatermark.BOTTOM_END,
    @DrawableRes resId: Int = R.drawable.bg_watermark
): Bitmap? {
    val originalBitmap = this ?: return null

    return try {
        val result = createBitmap(originalBitmap.width, originalBitmap.height)
        val watermark = BitmapFactory.decodeResource(resources, resId) ?: return null

        val left = when (position) {
            PositionWatermark.TOP_START, PositionWatermark.BOTTOM_START -> padding.left
            PositionWatermark.TOP_END, PositionWatermark.BOTTOM_END -> originalBitmap.width - watermark.width - padding.right
        }.coerceAtLeast(0)

        val top = when (position) {
            PositionWatermark.TOP_START, PositionWatermark.TOP_END -> padding.top
            PositionWatermark.BOTTOM_START, PositionWatermark.BOTTOM_END -> originalBitmap.height - watermark.height - padding.bottom
        }.coerceAtLeast(0)

        Canvas(result).apply {
            drawBitmap(originalBitmap, 0f, 0f, null)
            drawBitmap(watermark, left.toFloat(), top.toFloat(), null)
        }

        result
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

fun Bitmap.trimTransparent(): Bitmap {
    val width = width
    val height = height
    var top = 0
    var bottom = height
    var left = 0
    var right = width

    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)

    // Tìm top
    loop@ for (y in 0 until height) {
        for (x in 0 until width) {
            if (pixels[y * width + x] != 0) {
                top = y
                break@loop
            }
        }
    }

    // Tìm bottom
    loop@ for (y in height - 1 downTo 0) {
        for (x in 0 until width) {
            if (pixels[y * width + x] != 0) {
                bottom = y + 1
                break@loop
            }
        }
    }

    // Tìm left
    loop@ for (x in 0 until width) {
        for (y in top until bottom) {
            if (pixels[y * width + x] != 0) {
                left = x
                break@loop
            }
        }
    }

    // Tìm right
    loop@ for (x in width - 1 downTo 0) {
        for (y in top until bottom) {
            if (pixels[y * width + x] != 0) {
                right = x + 1
                break@loop
            }
        }
    }

    val newWidth = right - left
    val newHeight = bottom - top

    if (newWidth <= 0 || newHeight <= 0) return this // Tránh lỗi

    return Bitmap.createBitmap(this, left, top, newWidth, newHeight)
}



