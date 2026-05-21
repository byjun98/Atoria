package com.ssafy.culture.ui.screen.story

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.R
import com.ssafy.culture.data.ebook.EbookFileRepository
import com.ssafy.culture.data.ebook.EbookRepository
import com.ssafy.culture.data.ebook.FairyTaleImageRepository
import com.ssafy.culture.data.preferences.AppPreferenceStore
import com.ssafy.culture.data.route.RouteHistoryStore
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.EbookContentPage
import com.ssafy.culture.domain.model.EbookResult
import com.ssafy.culture.domain.model.EbookStatus
import com.ssafy.culture.domain.model.RoutePoint
import com.ssafy.culture.domain.model.StoryDetail
import com.ssafy.culture.ui.component.CultureAsyncImage
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.motion.tossClickable
import com.ssafy.culture.ui.theme.CultureTheme
import com.ssafy.culture.ui.theme.Spacing
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import com.ssafy.culture.data.ebook.FairyTaleProtagonistCandidate
import com.ssafy.culture.data.ebook.FairyTaleProtagonistSelectionPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog

data class StoryBookViewerUiState(
    val book: StoryBook? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val showCoachmark: Boolean = false,
    val isCreatingFairyTaleVersion: Boolean = false,
    val hasFairyTaleVersion: Boolean = false,
    val isFairyTaleVersion: Boolean = false,
    val fairyTaleMessage: String? = null,
    val currentPageIndex: Int = 0,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val exportCaptureRequestId: Long = 0L,
    val exportCaptureBook: StoryBook? = null,
    val exportUri: Uri? = null,
    val exportFileName: String? = null,
    val protagonistSelection: ProtagonistSelectionUiState? = null,
)

data class ProtagonistSelectionUiState(
    val pageId: String,
    val title: String,
    val progressLabel: String,
    val previewBitmap: Bitmap,
    val candidates: List<FairyTaleProtagonistCandidate>,
    val selectedIndex: Int?,
)

@HiltViewModel
class StoryBookViewerViewModel @Inject constructor(
    private val ebookRepository: EbookRepository,
    private val ebookFileRepository: EbookFileRepository,
    private val fairyTaleImageRepository: FairyTaleImageRepository,
    private val appPreferenceStore: AppPreferenceStore,
    private val storyRepository: StoryRepository,
    private val routeHistoryStore: RouteHistoryStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val bookId: String = savedStateHandle["ebookId"] ?: LatestBookId
    private val _uiState: MutableStateFlow<StoryBookViewerUiState> = MutableStateFlow(
        StoryBookViewerUiState(),
    )
    val uiState: StateFlow<StoryBookViewerUiState> = _uiState.asStateFlow()
    private var originalBook: StoryBook? = null
    private var fairyTaleBook: StoryBook? = null
    private var pendingFairyTaleCreation: PendingFairyTaleCreation? = null

    init {
        loadBook()
    }

    fun dismissCoachmark() {
        if (!_uiState.value.showCoachmark) return
        _uiState.update { state -> state.copy(showCoachmark = false) }
        viewModelScope.launch {
            runCatching { appPreferenceStore.setHasSeenEbookHint(true) }
        }
    }

    fun handleFairyTaleAction() {
        if (_uiState.value.isCreatingFairyTaleVersion) return
        if (_uiState.value.isFairyTaleVersion) {
            showOriginalVersion()
            return
        }
        val existingFairyTaleBook: StoryBook? = fairyTaleBook
        if (existingFairyTaleBook != null) {
            showFairyTaleVersion(existingFairyTaleBook)
            return
        }
        createFairyTaleVersion()
    }

    fun updateCurrentPage(pageIndex: Int) {
        _uiState.update { state ->
            val coercedPageIndex: Int = state.book.coercePageIndex(pageIndex)
            if (state.currentPageIndex == coercedPageIndex) {
                state
            } else {
                state.copy(currentPageIndex = coercedPageIndex)
            }
        }
    }

    fun exportCurrentBook() {
        val book: StoryBook = _uiState.value.book ?: return
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isExporting = true,
                    exportMessage = "PDF 내보내기를 준비하고 있어요.",
                    exportCaptureBook = null,
                    exportUri = null,
                    exportFileName = null,
                    errorMessage = null,
                )
            }
            runCatching {
                prepareBookForCapture(book)
            }.onSuccess { captureBook ->
                _uiState.update { state ->
                    state.copy(
                        exportMessage = "가로 전체화면으로 PDF를 캡쳐하고 있어요.",
                        exportCaptureRequestId = System.nanoTime(),
                        exportCaptureBook = captureBook,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isExporting = false,
                        exportMessage = throwable.localizedMessage ?: "PDF 내보내기에 실패했어요.",
                        exportCaptureBook = null,
                    )
                }
            }
        }
    }

    fun saveCapturedExport(spreadBitmaps: List<Bitmap>) {
        val book: StoryBook = _uiState.value.exportCaptureBook ?: _uiState.value.book ?: return
        viewModelScope.launch {
            runCatching {
                ebookFileRepository.saveCapturedPdf(
                    bookId = book.id,
                    title = book.title,
                    spreadBitmaps = spreadBitmaps,
                )
            }.onSuccess { file ->
                val uri: Uri = ebookFileRepository.getShareUri(file)
                _uiState.update { state ->
                    state.copy(
                        isExporting = false,
                        exportMessage = null,
                        exportCaptureBook = null,
                        exportUri = uri,
                        exportFileName = file.name,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isExporting = false,
                        exportMessage = throwable.localizedMessage ?: "PDF 내보내기에 실패했어요.",
                        exportCaptureBook = null,
                    )
                }
            }
            spreadBitmaps.forEach { bitmap -> bitmap.recycle() }
        }
    }

    fun failCapturedExport(throwable: Throwable) {
        _uiState.update { state ->
            state.copy(
                isExporting = false,
                exportMessage = throwable.localizedMessage ?: "PDF 캡쳐에 실패했어요.",
                exportCaptureBook = null,
            )
        }
    }

    fun consumeExportEvent() {
        _uiState.update { state ->
            state.copy(exportUri = null, exportFileName = null)
        }
    }

    private fun saveFairyTaleModePreference(isEnabled: Boolean) {
        val ebookId: String = originalBook?.id ?: return
        viewModelScope.launch {
            runCatching {
                appPreferenceStore.setEbookFairyTaleModeEnabled(
                    ebookId = ebookId,
                    isEnabled = isEnabled,
                )
            }
        }
    }

    private suspend fun prepareBookForCapture(book: StoryBook): StoryBook {
        val capturePages: List<StoryBookPage> = book.pages.map { page ->
            val imageUrl: String = page.imageUrl.takeUnlessBlank() ?: return@map page
            if (page.bitmap != null || page.illustrationBitmap != null) return@map page
            runCatching {
                ebookFileRepository.downloadImageBitmap(
                    imageUrl = imageUrl,
                    maxDimension = PdfCaptureImageMaxDimension,
                )
            }.map { bitmap ->
                page.copy(illustrationBitmap = bitmap)
            }.getOrDefault(page)
        }
        return book.copy(pages = capturePages)
    }

    private fun showOriginalVersion() {
        val book: StoryBook = originalBook ?: return
        saveFairyTaleModePreference(isEnabled = false)
        _uiState.update { state ->
            state.copy(
                book = book,
                isFairyTaleVersion = false,
                fairyTaleMessage = "원본 동화로 돌아왔어요.",
                currentPageIndex = book.coercePageIndex(state.currentPageIndex),
            )
        }
    }

    private fun showFairyTaleVersion(book: StoryBook) {
        saveFairyTaleModePreference(isEnabled = true)
        _uiState.update { state ->
            state.copy(
                book = book,
                isFairyTaleVersion = true,
                fairyTaleMessage = "동화버전을 보고 있어요. 원본도 다시 볼 수 있어요.",
                currentPageIndex = book.coercePageIndex(state.currentPageIndex),
            )
        }
    }

    fun createFairyTaleVersion() {
        val sourceBook: StoryBook = originalBook ?: _uiState.value.book ?: return
        if (_uiState.value.isCreatingFairyTaleVersion || _uiState.value.isFairyTaleVersion) return
        val sourcePages: List<StoryBookPage> = sourceBook.pages
        val targetPages: List<StoryBookPage> = sourcePages.filter(StoryBookPage::canCreateFairyTaleImage)
        if (targetPages.isEmpty()) {
            _uiState.update { state ->
                state.copy(fairyTaleMessage = "동화풍으로 바꿀 사진 페이지가 없어요.")
            }
            return
        }
        pendingFairyTaleCreation = PendingFairyTaleCreation(
            sourceBook = sourceBook,
            sourcePages = sourcePages,
            targetPages = targetPages,
        )
        showNextProtagonistSelection()
    }

    fun pickProtagonistAt(
        normalizedX: Float,
        normalizedY: Float,
    ) {
        val selection: ProtagonistSelectionUiState = _uiState.value.protagonistSelection ?: return
        val existingIndex: Int? = selection.candidates.indexOfFirst { candidate ->
            normalizedX in candidate.normalizedLeft..candidate.normalizedRight &&
                normalizedY in candidate.normalizedTop..candidate.normalizedBottom
        }.takeIf { index -> index >= 0 }
        if (existingIndex != null) {
            _uiState.update { state ->
                state.copy(
                    protagonistSelection = selection.copy(selectedIndex = existingIndex),
                )
            }
            return
        }
        val manualCandidate = FairyTaleProtagonistCandidate.around(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
        )
        _uiState.update { state ->
            state.copy(
                protagonistSelection = selection.copy(
                    candidates = selection.candidates + manualCandidate,
                    selectedIndex = selection.candidates.size,
                ),
            )
        }
    }

    fun confirmProtagonistSelection() {
        val pending: PendingFairyTaleCreation = pendingFairyTaleCreation ?: return
        val selection: ProtagonistSelectionUiState = _uiState.value.protagonistSelection ?: return
        val selectedCandidate: FairyTaleProtagonistCandidate? = selection.selectedIndex
            ?.let(selection.candidates::getOrNull)
        pendingFairyTaleCreation = pending.copy(
            nextSelectionIndex = pending.nextSelectionIndex + 1,
            selectedCandidates = if (selectedCandidate == null) {
                pending.selectedCandidates
            } else {
                pending.selectedCandidates + (selection.pageId to selectedCandidate)
            },
        )
        _uiState.update { state ->
            state.copy(protagonistSelection = null)
        }
        showNextProtagonistSelection()
    }

    fun skipProtagonistSelection() {
        val pending: PendingFairyTaleCreation = pendingFairyTaleCreation ?: return
        pendingFairyTaleCreation = pending.copy(
            nextSelectionIndex = pending.nextSelectionIndex + 1,
        )
        _uiState.update { state ->
            state.copy(protagonistSelection = null)
        }
        showNextProtagonistSelection()
    }

    fun cancelProtagonistSelection() {
        pendingFairyTaleCreation = null
        _uiState.update { state ->
            state.copy(
                isCreatingFairyTaleVersion = false,
                fairyTaleMessage = null,
                protagonistSelection = null,
            )
        }
    }

    private fun showNextProtagonistSelection() {
        val pending: PendingFairyTaleCreation = pendingFairyTaleCreation ?: return
        val page: StoryBookPage? = pending.targetPages.getOrNull(pending.nextSelectionIndex)
        if (page == null) {
            pendingFairyTaleCreation = null
            generateFairyTaleVersion(
                sourceBook = pending.sourceBook,
                sourcePages = pending.sourcePages,
                targetPages = pending.targetPages,
                selectedCandidates = pending.selectedCandidates,
            )
            return
        }
        val sourceImageUrl: String = page.imageUrl.takeUnlessBlank()
            ?: run {
                pendingFairyTaleCreation = pending.copy(
                    nextSelectionIndex = pending.nextSelectionIndex + 1,
                )
                showNextProtagonistSelection()
                return
            }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isCreatingFairyTaleVersion = true,
                    fairyTaleMessage = "주인공 후보를 찾고 있어요.",
                    protagonistSelection = null,
                )
            }
            runCatching {
                fairyTaleImageRepository.prepareProtagonistSelection(sourceImageUrl)
            }.onSuccess { preview ->
                _uiState.update { state ->
                    state.copy(
                        fairyTaleMessage = "주인공을 확인해 주세요.",
                        protagonistSelection = preview.toUiState(
                            page = page,
                            currentIndex = pending.nextSelectionIndex,
                            totalCount = pending.targetPages.size,
                        ),
                    )
                }
            }.onFailure {
                pendingFairyTaleCreation = pending.copy(
                    nextSelectionIndex = pending.nextSelectionIndex + 1,
                )
                _uiState.update { state ->
                    state.copy(protagonistSelection = null)
                }
                showNextProtagonistSelection()
            }
        }
    }

    private fun generateFairyTaleVersion(
        sourceBook: StoryBook,
        sourcePages: List<StoryBookPage>,
        targetPages: List<StoryBookPage>,
        selectedCandidates: Map<String, FairyTaleProtagonistCandidate>,
    ) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isCreatingFairyTaleVersion = true,
                    fairyTaleMessage = "동화버전 이미지를 준비하고 있어요.",
                    protagonistSelection = null,
                )
            }
            var generatedCount = 0
            val failedPageTitles = mutableListOf<String>()
            var attemptedCount = 0
            val convertedPages: List<StoryBookPage> = sourcePages.map pageMap@ { page ->
                val sourceImageUrl: String = page.imageUrl.takeUnlessBlank()
                    ?: return@pageMap page
                if (!page.canCreateFairyTaleImage()) return@pageMap page
                attemptedCount += 1
                _uiState.update { state ->
                    state.copy(fairyTaleMessage = "동화버전 이미지 $attemptedCount/${targetPages.size}장을 만들고 있어요.")
                }
                runCatching {
                    fairyTaleImageRepository.createFairyTaleImage(
                        sourceImageUrl = sourceImageUrl,
                        bookTitle = sourceBook.title,
                        pageTitle = page.title,
                        pageText = page.body,
                        protagonistCandidate = selectedCandidates[page.id],
                    )
                }.map { bitmap ->
                    fairyTaleImageRepository.saveFairyTaleImage(
                        bookId = sourceBook.id,
                        pageId = page.id,
                        sourceImageUrl = sourceImageUrl,
                        bitmap = bitmap,
                    )
                    generatedCount += 1
                    page.toFairyTalePage(bitmap)
                }.getOrElse {
                    failedPageTitles += page.title
                    page
                }
            }
            if (generatedCount == 0 || failedPageTitles.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        isCreatingFairyTaleVersion = false,
                        fairyTaleMessage = "동화버전 이미지를 완성하지 못했어요. 다시 시도해 주세요.",
                    )
                }
                return@launch
            }
            val convertedBook = sourceBook.copy(
                id = "${sourceBook.id}-fairy-tale",
                pages = convertedPages,
            )
            fairyTaleBook = convertedBook
            runCatching {
                appPreferenceStore.setEbookFairyTaleModeEnabled(
                    ebookId = sourceBook.id,
                    isEnabled = true,
                )
            }
            _uiState.update { state ->
                state.copy(
                    book = convertedBook,
                    isCreatingFairyTaleVersion = false,
                    hasFairyTaleVersion = true,
                    isFairyTaleVersion = true,
                    fairyTaleMessage = "동화버전을 새로 만들었어요. 원본도 다시 볼 수 있어요.",
                    currentPageIndex = convertedBook.coercePageIndex(state.currentPageIndex),
                )
            }
        }
    }

    private fun loadBook() {
        viewModelScope.launch {
            pendingFairyTaleCreation = null
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                    isCreatingFairyTaleVersion = false,
                    fairyTaleMessage = null,
                    isExporting = false,
                    exportMessage = null,
                    exportCaptureBook = null,
                    protagonistSelection = null,
                )
            }
            runCatching {
                val result: EbookResult = if (bookId == LatestBookId) {
                    ebookRepository.getLatestResult()
                } else {
                    ebookRepository.getEbookDetail(bookId)
                }
                val baseBook: StoryBook = if (result.fileUrl.isNotBlank()) {
                    result.toStoryBook(
                        pages = renderPdfPages(result),
                    )
                } else {
                    result.toStoryBookFromContent()
                }
                appendRouteMapPageIfAvailable(book = baseBook, result = result)
            }.onSuccess { book ->
                val hasSeenHint = runCatching { appPreferenceStore.hasSeenEbookHint.first() }
                    .getOrDefault(true)
                val savedFairyTaleBook: StoryBook? = loadSavedFairyTaleBook(book)
                val shouldShowFairyTaleVersion: Boolean = savedFairyTaleBook != null &&
                    runCatching {
                        appPreferenceStore.isEbookFairyTaleModeEnabled(book.id)
                    }.getOrDefault(false)
                val displayBook: StoryBook = if (shouldShowFairyTaleVersion) {
                    savedFairyTaleBook ?: book
                } else {
                    book
                }
                originalBook = book
                fairyTaleBook = savedFairyTaleBook
                _uiState.update { state ->
                    state.copy(
                        book = displayBook,
                        isLoading = false,
                        showCoachmark = !hasSeenHint,
                        isCreatingFairyTaleVersion = false,
                        hasFairyTaleVersion = savedFairyTaleBook != null,
                        isFairyTaleVersion = shouldShowFairyTaleVersion,
                        fairyTaleMessage = null,
                        protagonistSelection = null,
                        currentPageIndex = displayBook.coercePageIndex(state.currentPageIndex),
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "결과물을 불러오지 못했어요.",
                        isCreatingFairyTaleVersion = false,
                        protagonistSelection = null,
                    )
                }
            }
        }
    }

    private suspend fun loadSavedFairyTaleBook(sourceBook: StoryBook): StoryBook? {
        val targetPageCount: Int = sourceBook.pages.count(StoryBookPage::canCreateFairyTaleImage)
        if (targetPageCount == 0) return null
        var savedCount = 0
        val convertedPages: List<StoryBookPage> = sourceBook.pages.map { page ->
            val sourceImageUrl: String = page.imageUrl.takeUnlessBlank()
                ?: return@map page
            if (!page.canCreateFairyTaleImage()) return@map page
            val savedBitmap: Bitmap = fairyTaleImageRepository.loadSavedFairyTaleImage(
                bookId = sourceBook.id,
                pageId = page.id,
                sourceImageUrl = sourceImageUrl,
            ) ?: return@map page
            savedCount += 1
            page.toFairyTalePage(savedBitmap)
        }
        if (savedCount != targetPageCount) return null
        return sourceBook.copy(
            id = "${sourceBook.id}-fairy-tale",
            pages = convertedPages,
        )
    }

    private suspend fun appendRouteMapPageIfAvailable(
        book: StoryBook,
        result: EbookResult,
    ): StoryBook {
        val storyId: Long = routeHistoryStore.getActiveStoryId() ?: return book
        val story: StoryDetail = runCatching {
            storyRepository.getStoryDetail(storyId)
        }.getOrNull() ?: return book
        val markers: List<RouteChapterMarker> = story.chapters
            .mapNotNull { chapter ->
                val placeId: Long = chapter.placeId ?: return@mapNotNull null
                runCatching {
                    storyRepository.getChapterDetail(
                        storyId = storyId,
                        chapterId = chapter.chapterId,
                    )
                }.getOrNull()?.let { detail ->
                    val lat: Double = detail.place.latitude ?: return@let null
                    val lng: Double = detail.place.longitude ?: return@let null
                    RouteChapterMarker(
                        order = chapter.sequence,
                        title = detail.place.title.ifBlank { chapter.placeTitle },
                        latitude = lat,
                        longitude = lng,
                        placeId = placeId,
                    )
                }
            }
            .sortedBy { marker -> marker.order }
        if (markers.isEmpty()) return book
        val remoteRoutePoints: List<RoutePoint> = result.metadata?.routePoints.orEmpty()
        val routePoints: List<RoutePoint> = remoteRoutePoints.ifEmpty {
            routeHistoryStore.getRouteForStory(storyId)
        }
        val routePage = StoryBookPage(
            id = "${book.id}-route-map",
            title = "다녀온 길",
            body = "",
            photoLabel = "지도",
            pageLabel = "",
            imageResId = null,
            imageUrl = null,
            imageSide = ImageSide.Left,
            layout = StoryBookPageLayout.RouteMap,
            palette = listOf(Color(0xFFE94A7B), Color(0xFFFFB65C), Color(0xFFFFE08A)),
            routeChapterMarkers = markers,
            routeUserPoints = routePoints,
        )
        return book.copy(pages = book.pages + routePage)
    }

    private suspend fun renderPdfPages(result: EbookResult): List<Bitmap> {
        check(result.status == EbookStatus.Completed) {
            "완성된 eBook만 읽을 수 있어요."
        }
        check(result.fileUrl.isNotBlank()) {
            "eBook 파일 주소가 아직 준비되지 않았어요."
        }
        val pdfFile: File = ebookFileRepository.getLocalPdfFile(result)
        return withContext(Dispatchers.Default) {
            renderPdfFile(pdfFile)
        }
    }

    private fun renderPdfFile(pdfFile: File): List<Bitmap> {
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)
        try {
            return List(renderer.pageCount) { pageIndex ->
                val page = renderer.openPage(pageIndex)
                try {
                    page.renderToBitmap()
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            fileDescriptor.close()
        }
    }
}

private data class PendingFairyTaleCreation(
    val sourceBook: StoryBook,
    val sourcePages: List<StoryBookPage>,
    val targetPages: List<StoryBookPage>,
    val nextSelectionIndex: Int = 0,
    val selectedCandidates: Map<String, FairyTaleProtagonistCandidate> = emptyMap(),
)

private fun FairyTaleProtagonistSelectionPreview.toUiState(
    page: StoryBookPage,
    currentIndex: Int,
    totalCount: Int,
): ProtagonistSelectionUiState =
    ProtagonistSelectionUiState(
        pageId = page.id,
        title = page.title,
        progressLabel = "${currentIndex + 1} / $totalCount",
        previewBitmap = previewBitmap,
        candidates = candidates,
        selectedIndex = selectedIndex,
    )

@Composable
fun StoryBookViewerRoute(
    onBack: () -> Unit,
    onOpenFullMode: (Int) -> Unit,
    viewModel: StoryBookViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StoryBookExportEffects(
        uiState = uiState,
        onCaptureComplete = viewModel::saveCapturedExport,
        onCaptureFailed = viewModel::failCapturedExport,
        onExportConsumed = viewModel::consumeExportEvent,
    )
    StoryBookViewerScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenFullMode = onOpenFullMode,
        onPageChange = viewModel::updateCurrentPage,
        onDismissCoachmark = viewModel::dismissCoachmark,
        onCreateFairyTaleVersion = viewModel::handleFairyTaleAction,
        onPickProtagonist = viewModel::pickProtagonistAt,
        onConfirmProtagonist = viewModel::confirmProtagonistSelection,
        onSkipProtagonist = viewModel::skipProtagonistSelection,
        onCancelProtagonist = viewModel::cancelProtagonistSelection,
        onExport = viewModel::exportCurrentBook,
    )
}

@Composable
fun StoryBookFullModeRoute(
    initialPage: Int,
    onBack: () -> Unit,
    viewModel: StoryBookViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialPage) {
        viewModel.updateCurrentPage(initialPage)
    }
    StoryBookExportEffects(
        uiState = uiState,
        onCaptureComplete = viewModel::saveCapturedExport,
        onCaptureFailed = viewModel::failCapturedExport,
        onExportConsumed = viewModel::consumeExportEvent,
    )
    StoryBookFullModeScreen(
        uiState = uiState,
        onBack = onBack,
        onPageChange = viewModel::updateCurrentPage,
        onExport = viewModel::exportCurrentBook,
    )
}

@Composable
private fun StoryBookExportEffects(
    uiState: StoryBookViewerUiState,
    onCaptureComplete: (List<Bitmap>) -> Unit,
    onCaptureFailed: (Throwable) -> Unit,
    onExportConsumed: () -> Unit,
) {
    val context: Context = LocalContext.current
    val compositionContext: CompositionContext = rememberCompositionContext()
    LaunchedEffect(uiState.exportUri) {
        val uri: Uri = uiState.exportUri ?: return@LaunchedEffect
        val exportIntent = Intent(Intent.ACTION_SEND).apply {
            type = PdfMimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, uiState.exportFileName ?: "storybook.pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(exportIntent, "eBook 내보내기"))
        onExportConsumed()
    }
    LaunchedEffect(uiState.exportCaptureRequestId) {
        val captureBook: StoryBook = uiState.exportCaptureBook ?: return@LaunchedEffect
        if (uiState.exportCaptureRequestId == 0L) return@LaunchedEffect
        val fullModeBook: StoryBook = captureBook.toFullModeBook().book
        runCatching {
            captureLandscapeBookSpreads(
                context = context,
                compositionContext = compositionContext,
                book = fullModeBook,
                textSizing = fullModeBook.readerTextSizing(),
            )
        }.onSuccess(onCaptureComplete)
            .onFailure(onCaptureFailed)
    }
}

@Composable
private fun StoryBookViewerScreen(
    uiState: StoryBookViewerUiState,
    onBack: () -> Unit,
    onOpenFullMode: (Int) -> Unit,
    onPageChange: (Int) -> Unit,
    onDismissCoachmark: () -> Unit,
    onCreateFairyTaleVersion: () -> Unit,
    onPickProtagonist: (Float, Float) -> Unit,
    onConfirmProtagonist: () -> Unit,
    onSkipProtagonist: () -> Unit,
    onCancelProtagonist: () -> Unit,
    onExport: () -> Unit,
) {
    val book: StoryBook? = uiState.book

    Scaffold(
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(readerBackgroundBrush())
                .padding(innerPadding),
        ) {
            ReaderBackdrop()
            if (book == null) {
                StoryBookStatusContent(
                    isLoading = uiState.isLoading,
                    message = uiState.errorMessage ?: "결과물을 불러오는 중이에요.",
                    onBack = onBack,
                )
            } else {
                StoryBookReader(
                    book = book,
                    onBack = onBack,
                    isCreatingFairyTaleVersion = uiState.isCreatingFairyTaleVersion,
                    hasFairyTaleVersion = uiState.hasFairyTaleVersion,
                    isFairyTaleVersion = uiState.isFairyTaleVersion,
                    fairyTaleMessage = uiState.fairyTaleMessage,
                    isExporting = uiState.isExporting,
                    exportMessage = uiState.exportMessage,
                    currentPageIndex = uiState.currentPageIndex,
                    onCreateFairyTaleVersion = onCreateFairyTaleVersion,
                    onExport = onExport,
                    onOpenFullMode = onOpenFullMode,
                    onPageChange = onPageChange,
                )
                if (uiState.showCoachmark) {
                    EbookCoachmarkOverlay(onDismiss = onDismissCoachmark)
                }
            }
            uiState.protagonistSelection?.let { selection ->
                ProtagonistSelectionDialog(
                    selection = selection,
                    onPick = onPickProtagonist,
                    onConfirm = onConfirmProtagonist,
                    onSkip = onSkipProtagonist,
                    onCancel = onCancelProtagonist,
                )
            }
        }
    }
}

@Composable
private fun EbookCoachmarkOverlay(
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC35121F))
            .tossClickable(
                role = Role.Button,
                pressedScale = 1f,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .heightIn(max = maxHeight - 32.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.Lg, vertical = Spacing.Xl)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            ) {
                Text(
                    text = "이북 사용법",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.ExtraBold,
                )
                CoachmarkRow(
                    icon = Icons.Rounded.SwipeLeft,
                    title = "좌우로 스와이프해 페이지를 넘기세요",
                )
                CoachmarkRow(
                    icon = Icons.Rounded.TouchApp,
                    title = "하단 화살표 버튼으로도 이동할 수 있어요",
                )
                CoachmarkRow(
                    icon = Icons.Rounded.Close,
                    title = "왼쪽 위 뒤로가기로 갤러리로 돌아가요",
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tossClickable(
                            role = Role.Button,
                            pressedScale = CultureMotion.SubtlePressedScale,
                            onClick = onDismiss,
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "확인했어요",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoachmarkRow(
    icon: ImageVector,
    title: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProtagonistSelectionDialog(
    selection: ProtagonistSelectionUiState,
    onPick: (Float, Float) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight - 32.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "주인공 확인",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = selection.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = selection.progressLabel,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    ProtagonistSelectionImage(
                        selection = selection,
                        onPick = onPick,
                    )
                    Text(
                        text = if (selection.selectedIndex == null) {
                            "사진에서 주인공을 탭해 주세요."
                        } else {
                            "표시된 인물이 주인공으로 반영돼요."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    text = "취소",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Button(
                                onClick = onConfirm,
                                enabled = selection.selectedIndex != null,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    text = "확인",
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                text = "건너뛰기",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtagonistSelectionImage(
    selection: ProtagonistSelectionUiState,
    onPick: (Float, Float) -> Unit,
) {
    val bitmap: Bitmap = selection.previewBitmap
    val aspectRatio: Float = bitmap.width.toFloat() / bitmap.height.toFloat()
    val selectedColor: Color = MaterialTheme.colorScheme.primary
    val candidateColor = Color(0xFF2B1B23)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2B1B23)),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(selection.pageId, selection.candidates) {
                    detectTapGestures { offset ->
                        onPick(
                            (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                            (offset.y / size.height.toFloat()).coerceIn(0f, 1f),
                        )
                    }
                },
        ) {
            selection.candidates.forEachIndexed { index, candidate ->
                val isSelected: Boolean = index == selection.selectedIndex
                val color: Color = if (isSelected) {
                    selectedColor
                } else {
                    candidateColor
                }
                val left = candidate.normalizedLeft * size.width
                val top = candidate.normalizedTop * size.height
                val right = candidate.normalizedRight * size.width
                val bottom = candidate.normalizedBottom * size.height
                drawRect(
                    color = color.copy(alpha = if (isSelected) 0.18f else 0.04f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                )
                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = if (isSelected) 4.dp.toPx() else 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun StoryBookReader(
    book: StoryBook,
    onBack: () -> Unit,
    isCreatingFairyTaleVersion: Boolean,
    hasFairyTaleVersion: Boolean,
    isFairyTaleVersion: Boolean,
    fairyTaleMessage: String?,
    isExporting: Boolean,
    exportMessage: String?,
    currentPageIndex: Int,
    onCreateFairyTaleVersion: () -> Unit,
    onExport: () -> Unit,
    onOpenFullMode: (Int) -> Unit,
    onPageChange: (Int) -> Unit,
) {
    val pagerState: PagerState = rememberPagerState(
        initialPage = book.coercePageIndex(currentPageIndex),
        pageCount = { book.pages.size },
    )
    val scope = rememberCoroutineScope()
    val textSizing: ReaderTextSizing = book.readerTextSizing()
    LaunchedEffect(book.id, currentPageIndex) {
        val targetPage: Int = book.coercePageIndex(currentPageIndex)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        onPageChange(pagerState.currentPage)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompactLandscape: Boolean = maxWidth > maxHeight
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            ReaderTopBar(
                book = book,
                currentPage = pagerState.currentPage + 1,
                totalPage = book.pages.size,
                isCompact = isCompactLandscape,
                onBack = onBack,
            )
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val isWideLayout: Boolean = maxWidth >= 720.dp
                val horizontalPadding: Dp = when {
                    isCompactLandscape -> 28.dp
                    isWideLayout -> 64.dp
                    else -> 18.dp
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    pageSpacing = if (isCompactLandscape) 12.dp else if (isWideLayout) 24.dp else 16.dp,
                ) { pageIndex ->
                    val pageOffset: Float = (
                        (pagerState.currentPage - pageIndex) +
                            pagerState.currentPageOffsetFraction
                        ).absoluteValue
                    BookPagerItem(
                        page = book.pages[pageIndex],
                        pageOffset = pageOffset,
                        isWideLayout = isWideLayout,
                        textSizing = textSizing,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            ReaderActionButtons(
                isCreatingFairyTaleVersion = isCreatingFairyTaleVersion,
                hasFairyTaleVersion = hasFairyTaleVersion,
                isFairyTaleVersion = isFairyTaleVersion,
                fairyTaleMessage = fairyTaleMessage,
                isExporting = isExporting,
                exportMessage = exportMessage,
                isCompact = isCompactLandscape,
                onCreateFairyTaleVersion = onCreateFairyTaleVersion,
                onExport = onExport,
                onOpenFullMode = {
                    onPageChange(pagerState.currentPage)
                    onOpenFullMode(pagerState.currentPage)
                },
            )
            ReaderControls(
                pagerState = pagerState,
                pageCount = book.pages.size,
                isCompact = isCompactLandscape,
                onPrevious = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
            )
        }
    }
}

@Composable
private fun ReaderActionButtons(
    isCreatingFairyTaleVersion: Boolean,
    hasFairyTaleVersion: Boolean,
    isFairyTaleVersion: Boolean,
    fairyTaleMessage: String?,
    isExporting: Boolean,
    exportMessage: String?,
    isCompact: Boolean,
    onCreateFairyTaleVersion: () -> Unit,
    onExport: () -> Unit,
    onOpenFullMode: () -> Unit,
) {
    val actionMessage: String? = exportMessage ?: fairyTaleMessage
    val buttonHeight: Dp = if (isCompact) 42.dp else 48.dp
    val horizontalPadding: Dp = if (isCompact) 14.dp else 18.dp
    val verticalPadding: Dp = if (isCompact) 2.dp else 4.dp
    val rowSpacing: Dp = if (isCompact) 6.dp else 10.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 5.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            Button(
                onClick = onCreateFairyTaleVersion,
                enabled = !isCreatingFairyTaleVersion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.86f),
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                ),
            ) {
                if (isCreatingFairyTaleVersion) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = when {
                        isFairyTaleVersion -> "원본 보기"
                        hasFairyTaleVersion -> "동화버전 보기"
                        else -> "동화버전 만들기"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            OutlinedButton(
                onClick = onOpenFullMode,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "전체모드",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onExport,
                enabled = !isExporting,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.White.copy(alpha = 0.72f),
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                ),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (isExporting) "PDF 준비 중" else "PDF 내보내기",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actionMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.9f),
            ) {
                Text(
                    text = actionMessage,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StoryBookStatusContent(
    isLoading: Boolean,
    message: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "뒤로가기",
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StoryBookFullModeScreen(
    uiState: StoryBookViewerUiState,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onExport: () -> Unit,
) {
    val book: StoryBook? = uiState.book
    if (book == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            StoryBookStatusContent(
                isLoading = uiState.isLoading,
                message = uiState.errorMessage ?: "결과물을 불러오는 중이에요.",
                onBack = onBack,
            )
        }
        return
    }
    val fullModeBook: FullModeBook = remember(book) { book.toFullModeBook() }
    FullModeReader(
        book = fullModeBook.book,
        initialPage = fullModeBook.toFullModePageIndex(uiState.currentPageIndex),
        textSizing = fullModeBook.book.readerTextSizing(),
        onBack = onBack,
        isExporting = uiState.isExporting,
        onExport = onExport,
        onPageChange = { pageIndex ->
            onPageChange(fullModeBook.toSourcePageIndex(pageIndex))
        },
    )
}

@Composable
private fun ReaderTopBar(
    book: StoryBook,
    currentPage: Int,
    totalPage: Int,
    isCompact: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isCompact) 14.dp else 16.dp,
                vertical = if (isCompact) 6.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "뒤로가기",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isCompact) {
                Text(
                    text = book.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 4.dp,
        ) {
            Text(
                text = "$currentPage / $totalPage",
                modifier = Modifier.padding(
                    horizontal = if (isCompact) 10.dp else 12.dp,
                    vertical = if (isCompact) 6.dp else 8.dp,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun FullModeReader(
    book: StoryBook,
    initialPage: Int,
    textSizing: ReaderTextSizing,
    onBack: () -> Unit,
    isExporting: Boolean,
    onExport: () -> Unit,
    onPageChange: (Int) -> Unit,
) {
    val activity: Activity? = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val previousOrientation: Int? = activity?.requestedOrientation
        val window: Window? = activity?.window
        val controller: WindowInsetsControllerCompat? = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        val previousBehavior: Int? = controller?.systemBarsBehavior
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) {
                controller.systemBarsBehavior = previousBehavior
            }
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
            if (previousOrientation != null) {
                activity?.requestedOrientation = previousOrientation
            }
        }
    }

    val spreadCount: Int = ((book.pages.size + 1) / 2).coerceAtLeast(1)
    val initialSpreadIndex: Int = (book.coercePageIndex(initialPage) / 2).coerceIn(0, spreadCount - 1)
    val spreadState: PagerState = rememberPagerState(
        initialPage = initialSpreadIndex,
        pageCount = { spreadCount },
    )
    val scope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var hasReportedInitialSpread by remember(book.id, initialSpreadIndex) { mutableStateOf(false) }
    LaunchedEffect(showControls, spreadState.currentPage) {
        if (showControls) {
            delay(FullModeControlsAutoHideMillis)
            showControls = false
        }
    }
    LaunchedEffect(spreadState.currentPage, book.pages.size) {
        if (!hasReportedInitialSpread && spreadState.currentPage == initialSpreadIndex) {
            hasReportedInitialSpread = true
            return@LaunchedEffect
        }
        hasReportedInitialSpread = true
        onPageChange((spreadState.currentPage * 2).coerceAtMost(book.pages.lastIndex))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                )
            },
    ) {
            HorizontalPager(
                state = spreadState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 0.dp),
                pageSpacing = 0.dp,
            ) { spreadIndex ->
                FullModeSpread(
                    leftPage = book.pages.getOrNull(spreadIndex * 2),
                    rightPage = book.pages.getOrNull(spreadIndex * 2 + 1),
                    textSizing = textSizing,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically { offset -> -offset } + fadeIn(),
                exit = slideOutVertically { offset -> -offset } + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = book.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                    ) {
                        Text(
                            text = "${spreadState.currentPage + 1} / $spreadCount",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    IconButton(
                        onClick = onExport,
                        enabled = !isExporting,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f),
                            disabledContentColor = Color.White.copy(alpha = 0.35f),
                        ),
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "PDF 내보내기",
                            )
                        }
                    }
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "닫기",
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { offset -> offset } + fadeIn(),
                exit = slideOutVertically { offset -> offset } + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f),
                                ),
                            ),
                        ),
                ) {
                    FullModeControls(
                        currentPage = spreadState.currentPage,
                        pageCount = spreadCount,
                        onPrevious = {
                            scope.launch {
                                spreadState.animateScrollToPage(spreadState.currentPage - 1)
                            }
                        },
                        onNext = {
                            scope.launch {
                                spreadState.animateScrollToPage(spreadState.currentPage + 1)
                            }
                        },
                    )
                }
            }
        }
    }

@Composable
private fun FullModeSpread(
    leftPage: StoryBookPage?,
    rightPage: StoryBookPage?,
    textSizing: ReaderTextSizing,
    preferStaticRouteMap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        FullModePageSlot(
            page = leftPage,
            textSizing = textSizing,
            preferStaticRouteMap = preferStaticRouteMap,
            cornerShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.86f)
                .background(Color(0x5535121F)),
        )
        FullModePageSlot(
            page = rightPage,
            textSizing = textSizing,
            preferStaticRouteMap = preferStaticRouteMap,
            cornerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FullModePageSlot(
    page: StoryBookPage?,
    textSizing: ReaderTextSizing,
    preferStaticRouteMap: Boolean = false,
    cornerShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        if (page == null) {
            Surface(
                modifier = Modifier
                    .fillMaxSize(),
                shape = cornerShape,
                color = PaperColor,
                shadowElevation = 18.dp,
            ) {
                PaperTexture()
            }
        } else {
            BookFrame(
                page = page,
                isWideLayout = true,
                textSizing = textSizing,
                preferStaticRouteMap = preferStaticRouteMap,
                shape = cornerShape,
                modifier = Modifier
                    .fillMaxSize(),
            )
        }
    }
}

private suspend fun captureLandscapeBookSpreads(
    context: Context,
    compositionContext: CompositionContext,
    book: StoryBook,
    textSizing: ReaderTextSizing,
): List<Bitmap> = withContext(Dispatchers.Main.immediate) {
    val activity: Activity = context.findActivity()
        ?: error("PDF 캡쳐를 시작할 화면을 찾지 못했어요.")
    val root: ViewGroup = activity.findViewById(android.R.id.content)
    val spreadCount: Int = book.spreadCount()
    val capturedBitmaps = mutableListOf<Bitmap>()
    try {
        repeat(spreadCount) { spreadIndex ->
            val captureView: ComposeView = createSpreadCaptureView(
                activity = activity,
                compositionContext = compositionContext,
                book = book,
                textSizing = textSizing,
                spreadIndex = spreadIndex,
            )
            root.addView(captureView)
            try {
                captureView.measure(
                    View.MeasureSpec.makeMeasureSpec(PdfCaptureWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(PdfCaptureHeight, View.MeasureSpec.EXACTLY),
                )
                captureView.layout(0, 0, PdfCaptureWidth, PdfCaptureHeight)
                awaitPdfCaptureContent()
                capturedBitmaps += captureView.drawToBitmap(Bitmap.Config.ARGB_8888)
            } finally {
                captureView.disposeComposition()
                root.removeView(captureView)
            }
        }
        capturedBitmaps
    } catch (throwable: Throwable) {
        capturedBitmaps.forEach { bitmap -> bitmap.recycle() }
        throw throwable
    }
}

private fun createSpreadCaptureView(
    activity: Activity,
    compositionContext: CompositionContext,
    book: StoryBook,
    textSizing: ReaderTextSizing,
    spreadIndex: Int,
): ComposeView =
    ComposeView(activity).apply {
        setParentCompositionContext(compositionContext)
        layoutParams = FrameLayout.LayoutParams(PdfCaptureWidth, PdfCaptureHeight)
        translationX = -PdfCaptureWidth * 2f
        translationY = -PdfCaptureHeight * 2f
        setContent {
            CultureTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    FullModeSpread(
                        leftPage = book.pages.getOrNull(spreadIndex * 2),
                        rightPage = book.pages.getOrNull(spreadIndex * 2 + 1),
                        textSizing = textSizing,
                        preferStaticRouteMap = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

private suspend fun awaitPdfCaptureContent() {
    withFrameNanos { }
    delay(PdfCaptureSettleMillis)
    withFrameNanos { }
}

@Composable
private fun FullModeControls(
    currentPage: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = currentPage > 0,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.35f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "이전",
            )
        }
        IconButton(
            onClick = onNext,
            enabled = currentPage < pageCount - 1,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.35f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "다음",
            )
        }
    }
}

@Composable
private fun BookPagerItem(
    page: StoryBookPage,
    pageOffset: Float,
    isWideLayout: Boolean,
    textSizing: ReaderTextSizing,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val clampedOffset: Float = pageOffset.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val pageRatio: Float = page.bitmap
            ?.let { bitmap -> bitmap.width.toFloat() / bitmap.height.toFloat() }
            ?.takeIf { ratio -> ratio > 0f }
            ?: if (isWideLayout) 0.72f else 0.68f
        val pageWidth: Dp = maxWidth.coerceAtMost(maxHeight * pageRatio)
        BookFrame(
            page = page,
            isWideLayout = isWideLayout,
            textSizing = textSizing,
            modifier = Modifier
                .width(pageWidth)
                .aspectRatio(pageRatio)
                .graphicsLayer {
                    val turnDirection = if (pageOffset > 0f) -1f else 1f
                    rotationY = turnDirection * clampedOffset * 10f
                    cameraDistance = 18f * density
                    alpha = 1f - clampedOffset * 0.16f
                    scaleX = 1f - clampedOffset * 0.04f
                    scaleY = 1f - clampedOffset * 0.04f
                },
        )
    }
}

@Composable
private fun BookFrame(
    page: StoryBookPage,
    isWideLayout: Boolean,
    textSizing: ReaderTextSizing,
    preferStaticRouteMap: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(if (isWideLayout) 28.dp else 24.dp),
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = PaperColor,
        shadowElevation = 18.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val bitmap: Bitmap? = page.bitmap
            if (bitmap != null) {
                PdfPageSpread(bitmap = bitmap)
            } else {
                PaperTexture()
                when (page.layout) {
                    StoryBookPageLayout.Cover -> CoverPage(
                        page = page,
                        textSizing = textSizing,
                    )
                    StoryBookPageLayout.CoverImage -> CoverImagePage(page = page)
                    StoryBookPageLayout.Photo -> PhotoPageFace(
                        page = page,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    )
                    StoryBookPageLayout.FullImage -> FullImagePage(page = page)
                    StoryBookPageLayout.ImageTextStack -> ImageTextStackPage(
                        page = page,
                        textSizing = textSizing,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    )
                    StoryBookPageLayout.RouteMap -> {
                        if (preferStaticRouteMap) {
                            StaticRouteMapPage(
                                page = page,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                            )
                        } else {
                            RouteMapPage(
                                page = page,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                            )
                        }
                    }
                    StoryBookPageLayout.TextFocus,
                    StoryBookPageLayout.ImageText -> TextPageFace(
                        page = page,
                        textSizing = textSizing,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPageSpread(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "eBook 페이지",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun CoverPage(
    page: StoryBookPage,
    textSizing: ReaderTextSizing,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(page.palette)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            repeat(12) { index ->
                val x: Float = ((index * 53 + 15) % 100) / 100f * size.width
                val y: Float = ((index * 37 + 22) % 100) / 100f * size.height
                val length: Float = 8f + (index % 3) * 4f
                drawLine(
                    color = Color.White.copy(alpha = 0.58f),
                    start = Offset(x - length, y),
                    end = Offset(x + length, y),
                    strokeWidth = 2.4f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.58f),
                    start = Offset(x, y - length),
                    end = Offset(x, y + length),
                    strokeWidth = 2.4f,
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                ),
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = page.body,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = textSizing.bodyFontSize,
                    lineHeight = textSizing.bodyLineHeight,
                ),
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CoverImagePage(
    page: StoryBookPage,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(page.palette))
            .padding(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.74f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.atoria_mascot),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight(0.82f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun FullImagePage(
    page: StoryBookPage,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(page.palette)),
    ) {
        IllustrationImage(
            page = page,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xAA35121F))
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = page.body,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    lineHeight = 29.sp,
                ),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PhotoPageFace(
    page: StoryBookPage,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(page.palette))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.68f),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        IllustrationImage(
            page = page,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            shape = CircleShape,
            color = Color.White,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = page.photoLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TextPageFace(
    page: StoryBookPage,
    textSizing: ReaderTextSizing,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = textSizing.bodyFontSize,
                lineHeight = textSizing.bodyLineHeight,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = page.pageLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ImageTextStackPage(
    page: StoryBookPage,
    textSizing: ReaderTextSizing,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.48f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Brush.linearGradient(page.palette)),
            contentAlignment = Alignment.Center,
        ) {
            IllustrationImage(
                page = page,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                shape = CircleShape,
                color = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = page.photoLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 23.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = page.body,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = textSizing.bodyFontSize,
                    lineHeight = textSizing.bodyLineHeight,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = page.pageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun IllustrationImage(
    page: StoryBookPage,
    modifier: Modifier = Modifier,
) {
    val illustrationBitmap: Bitmap? = page.illustrationBitmap
    val imageUrl: String? = page.imageUrl?.takeIf(String::isNotBlank)
    val imageResId: Int? = page.imageResId
    if (illustrationBitmap != null) {
        Image(
            bitmap = illustrationBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else if (imageUrl != null) {
        CultureAsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            allowHardware = false,
        )
    } else if (imageResId != null) {
        Image(
            painter = painterResource(id = imageResId),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(Brush.linearGradient(page.palette)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(18.dp)
                        .size(42.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ReaderControls(
    pagerState: PagerState,
    pageCount: Int,
    isCompact: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isCompact) 14.dp else 18.dp,
                vertical = if (isCompact) 5.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp),
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = pagerState.currentPage > 0,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.onBackground,
                disabledContainerColor = Color.White,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "이전 장",
            )
        }
        PageDots(
            currentPage = pagerState.currentPage,
            pageCount = pageCount,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onNext,
            enabled = pagerState.currentPage < pageCount - 1,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                disabledContainerColor = Color.White,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "다음 장",
            )
        }
    }
}

@Composable
private fun PageDots(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount > MaxVisiblePageDots) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
            ) {
                Text(
                    text = "${currentPage + 1} / $pageCount",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected: Boolean = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(if (isSelected) 22.dp else 8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White),
            )
        }
    }
}

@Composable
private fun PaperTexture() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = PaperColor)
        repeat(18) { index ->
            val y = (index + 1) * size.height / 19f
            drawLine(
                color = Color(0xFFFFDCE4).copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.1f,
            )
        }
    }
}

@Composable
private fun ReaderBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(16) { index ->
            val x: Float = ((index * 61 + 19) % 100) / 100f * size.width
            val y: Float = ((index * 43 + 11) % 100) / 100f * size.height
            val length: Float = 5f + (index % 3) * 3f
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(x - length, y),
                end = Offset(x + length, y),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(x, y - length),
                end = Offset(x, y + length),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

data class StoryBook(
    val id: String,
    val title: String,
    val subtitle: String,
    val pages: List<StoryBookPage>,
)

data class StoryBookPage(
    val id: String,
    val title: String,
    val body: String,
    val photoLabel: String,
    val pageLabel: String,
    val imageResId: Int?,
    val imageUrl: String? = null,
    val imageSide: ImageSide,
    val layout: StoryBookPageLayout,
    val palette: List<Color>,
    val bitmap: Bitmap? = null,
    val illustrationBitmap: Bitmap? = null,
    val routeChapterMarkers: List<RouteChapterMarker> = emptyList(),
    val routeUserPoints: List<RoutePoint> = emptyList(),
)

data class RouteChapterMarker(
    val order: Int,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: Long,
)

private fun StoryBook?.coercePageIndex(pageIndex: Int): Int {
    val pageCount: Int = this?.pages?.size ?: 0
    if (pageCount <= 0) return pageIndex.coerceAtLeast(0)
    return pageIndex.coerceIn(0, pageCount - 1)
}

private fun StoryBook.spreadCount(): Int =
    ((pages.size + 1) / 2).coerceAtLeast(1)

private data class FullModeBook(
    val book: StoryBook,
    val sourcePageIndices: List<Int>,
) {
    fun toFullModePageIndex(sourcePageIndex: Int): Int {
        val exactPageIndex: Int = sourcePageIndices.indexOf(sourcePageIndex)
        if (exactPageIndex >= 0) return exactPageIndex
        return book.coercePageIndex(sourcePageIndex)
    }

    fun toSourcePageIndex(fullModePageIndex: Int): Int {
        val coercedPageIndex: Int = book.coercePageIndex(fullModePageIndex)
        return sourcePageIndices.getOrElse(coercedPageIndex) { coercedPageIndex }
    }
}

private fun StoryBook.toFullModeBook(): FullModeBook {
    val fullModePages = mutableListOf<StoryBookPage>()
    val sourcePageIndices = mutableListOf<Int>()
    pages.forEachIndexed { sourcePageIndex, page ->
        val pagesForFullMode: List<StoryBookPage> = page.toFullModePages()
        fullModePages += pagesForFullMode
        repeat(pagesForFullMode.size) {
            sourcePageIndices += sourcePageIndex
        }
    }
    return FullModeBook(
        book = copy(pages = fullModePages),
        sourcePageIndices = sourcePageIndices,
    )
}

private fun StoryBookPage.toFullModePages(): List<StoryBookPage> {
    return when (layout) {
        StoryBookPageLayout.ImageTextStack -> listOf(
            copy(
                id = "$id-image",
                body = "",
                layout = StoryBookPageLayout.Photo,
            ),
        ) + copy(
            id = "$id-text",
            imageResId = null,
            imageUrl = null,
            illustrationBitmap = null,
            layout = StoryBookPageLayout.TextFocus,
        ).splitFullModeTextPages()

        StoryBookPageLayout.TextFocus,
        StoryBookPageLayout.ImageText,
        -> splitFullModeTextPages()

        else -> listOf(this)
    }
}

private fun StoryBookPage.splitFullModeTextPages(): List<StoryBookPage> {
    val bodyChunks: List<String> = body.toStoryBookTextChunks(
        firstLimit = pageBodyLimitForTitle(
            baseLimit = FullModeTextBodyLimit,
            title = title,
        ),
        nextLimit = pageBodyLimitForTitle(
            baseLimit = FullModeTextBodyContinuationLimit,
            title = title,
        ),
    )
    if (bodyChunks.size == 1) return listOf(this)
    return bodyChunks.mapIndexed { chunkIndex, chunk ->
        copy(
            id = "$id-full-text-${chunkIndex + 1}",
            body = chunk,
            pageLabel = pageLabel.toSplitPageLabel(chunkIndex, bodyChunks.size),
        )
    }
}

private data class ReaderTextSizing(
    val bodyFontSize: TextUnit,
    val bodyLineHeight: TextUnit,
)

private fun StoryBook.readerTextSizing(): ReaderTextSizing {
    val maxTextWeight: Int = pages
        .filter { page ->
            page.layout == StoryBookPageLayout.Cover ||
                page.layout == StoryBookPageLayout.TextFocus ||
                page.layout == StoryBookPageLayout.ImageText ||
                page.layout == StoryBookPageLayout.ImageTextStack
        }
        .maxOfOrNull { page ->
            page.body.length + page.body.count { character -> character == '\n' } * 28
        }
        ?: 0
    val bodyFontSize: TextUnit = when {
        maxTextWeight >= 900 -> 11.sp
        maxTextWeight >= 700 -> 12.sp
        maxTextWeight >= 520 -> 13.sp
        maxTextWeight >= 360 -> 14.sp
        else -> 15.sp
    }
    return ReaderTextSizing(
        bodyFontSize = bodyFontSize,
        bodyLineHeight = bodyFontSize * 1.55f,
    )
}

enum class ImageSide {
    Left,
    Right,
}

enum class StoryBookPageLayout {
    Cover,
    CoverImage,
    ImageText,
    FullImage,
    Photo,
    TextFocus,
    ImageTextStack,
    RouteMap,
}

private const val LatestBookId: String = "latest"
private const val MaxVisiblePageDots: Int = 9
private const val FullModeTextBodyLimit: Int = 220
private const val FullModeTextBodyContinuationLimit: Int = 240
private const val ImageTextStackFirstBodyLimit: Int = 190
private const val TextFocusFirstBodyLimit: Int = 430
private const val TextFocusContinuationBodyLimit: Int = 470
private const val TextChunkMinimumBreakRatio: Float = 0.55f
private const val MinBodyLimitRatio: Float = 0.72f
private const val MaxTitleLengthPenalty: Int = 36

private fun PdfRenderer.Page.renderToBitmap(): Bitmap {
    val scale: Float = PdfRenderMaxWidth.toFloat() / width.toFloat()
    val targetWidth: Int = PdfRenderMaxWidth
    val targetHeight: Int = (height * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.WHITE)
    render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return bitmap
}

private fun EbookResult.toStoryBook(
    pages: List<Bitmap>,
): StoryBook {
    val displayTitle: String = title.ifBlank { "나의 문화 여행 이야기" }
    return StoryBook(
        id = fileId.ifBlank { LatestBookId },
        title = displayTitle,
        subtitle = "${pages.size}쪽 eBook",
        pages = pages.mapIndexed { index, bitmap ->
            StoryBookPage(
                id = "${fileId.ifBlank { LatestBookId }}-$index",
                title = displayTitle,
                body = "",
                photoLabel = "eBook",
                pageLabel = "${index + 1}",
                imageResId = null,
                imageSide = ImageSide.Left,
                layout = StoryBookPageLayout.TextFocus,
                palette = listOf(Color(0xFFE94A7B), Color(0xFFFFB65C), Color(0xFFFFE08A)),
                bitmap = bitmap,
            )
        },
    )
}

private fun EbookResult.toStoryBookFromContent(): StoryBook {
    check(status == EbookStatus.Completed) {
        "Completed eBook content is required."
    }
    val contentPages: List<EbookContentPage> = metadata?.content?.pages.orEmpty()
    check(contentPages.isNotEmpty()) {
        "eBook pages are empty."
    }
    val displayTitle: String = metadata?.content?.meta?.title.takeUnlessBlank()
        ?: metadata?.content?.cover?.title.takeUnlessBlank()
        ?: title.ifBlank { "eBook" }
    val pages: List<StoryBookPage> = contentPages.flatMapIndexed { index, page ->
        page.toStoryBookPages(
            bookId = fileId.ifBlank { LatestBookId },
            fallbackTitle = displayTitle,
            index = index,
        )
    }
    return StoryBook(
        id = fileId.ifBlank { LatestBookId },
        title = displayTitle,
        subtitle = metadata?.content?.meta?.subtitle.takeUnlessBlank()
            ?: "${pages.size} pages",
        pages = pages,
    )
}

private fun EbookContentPage.toStoryBookPages(
    bookId: String,
    fallbackTitle: String,
    index: Int,
): List<StoryBookPage> {
    val remoteImageUrl: String? = imageUrl.takeUnlessBlank()
    val pageTitle: String = title.takeUnlessBlank()
        ?: subtitle.takeUnlessBlank()
        ?: fallbackTitle
    val pageBody: String = listOfNotNull(
        text.takeUnlessBlank(),
        quote.takeUnlessBlank()?.let { quoteText -> "\"$quoteText\"" },
        if (text.isNullOrBlank()) caption.takeUnlessBlank() else null,
    ).joinToString(separator = "\n\n")
        .ifBlank { subtitle.orEmpty() }
    val originalPageNumber: Int = pageNumber ?: index + 1
    val normalizedType: String = type.orEmpty().uppercase()
    val textChunks: List<String> = pageBody.toStoryBookTextChunks(
        firstLimit = pageBodyLimitForTitle(
            baseLimit = if (remoteImageUrl == null) {
                TextFocusFirstBodyLimit
            } else {
                ImageTextStackFirstBodyLimit
            },
            title = pageTitle,
        ),
        nextLimit = pageBodyLimitForTitle(
            baseLimit = TextFocusContinuationBodyLimit,
            title = pageTitle,
        ),
    )
    if (normalizedType == "COVER" || normalizedType == "BACK_COVER") {
        return listOf(
            StoryBookPage(
                id = "$bookId-$originalPageNumber-cover-text",
                title = pageTitle,
                body = pageBody,
                photoLabel = caption.takeUnlessBlank() ?: type.takeUnlessBlank() ?: "eBook",
                pageLabel = "",
                imageResId = null,
                imageUrl = null,
                imageSide = ImageSide.Left,
                layout = StoryBookPageLayout.Cover,
                palette = ebookPagePalette(index),
            ),
            StoryBookPage(
                id = "$bookId-$originalPageNumber-cover-image",
                title = pageTitle,
                body = pageBody,
                photoLabel = caption.takeUnlessBlank() ?: type.takeUnlessBlank() ?: "eBook",
                pageLabel = "",
                imageResId = if (remoteImageUrl == null) R.drawable.atoria_mascot else null,
                imageUrl = remoteImageUrl,
                imageSide = ImageSide.Right,
                layout = StoryBookPageLayout.CoverImage,
                palette = ebookPagePalette(index),
            ),
        )
    }
    if (remoteImageUrl == null) {
        return textChunks.mapIndexed { chunkIndex, body ->
            StoryBookPage(
                id = "$bookId-$originalPageNumber-text-${chunkIndex + 1}",
                title = pageTitle,
                body = body,
                photoLabel = caption.takeUnlessBlank() ?: type.takeUnlessBlank() ?: "eBook",
                pageLabel = originalPageNumber.toSplitPageLabel(chunkIndex, textChunks.size),
                imageResId = null,
                imageUrl = null,
                imageSide = ImageSide.Left,
                layout = StoryBookPageLayout.TextFocus,
                palette = ebookPagePalette(index),
            )
        }
    }
    return textChunks.mapIndexed { chunkIndex, body ->
        val isFirstChunk: Boolean = chunkIndex == 0
        StoryBookPage(
            id = "$bookId-$originalPageNumber-${if (isFirstChunk) "stack" else "text"}-${chunkIndex + 1}",
            title = pageTitle,
            body = body,
            photoLabel = caption.takeUnlessBlank() ?: type.takeUnlessBlank() ?: "eBook",
            pageLabel = originalPageNumber.toSplitPageLabel(chunkIndex, textChunks.size),
            imageResId = null,
            imageUrl = if (isFirstChunk) remoteImageUrl else null,
            imageSide = ImageSide.Left,
            layout = if (isFirstChunk) StoryBookPageLayout.ImageTextStack else StoryBookPageLayout.TextFocus,
            palette = ebookPagePalette(index),
        )
    }
}

private fun String.toStoryBookTextChunks(
    firstLimit: Int,
    nextLimit: Int,
): List<String> {
    val normalizedText: String = trim()
    if (normalizedText.isBlank()) return listOf("")
    val chunks: MutableList<String> = mutableListOf()
    var remainingText: String = normalizedText
    var currentLimit: Int = firstLimit
    while (remainingText.isNotBlank()) {
        val breakIndex: Int = remainingText.findTextChunkBreakIndex(currentLimit)
        chunks += remainingText.substring(0, breakIndex).trim()
        remainingText = remainingText.substring(breakIndex).trimStart()
        currentLimit = nextLimit
    }
    return chunks
}

private fun String.findTextChunkBreakIndex(limit: Int): Int {
    if (length <= limit) return length
    val maxIndex: Int = limit.coerceIn(1, length - 1)
    val minIndex: Int = (limit * TextChunkMinimumBreakRatio).roundToInt().coerceIn(1, maxIndex)
    for (index in maxIndex downTo minIndex) {
        if (this[index] == '\n') return index + 1
        if (this[index].isStorySentenceBoundary() && getOrNull(index + 1)?.isWhitespace() != false) {
            return index + 1
        }
    }
    val spaceIndex: Int? = lastIndexOf(' ', startIndex = maxIndex).takeIf { index -> index >= minIndex }
    return spaceIndex?.coerceAtLeast(1) ?: maxIndex
}

private fun Char.isStorySentenceBoundary(): Boolean =
    this == '.' || this == '?' || this == '!' || this == '。' || this == '"' || this == '”' || this == '’'

private fun pageBodyLimitForTitle(
    baseLimit: Int,
    title: String,
): Int {
    val titlePenalty: Int = title.length.coerceAtMost(MaxTitleLengthPenalty)
    val minimumLimit: Int = (baseLimit * MinBodyLimitRatio).roundToInt()
    return (baseLimit - titlePenalty).coerceAtLeast(minimumLimit)
}

private fun Int.toSplitPageLabel(
    chunkIndex: Int,
    chunkCount: Int,
): String =
    if (chunkCount == 1) {
        "$this"
    } else {
        "$this-${chunkIndex + 1}"
    }

private fun String.toSplitPageLabel(
    chunkIndex: Int,
    chunkCount: Int,
): String =
    if (chunkCount == 1 || isBlank()) {
        this
    } else {
        "$this-${chunkIndex + 1}"
    }

private fun String?.takeUnlessBlank(): String? =
    this?.takeIf(String::isNotBlank)

private fun StoryBookPage.canCreateFairyTaleImage(): Boolean =
    imageUrl.takeUnlessBlank() != null && layout != StoryBookPageLayout.RouteMap

private fun StoryBookPage.toFairyTalePage(bitmap: Bitmap): StoryBookPage =
    copy(
        id = "${id}-fairy-tale",
        imageUrl = null,
        pageLabel = pageLabel.takeUnlessBlank()
            ?.let { label -> "$label 동화" }
            ?: "동화",
        photoLabel = "동화 일러스트",
        illustrationBitmap = bitmap,
    )

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun ebookPagePalette(index: Int): List<Color> =
    EbookPagePalettes[index % EbookPagePalettes.size]

private val EbookPagePalettes: List<List<Color>> = listOf(
    listOf(Color(0xFFE94A7B), Color(0xFFFFB65C), Color(0xFFFFE08A)),
    listOf(Color(0xFF71B7FF), Color(0xFFFFA4BD), Color(0xFFFFD96A)),
    listOf(Color(0xFF81D8AC), Color(0xFFFFC36B), Color(0xFFFFE6A8)),
)

private object MockStoryBookLibrary {
    val latestBook: StoryBook = createBook(
        id = "mock-result-latest",
        title = "경주에서 만난 빛나는 하루",
        subtitle = "사진으로 엮은 우리 가족 동화책",
    )

    private val books: List<StoryBook> = listOf(
        latestBook,
        createBook(
            id = "mock-completed-102",
            title = "북촌 골목의 작은 문",
            subtitle = "느린 산책이 들려준 이야기",
        ),
        createBook(
            id = "mock-completed-103",
            title = "한강 바람을 따라간 오후",
            subtitle = "반짝이는 물결과 웃음 기록",
        ),
    )

    fun findBook(bookId: String): StoryBook =
        books.firstOrNull { book -> book.id == bookId } ?: latestBook

    private fun createBook(
        id: String,
        title: String,
        subtitle: String,
    ): StoryBook {
        val pages: List<StoryBookPage> = listOf(
            StoryBookPage(
                id = "$id-cover",
                title = title,
                body = subtitle,
                photoLabel = "표지",
                pageLabel = "표지",
                imageResId = null,
                imageSide = ImageSide.Left,
                layout = StoryBookPageLayout.Cover,
                palette = listOf(Color(0xFFE94A7B), Color(0xFFFFB65C), Color(0xFFFFE08A)),
            ),
            StoryBookPage(
                id = "$id-scene-1",
                title = "첫 번째 반짝임",
                body = "햇살이 오래된 돌담 위에 내려앉자, 아이의 발걸음도 이야기처럼 가벼워졌어요.",
                photoLabel = "오늘의 사진",
                pageLabel = "2-3",
                imageResId = R.drawable.login_hero_scene,
                imageSide = ImageSide.Left,
                layout = StoryBookPageLayout.ImageText,
                palette = listOf(Color(0xFF71B7FF), Color(0xFFFFA4BD), Color(0xFFFFD96A)),
            ),
            StoryBookPage(
                id = "$id-scene-2",
                title = "바람이 넘긴 지도",
                body = "지도 한쪽이 팔랑이자, 숨어 있던 길 하나가 우리를 다음 장면으로 데려갔어요.",
                photoLabel = "장소 기록",
                pageLabel = "4-5",
                imageResId = null,
                imageSide = ImageSide.Right,
                layout = StoryBookPageLayout.ImageText,
                palette = listOf(Color(0xFF8FD6B4), Color(0xFF7DB7FF), Color(0xFFFFCF72)),
            ),
            StoryBookPage(
                id = "$id-scene-3",
                title = "가장 크게 웃은 순간",
                body = "찰칵. 웃음소리가 사진 속에 폭신하게 접혀 들어갔어요.",
                photoLabel = "클라이맥스",
                pageLabel = "6-7",
                imageResId = R.drawable.login_hero_scene,
                imageSide = ImageSide.Left,
                layout = StoryBookPageLayout.FullImage,
                palette = listOf(Color(0xFFFF8DB3), Color(0xFF83D2FF), Color(0xFFFFD66B)),
            ),
            StoryBookPage(
                id = "$id-scene-4",
                title = "작은 약속",
                body = "우리는 다음에도 새로운 길을 만나면, 먼저 웃고 그다음 사진을 찍기로 했어요.",
                photoLabel = "마지막 장면",
                pageLabel = "8-9",
                imageResId = null,
                imageSide = ImageSide.Right,
                layout = StoryBookPageLayout.TextFocus,
                palette = listOf(Color(0xFFFFCF72), Color(0xFFFF9AB7), Color(0xFF91D8B6)),
            ),
        )
        return StoryBook(
            id = id,
            title = title,
            subtitle = subtitle,
            pages = pages,
        )
    }
}

@Composable
private fun readerBackgroundBrush(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.background,
        ),
    )
private val PaperColor: Color = Color(0xFFFFFBF3)
private const val PdfRenderMaxWidth: Int = 1200
private const val PdfCaptureWidth: Int = 1600
private const val PdfCaptureHeight: Int = 900
private const val PdfCaptureImageMaxDimension: Int = 1600
private const val PdfCaptureSettleMillis: Long = 280L
private const val PdfMimeType: String = "application/pdf"
private const val FullModeControlsAutoHideMillis: Long = 3_000L
