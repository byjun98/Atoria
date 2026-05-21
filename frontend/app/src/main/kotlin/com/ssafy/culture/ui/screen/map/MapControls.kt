package com.ssafy.culture.ui.screen.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.motion.tossClickable

@Immutable
data class MapKeywordButtonState(
    val id: String,
    val label: String,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
)

@Composable
fun MapControls(
    keywords: List<MapKeywordButtonState>,
    onKeywordClick: (String) -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    onResearchHereClick: () -> Unit,
    modifier: Modifier = Modifier,
    isZoomInEnabled: Boolean = true,
    isZoomOutEnabled: Boolean = true,
    isCurrentLocationEnabled: Boolean = true,
    isResearchHereVisible: Boolean = true,
    isResearchHereEnabled: Boolean = true,
    isFollowingCurrentLocation: Boolean = false,
    researchHereLabel: String = "현 지도에서 재검색",
): Unit {
    Box(modifier = modifier) {
        MapKeywordButtonRow(
            keywords = keywords,
            onKeywordClick = onKeywordClick,
            onResearchHereClick = onResearchHereClick,
            isResearchHereVisible = isResearchHereVisible,
            isResearchHereEnabled = isResearchHereEnabled,
            researchHereLabel = researchHereLabel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
        MapControlRail(
            onZoomInClick = onZoomInClick,
            onZoomOutClick = onZoomOutClick,
            onCurrentLocationClick = onCurrentLocationClick,
            isZoomInEnabled = isZoomInEnabled,
            isZoomOutEnabled = isZoomOutEnabled,
            isCurrentLocationEnabled = isCurrentLocationEnabled,
            isFollowingCurrentLocation = isFollowingCurrentLocation,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun MapKeywordButtonRow(
    keywords: List<MapKeywordButtonState>,
    onKeywordClick: (String) -> Unit,
    onResearchHereClick: () -> Unit,
    isResearchHereVisible: Boolean,
    isResearchHereEnabled: Boolean,
    researchHereLabel: String,
    modifier: Modifier = Modifier,
): Unit {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isResearchHereVisible) {
            item(key = "research-here") {
                MapResearchChip(
                    label = researchHereLabel,
                    isEnabled = isResearchHereEnabled,
                    onClick = onResearchHereClick,
                )
            }
        }
        items(
            items = keywords,
            key = MapKeywordButtonState::id,
        ) { keyword ->
            MapKeywordButton(
                keyword = keyword,
                onClick = { onKeywordClick(keyword.id) },
            )
        }
    }
}

@Composable
private fun MapResearchChip(
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    val containerColor: Color = MaterialTheme.colorScheme.primary
    val textColor: Color = Color.White
    val chipAlpha: Float = if (isEnabled) EnabledControlAlpha else DisabledControlAlpha
    Surface(
        modifier = modifier
            .height(40.dp)
            .alpha(chipAlpha)
            .tossClickable(
                enabled = isEnabled,
                role = Role.Button,
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(KeywordCornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, containerColor),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = textColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MapKeywordButton(
    keyword: MapKeywordButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    val containerColor: Color = if (keyword.isSelected) MaterialTheme.colorScheme.primary else Color.White
    val borderColor: Color = if (keyword.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val textColor: Color = if (keyword.isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val chipAlpha: Float = if (keyword.isEnabled) EnabledControlAlpha else DisabledControlAlpha
    Surface(
        modifier = modifier
            .height(40.dp)
            .alpha(chipAlpha)
            .tossClickable(
                enabled = keyword.isEnabled,
                role = Role.Button,
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(KeywordCornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (keyword.isSelected) 6.dp else 4.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = keyword.label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MapControlRail(
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    isZoomInEnabled: Boolean,
    isZoomOutEnabled: Boolean,
    isCurrentLocationEnabled: Boolean,
    isFollowingCurrentLocation: Boolean,
    modifier: Modifier = Modifier,
): Unit {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(ZoomControlCornerRadius),
            color = Color.White,
            shadowElevation = MapControlElevation,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MapIconControlButton(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "확대",
                    onClick = onZoomInClick,
                    isEnabled = isZoomInEnabled,
                )
                HorizontalDivider(
                    modifier = Modifier.width(28.dp),
                    color = MapDivider,
                )
                MapIconControlButton(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "축소",
                    onClick = onZoomOutClick,
                    isEnabled = isZoomOutEnabled,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = CircleShape,
            color = if (isFollowingCurrentLocation) MaterialTheme.colorScheme.primary else Color.White,
            shadowElevation = MapControlElevation,
        ) {
            MapIconControlButton(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = if (isFollowingCurrentLocation) "현재 위치 추적 중" else "현재 위치로 이동",
                onClick = onCurrentLocationClick,
                isEnabled = isCurrentLocationEnabled,
                tint = if (isFollowingCurrentLocation) Color.White else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MapIconControlButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
): Unit {
    val buttonAlpha: Float = if (isEnabled) EnabledControlAlpha else DisabledControlAlpha
    Box(
        modifier = modifier
            .size(MapIconButtonSize)
            .alpha(buttonAlpha)
            .tossClickable(
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

private val MapDivider: Color = Color(0xFFF1D8E0)
private val MapIconButtonSize: Dp = 48.dp
private val KeywordCornerRadius: Dp = 50.dp
private val ZoomControlCornerRadius: Dp = 18.dp
private val MapControlElevation: Dp = 8.dp
private const val EnabledControlAlpha = 1f
private const val DisabledControlAlpha = 0.42f
