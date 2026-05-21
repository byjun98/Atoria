package com.ssafy.culture.ui.screen.survey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.StoryProtagonist
import com.ssafy.culture.ui.theme.CultureBackgroundGradient
import com.ssafy.culture.ui.theme.CultureButtonDisabled
import com.ssafy.culture.ui.theme.CultureChipBg
import com.ssafy.culture.ui.theme.CultureChipDisabledBg
import com.ssafy.culture.ui.theme.CultureChipInk
import com.ssafy.culture.ui.theme.CultureHairline
import com.ssafy.culture.ui.theme.CultureInkDisabled
import com.ssafy.culture.ui.theme.CultureInkMuted
import com.ssafy.culture.ui.theme.CultureTrackSubtle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MaxSelectableOptionsPerCategory: Int = 2

enum class PersonalityCategory(val titleKr: String, val description: String) {
    Interests("관심 항목", "어떤 문화유산에 끌리나요"),
    Tendency("내 성향", "AI가 이야기와 미션 이유에 반영해요"),
}

enum class PersonalityOption(
    val labelKr: String,
    val category: PersonalityCategory,
    val aiValue: String = labelKr,
) {
    HistoricRuins("역사 깊은 유적", PersonalityCategory.Interests),
    RoyalTombs("왕릉·고분 탐방", PersonalityCategory.Interests),
    BuddhistHeritage("불교 문화유산", PersonalityCategory.Interests),
    LegendsAndTales("전설·설화 관심", PersonalityCategory.Interests),
    Brave("모험", PersonalityCategory.Tendency, "모험적"),
    Calm("신중", PersonalityCategory.Tendency, "신중함"),
    Curious("호기심", PersonalityCategory.Tendency, "호기심형"),
    Emotional("감성", PersonalityCategory.Tendency, "감성형"),
    Creative("창의", PersonalityCategory.Tendency, "창의형"),
    Cooperative("협동", PersonalityCategory.Tendency, "협동형"),
    Careful("소극", PersonalityCategory.Tendency, "소극적"),
}

data class PersonalitySurveyUiState(
    val protagonists: List<StoryProtagonist> = emptyList(),
    val selectedOptions: Set<PersonalityOption> = emptySet(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false,
) {
    val selectionCount: Int get() = selectedOptions.size
    val maxSelectionCount: Int get() = PersonalityCategory.values().size * MaxSelectableOptionsPerCategory
    val canProceed: Boolean
        get() = PersonalityCategory.values().all { category ->
            selectedCount(category) in 1..MaxSelectableOptionsPerCategory
        }

    fun selectedCount(category: PersonalityCategory): Int =
        selectedOptions.count { option -> option.category == category }
}

@HiltViewModel
class PersonalitySurveyViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
) : ViewModel() {
    private val _uiState: MutableStateFlow<PersonalitySurveyUiState> = MutableStateFlow(
        PersonalitySurveyUiState(protagonists = storyRepository.getProtagonistDraft()),
    )
    val uiState: StateFlow<PersonalitySurveyUiState> = _uiState.asStateFlow()

    fun toggleOption(option: PersonalityOption) {
        _uiState.update { state ->
            val next = if (option in state.selectedOptions) {
                state.selectedOptions - option
            } else if (state.selectedCount(option.category) >= MaxSelectableOptionsPerCategory) {
                state.selectedOptions
            } else {
                state.selectedOptions + option
            }
            state.copy(selectedOptions = next, errorMessage = null)
        }
    }

    fun submit() {
        val state: PersonalitySurveyUiState = _uiState.value
        if (!state.canProceed) {
            _uiState.update { it.copy(errorMessage = "관심 항목과 내 성향을 각각 1개 이상 골라 주세요.") }
            return
        }
        val tendency: String = PersonalityCategory.values()
            .mapNotNull { category ->
                val values: String = state.selectedOptions
                    .filter { option -> option.category == category }
                    .sortedBy { option -> option.ordinal }
                    .joinToString(", ") { option -> option.aiValue }
                values.takeIf(String::isNotBlank)?.let { "${category.titleKr}: $it" }
            }
            .joinToString(" / ")
        val updatedProtagonists: List<StoryProtagonist> = state.protagonists.map { protagonist ->
            protagonist.copy(tendency = tendency)
        }
        storyRepository.saveProtagonistDraft(updatedProtagonists)
        _uiState.update { it.copy(isComplete = true) }
    }

    fun consumeComplete() {
        _uiState.update { it.copy(isComplete = false) }
    }
}

@Composable
fun PersonalitySurveyRoute(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: PersonalitySurveyViewModel = hiltViewModel(),
) {
    val uiState: PersonalitySurveyUiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            viewModel.consumeComplete()
            onNext()
        }
    }
    PersonalitySurveyScreen(
        uiState = uiState,
        onBack = onBack,
        onToggleOption = viewModel::toggleOption,
        onSubmit = viewModel::submit,
    )
}

@Composable
private fun PersonalitySurveyScreen(
    uiState: PersonalitySurveyUiState,
    onBack: () -> Unit,
    onToggleOption: (PersonalityOption) -> Unit,
    onSubmit: () -> Unit,
) {
    val categories = remember { PersonalityCategory.values().toList() }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Color.Transparent,
        bottomBar = {
            SurveyBottomBar(
                uiState = uiState,
                onSubmit = onSubmit,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CultureBackgroundGradient),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SurveyHeader(onBack = onBack)
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                item {
                    SurveyIntroCard(
                        protagonists = uiState.protagonists,
                        selectedOptions = uiState.selectedOptions,
                        selectionCount = uiState.selectionCount,
                        maxSelectionCount = uiState.maxSelectionCount,
                    )
                }
                items(count = categories.size, key = { index -> categories[index].name }) { index ->
                    PersonalityCategoryCard(
                        category = categories[index],
                        selectedOptions = uiState.selectedOptions,
                        onToggle = onToggleOption,
                    )
                }
                if (uiState.errorMessage != null) {
                    item {
                        SurveyStatusCard(text = uiState.errorMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveyHeader(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "뒤로가기",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "스토리 재료",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "나에게 맞는\n이야기 재료를 골라요",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SurveyIntroCard(
    protagonists: List<StoryProtagonist>,
    selectedOptions: Set<PersonalityOption>,
    selectionCount: Int,
    maxSelectionCount: Int,
) {
    val countSummary: String = PersonalityCategory.values().joinToString(" · ") { category ->
        "${category.titleKr} ${selectedOptions.count { option -> option.category == category }}/$MaxSelectableOptionsPerCategory"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (protagonists.isEmpty()) {
                    "관심 항목과 내 성향을 골라 주세요"
                } else {
                    "${protagonists.joinToString("·") { it.name.ifBlank { "주인공" } }}와 함께할 이야기"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "각 항목에서 최대 ${MaxSelectableOptionsPerCategory}개까지 고를 수 있어요. 선택한 성향은 이야기 분기와 미션 이유에 반영돼요.",
                style = MaterialTheme.typography.bodyMedium,
                color = CultureInkMuted,
            )
            Text(
                text = countSummary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SelectionProgressBar(
                    current = selectionCount,
                    total = maxSelectionCount,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$selectionCount/$maxSelectionCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SelectionProgressBar(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val ratio: Float = if (total == 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(6.dp)
            .background(
                color = CultureTrackSubtle,
                shape = RoundedCornerShape(50),
            ),
    ) {
        if (ratio > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                    ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalityCategoryCard(
    category: PersonalityCategory,
    selectedOptions: Set<PersonalityOption>,
    onToggle: (PersonalityOption) -> Unit,
) {
    val options: List<PersonalityOption> = remember(category) {
        PersonalityOption.values().filter { option -> option.category == category }
    }
    val selectedCount: Int = selectedOptions.count { option -> option.category == category }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = category.titleKr,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "선택 $selectedCount/$MaxSelectableOptionsPerCategory",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = CultureInkMuted,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    val isSelected: Boolean = option in selectedOptions
                    val isDisabled: Boolean = !isSelected && selectedCount >= MaxSelectableOptionsPerCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(option) },
                        enabled = !isDisabled,
                        label = {
                            Text(
                                text = option.labelKr,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CultureChipBg,
                            labelColor = CultureChipInk,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            disabledContainerColor = CultureChipDisabledBg,
                            disabledLabelColor = CultureInkDisabled,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = !isDisabled,
                            selected = isSelected,
                            borderColor = Color.Transparent,
                            selectedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SurveyStatusCard(
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SurveyBottomBar(
    uiState: PersonalitySurveyUiState,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = Color.White,
        border = BorderStroke(1.dp, CultureHairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(50),
                enabled = uiState.canProceed && !uiState.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = CultureButtonDisabled,
                    disabledContentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
            ) {
                Text(
                    text = if (uiState.canProceed) {
                        "${uiState.selectionCount}개 선택 완료"
                    } else {
                        "항목별로 1개 이상 골라 주세요"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

