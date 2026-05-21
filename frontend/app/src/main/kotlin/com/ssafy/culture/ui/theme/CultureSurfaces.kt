package com.ssafy.culture.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CultureBackgroundGradient: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFB7C9),
        Color(0xFFFFDDE8),
        Color(0xFFFFF4CE),
    ),
)

val CultureCardShape = RoundedCornerShape(20.dp)
val CulturePillShape = RoundedCornerShape(50)
val CultureCardElevation: Dp = 5.dp
val CultureCardPadding: Dp = 22.dp
val CultureScreenPadding: Dp = 24.dp
val CultureSectionSpacing: Dp = 24.dp

val CultureCanvas: Color = Color(0xFFFBF7F8)
val CultureHairline: Color = Color(0xFFEDE5E8)
val CultureHairlineSoft: Color = Color(0xFFF5EEF0)

val CultureChipBg: Color = Color(0xFFFFE8EF)
val CultureChipInk: Color = Color(0xFF8D3C56)
val CultureChipDisabledBg: Color = Color(0xFFF2EAED)

val CultureInkMuted: Color = Color(0xFF7A6E73)
val CultureInkPlaceholder: Color = Color(0xFFB8AAB0)
val CultureInkDisabled: Color = Color(0xFFC9BCC2)
val CultureIconMuted: Color = Color(0xFFC9BCC2)

val CultureButtonDisabled: Color = Color(0xFFE9D8DE)

val CultureTrackSubtle: Color = Color(0xFFF2EAED)
