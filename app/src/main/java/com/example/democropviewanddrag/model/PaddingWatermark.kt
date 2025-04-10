package com.example.democropviewanddrag.model

import androidx.annotation.Keep

@Keep
data class PaddingWatermark(
    val top : Int = 0,
    val left : Int = 0,
    val bottom : Int = 0,
    val right : Int = 0,
)