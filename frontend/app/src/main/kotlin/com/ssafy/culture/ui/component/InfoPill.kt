package com.ssafy.culture.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.culture.ui.theme.Spacing

@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp? = 32.dp,
    cornerRadius: Dp = 14.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bold: Boolean = false,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    verticalPadding: Dp = 0.dp,
) {
    Surface(
        modifier = height?.let { fixedHeight -> modifier.height(fixedHeight) } ?: modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = Spacing.Sm,
                vertical = verticalPadding,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = if (bold) {
                    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = contentColor,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
    }
}
