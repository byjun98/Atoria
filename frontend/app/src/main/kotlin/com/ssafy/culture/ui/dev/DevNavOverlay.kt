package com.ssafy.culture.ui.dev

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.motion.tossClickable
import com.ssafy.culture.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.DevNavOverlay(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute: String? = backStackEntry?.destination?.route
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = modifier
            .align(if (isLandscape) Alignment.TopEnd else Alignment.BottomEnd)
            .padding(
                top = if (isLandscape) 56.dp else 0.dp,
                end = 12.dp,
                bottom = if (isLandscape) 0.dp else 96.dp,
            )
            .tossClickable(
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = { sheetVisible = true },
            ),
        shape = RoundedCornerShape(50),
        color = Color(0xCC1B1B1F),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Build,
                contentDescription = "Dev navigation",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "DEV",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            )
        }
    }

    if (sheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            DevSheetContent(
                currentRoute = currentRoute,
                onJump = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                    scope.launch {
                        sheetState.hide()
                        sheetVisible = false
                    }
                },
                onPopBack = {
                    navController.popBackStack()
                    scope.launch {
                        sheetState.hide()
                        sheetVisible = false
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevSheetContent(
    currentRoute: String?,
    onJump: (DevDestination) -> Unit,
    onPopBack: () -> Unit,
) {
    var mockApiOn by remember { mutableStateOf(MockApiConfig.enabled) }
    LaunchedEffect(mockApiOn) {
        MockApiConfig.enabled = mockApiOn
    }
    val groups: List<DevGroup> = remember { DevGroup.values().toList() }
    val destinationsByGroup: Map<DevGroup, List<DevDestination>> = remember {
        DevDestination.values().groupBy { it.group }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.Lg,
            end = Spacing.Lg,
            top = Spacing.Sm,
            bottom = Spacing.Xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.Md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xxs)) {
                Text(
                    text = "Dev Tools",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "현재 라우트: ${currentRoute ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            DevToggleRow(
                label = "Mock API",
                description = "켜면 모든 네트워크 호출이 더미 데이터로 대체돼요.",
                checked = mockApiOn,
                onCheckedChange = { mockApiOn = it },
            )
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .tossClickable(
                        pressedScale = CultureMotion.SubtlePressedScale,
                        onClick = onPopBack,
                    ),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = "← 이전 화면으로 돌아가기 (popBackStack)",
                    modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        groups.forEach { group ->
            val destinations: List<DevDestination> = destinationsByGroup[group].orEmpty()
            if (destinations.isNotEmpty()) {
                item(key = "header-${group.name}") {
                    Text(
                        text = group.titleKr,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item(key = "chips-${group.name}") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        destinations.forEach { destination ->
                            val isCurrent: Boolean = currentRoute == destination.route
                            FilterChip(
                                selected = isCurrent,
                                onClick = { onJump(destination) },
                                label = {
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = MaterialTheme.colorScheme.onBackground,
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

