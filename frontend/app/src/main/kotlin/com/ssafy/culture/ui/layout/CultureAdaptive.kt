package com.ssafy.culture.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CultureHeightClass {
    Compact,
    Medium,
    Expanded,
}

fun cultureHeightClass(maxHeight: Dp): CultureHeightClass =
    when {
        maxHeight < 760.dp -> CultureHeightClass.Compact
        maxHeight < 840.dp -> CultureHeightClass.Medium
        else -> CultureHeightClass.Expanded
    }
