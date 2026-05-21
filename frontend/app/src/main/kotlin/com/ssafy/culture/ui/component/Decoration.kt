package com.ssafy.culture.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun DecorativeCloud(
    modifier: Modifier = Modifier,
    colors: List<Color>,
) {
    Canvas(modifier = modifier) {
        val baseColor = colors.first()
        val highlight = colors.last()
        drawCircle(
            color = baseColor,
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.25f, size.height * 0.56f),
        )
        drawCircle(
            color = highlight,
            radius = size.minDimension * 0.3f,
            center = Offset(size.width * 0.52f, size.height * 0.42f),
        )
        drawCircle(
            color = baseColor.copy(alpha = 0.9f),
            radius = size.minDimension * 0.25f,
            center = Offset(size.width * 0.76f, size.height * 0.54f),
        )
        drawRoundRect(
            color = highlight,
            topLeft = Offset(size.width * 0.15f, size.height * 0.48f),
            size = Size(size.width * 0.7f, size.height * 0.34f),
            cornerRadius = CornerRadius(size.height * 0.2f),
        )
    }
}
