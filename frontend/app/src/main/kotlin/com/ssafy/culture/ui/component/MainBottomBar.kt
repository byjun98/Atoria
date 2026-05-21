package com.ssafy.culture.ui.component

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssafy.culture.ui.motion.CultureMotion

enum class MainDestination {
    Home,
    Map,
    Story,
    Profile,
}

@Composable
fun MainBottomBar(
    selectedDestination: MainDestination,
    onHomeClick: () -> Unit,
    onMapClick: () -> Unit,
    onStoryClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bottomBarShape: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val items = listOf(
        MainNavItem("홈", Icons.Rounded.Home, MainDestination.Home, onHomeClick),
        MainNavItem("지도", Icons.Rounded.Map, MainDestination.Map, onMapClick),
        MainNavItem("스토리", Icons.Rounded.Star, MainDestination.Story, onStoryClick),
        MainNavItem("프로필", Icons.Rounded.Person, MainDestination.Profile, onProfileClick),
    )
    NavigationBar(
        modifier = modifier
            .navigationBarsPadding()
            .height(if (isLandscape) 60.dp else 80.dp)
            .clip(bottomBarShape),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets(
            left = 0.dp,
            top = 0.dp,
            right = 0.dp,
            bottom = 0.dp,
        ),
    ) {
        items.forEach { item ->
            val isSelected = selectedDestination == item.destination
            val iconScale: Float by animateFloatAsState(
                targetValue = if (isSelected) 1.12f else 1f,
                animationSpec = CultureMotion.softSpring(),
                label = "main_nav_icon_scale",
            )
            val labelAlpha: Float by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.74f,
                animationSpec = CultureMotion.softSpring(),
                label = "main_nav_label_alpha",
            )
            NavigationBarItem(
                selected = isSelected,
                onClick = item.onClick,
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    )
                },
                label = if (isLandscape) {
                    null
                } else {
                    {
                        Text(
                            text = item.label,
                            modifier = Modifier.graphicsLayer {
                                alpha = labelAlpha
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private data class MainNavItem(
    val label: String,
    val icon: ImageVector,
    val destination: MainDestination,
    val onClick: () -> Unit,
)
