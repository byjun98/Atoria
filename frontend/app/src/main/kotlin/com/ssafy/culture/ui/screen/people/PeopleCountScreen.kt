package com.ssafy.culture.ui.screen.people

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.StoryProtagonist
import com.ssafy.culture.ui.theme.CultureBackgroundGradient
import com.ssafy.culture.ui.theme.CultureButtonDisabled
import com.ssafy.culture.ui.theme.CultureHairline
import com.ssafy.culture.ui.theme.CultureHairlineSoft
import com.ssafy.culture.ui.theme.CultureInkDisabled
import com.ssafy.culture.ui.theme.CultureInkMuted
import com.ssafy.culture.ui.theme.CultureInkPlaceholder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class PersonForm(
    val id: Int,
    val name: TextFieldValue = TextFieldValue(),
    val age: TextFieldValue = TextFieldValue(),
)

private class PersonFormState(
    val id: Int,
) {
    var name: TextFieldValue by mutableStateOf(TextFieldValue())
    var age: TextFieldValue by mutableStateOf(TextFieldValue())

    fun toForm(): PersonForm =
        PersonForm(
            id = id,
            name = name,
            age = age,
        )
}

@HiltViewModel
class PeopleCountViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
) : ViewModel() {
    fun savePeople(people: List<PersonForm>) {
        storyRepository.saveProtagonistDraft(
            people.map { person ->
                StoryProtagonist(
                    name = person.name.text.trim(),
                    age = person.age.text.toIntOrNull() ?: 0,
                    tendency = "",
                )
            },
        )
    }
}

@Composable
fun PeopleCountRoute(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: PeopleCountViewModel = hiltViewModel(),
) {
    PeopleCountScreen(
        onBack = onBack,
        onNext = { people ->
            viewModel.savePeople(people)
            onNext()
        },
    )
}

@Composable
private fun PeopleCountScreen(
    onBack: () -> Unit,
    onNext: (List<PersonForm>) -> Unit,
) {
    var nextPersonId by remember { mutableIntStateOf(2) }
    val people = remember { mutableStateListOf(PersonFormState(id = 1)) }
    val canProceed: Boolean = people.all { person ->
        person.name.text.trim().isNotBlank() &&
            (person.age.text.toIntOrNull() ?: 0) in ValidAgeRange
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
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
                        onClick = { onNext(people.map(PersonFormState::toForm)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(50),
                        enabled = canProceed,
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
                            text = "다음",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
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
                    PeopleCountHeader(onBack = onBack)
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                item {
                    PeopleCountPanel(
                        count = people.size,
                        canRemove = people.size > MinPeopleCount,
                        onAdd = {
                            people.add(PersonFormState(id = nextPersonId))
                            nextPersonId += 1
                        },
                        onRemove = {
                            if (people.size > MinPeopleCount) {
                                people.removeAt(people.lastIndex)
                            }
                        },
                    )
                }
                items(
                    items = people,
                    key = PersonFormState::id,
                ) { person ->
                    PersonInfoCard(
                        person = person,
                        order = people.indexOfFirst { item -> item.id == person.id } + 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleCountHeader(
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "주인공 정보",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "함께 떠날 사람",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "이름과 나이를 적어 주세요. 성향은 다음 단계에서 골라요.",
                style = MaterialTheme.typography.bodyMedium,
                color = CultureInkMuted,
            )
        }
    }
}

@Composable
private fun PeopleCountPanel(
    count: Int,
    canRemove: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "인원",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${count}명",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CultureInkMuted,
                )
            }
            PeopleRoundButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "인원 제거",
                onClick = onRemove,
                enabled = canRemove,
            )
            Spacer(modifier = Modifier.size(10.dp))
            PeopleRoundButton(
                icon = Icons.Rounded.Add,
                contentDescription = "인원 추가",
                onClick = onAdd,
                enabled = count < MaxPeopleCount,
            )
        }
    }
}

@Composable
private fun PeopleRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, if (enabled) CultureHairline else CultureHairlineSoft),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onBackground else CultureInkDisabled,
            )
        }
    }
}

@Composable
private fun PersonInfoCard(
    person: PersonFormState,
    order: Int,
) {
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
                text = "주인공 $order",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            PeopleInputField(
                label = "이름",
                value = person.name,
                onValueChange = { value -> person.name = value },
                icon = Icons.Rounded.Person,
                imeAction = ImeAction.Next,
            )
            PeopleInputField(
                label = "나이",
                value = person.age,
                onValueChange = { value ->
                    person.age = value.copy(text = value.text.filter(Char::isDigit).take(MaxAgeLength))
                },
                icon = Icons.Rounded.Cake,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            )
        }
    }
}

@Composable
private fun PeopleInputField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .wrapContentHeight()
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(1.dp, CultureHairline, RoundedCornerShape(50))
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CultureInkMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            decorationBox = { innerTextField ->
                if (value.text.isBlank()) {
                    Text(
                        text = label,
                        color = CultureInkPlaceholder,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                innerTextField()
            },
        )
    }
}

private const val MinPeopleCount: Int = 1
private const val MaxPeopleCount: Int = 8
private const val MaxAgeLength: Int = 2
private val ValidAgeRange: IntRange = 1..99
