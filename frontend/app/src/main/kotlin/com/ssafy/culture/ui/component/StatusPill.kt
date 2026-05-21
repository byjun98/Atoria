package com.ssafy.culture.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.culture.ui.theme.Spacing

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
    ) {
        if (actionLabel == null) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                style = if (bold) {
                    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = contentColor,
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}
