package com.ssafy.culture.ui.screen.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.ml.HeritageClassifier
import com.ssafy.culture.data.ml.HeritageMissionMatcher
import com.ssafy.culture.data.ml.HeritageMissionValidation
import com.ssafy.culture.data.ml.HeritagePrediction
import com.ssafy.culture.data.media.MediaCaptureResult
import com.ssafy.culture.data.media.MediaUploadRepository
import com.ssafy.culture.data.preferences.AppPreferenceStore
import com.ssafy.culture.data.route.RouteTracker
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.ui.component.CultureAsyncImage
import com.ssafy.culture.ui.component.ImmersiveNavigationBarEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class QuestCameraUiState(
    val mediaResult: MediaCaptureResult? = null,
    val heritagePrediction: HeritagePrediction? = null,
    val heritageValidation: HeritageMissionValidation = HeritageMissionValidation.Pending,
    val expectedPlaceTitle: String = "",
    val expectedPlaceLatitude: Double? = null,
    val expectedPlaceLongitude: Double? = null,
    val locationStatusMessage: String? = null,
    val locationIssue: MissionLocationIssue? = null,
    val isClassifyingHeritage: Boolean = false,
    val isSubmittingMission: Boolean = false,
    val hasNavigatedAfterSubmit: Boolean = false,
    val errorMessage: String? = null,
)

data class MissionLocationIssue(
    val message: String,
    val canRequestPermission: Boolean,
    val canSaveUnverified: Boolean,
)

private sealed interface MissionLocationVerification {
    val message: String
    val apiValue: String

    data object CurrentGps : MissionLocationVerification {
        override val message: String = "장소 인증이 완료됐어요."
        override val apiValue: String = "CURRENT_GPS"
    }

    data object PhotoExifPlace : MissionLocationVerification {
        override val message: String = "사진 위치 정보로 장소 인증했어요."
        override val apiValue: String = "PHOTO_EXIF_PLACE"
    }

    data object PhotoExifArea : MissionLocationVerification {
        override val message: String = "사진 위치 정보로 여행 지역을 확인했어요."
        override val apiValue: String = "PHOTO_EXIF_AREA"
    }

    data object UnknownPlace : MissionLocationVerification {
        override val message: String = "장소 좌표가 없어 위치 인증 없이 제출했어요."
        override val apiValue: String = "UNKNOWN_PLACE"
    }

    data class Blocked(
        override val message: String,
        val canRequestPermission: Boolean,
        val canSaveUnverified: Boolean = !canRequestPermission,
        val shouldRetake: Boolean = false,
    ) : MissionLocationVerification {
        override val apiValue: String = "UNVERIFIED"
    }
}

private data class MissionGeoPoint(
    val latitude: Double,
    val longitude: Double,
)

@HiltViewModel
class QuestCameraViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaUploadRepository: MediaUploadRepository,
    private val storyRepository: StoryRepository,
    private val heritageClassifier: HeritageClassifier,
    private val heritageMissionMatcher: HeritageMissionMatcher,
    private val appPreferenceStore: AppPreferenceStore,
    private val routeTracker: RouteTracker,
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuestCameraUiState())
    val uiState: StateFlow<QuestCameraUiState> = _uiState.asStateFlow()

    val captureTimerEnabled: StateFlow<Boolean> = appPreferenceStore.captureTimerEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferenceStore.DefaultCaptureTimerEnabled,
    )

    val captureTimerSeconds: StateFlow<Int> = appPreferenceStore.captureTimerSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferenceStore.DefaultCaptureTimerSeconds,
    )

    fun setCaptureTimerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferenceStore.setCaptureTimerEnabled(enabled)
        }
    }

    fun createCameraVideoFile(
        storyId: Long,
        chapterId: Long,
    ): File =
        mediaUploadRepository.createCameraVideoFile(
            storyId = storyId,
            chapterId = chapterId,
        )

    fun loadMissionContext(
        storyId: Long,
        chapterId: Long,
    ) {
        viewModelScope.launch {
            runCatching {
                storyRepository.getChapterSnapshot(
                    storyId = storyId,
                    chapterId = chapterId,
                )
            }.onSuccess { snapshot ->
                _uiState.update { state ->
                    state.copy(
                        expectedPlaceTitle = snapshot.chapter.place.title,
                        expectedPlaceLatitude = snapshot.chapter.place.latitude,
                        expectedPlaceLongitude = snapshot.chapter.place.longitude,
                    )
                }
            }.onFailure {
                runCatching {
                    storyRepository.getChapterDetail(
                        storyId = storyId,
                        chapterId = chapterId,
                    )
                }.onSuccess { chapter ->
                    _uiState.update { state ->
                        state.copy(
                            expectedPlaceTitle = chapter.place.title,
                            expectedPlaceLatitude = chapter.place.latitude,
                            expectedPlaceLongitude = chapter.place.longitude,
                        )
                    }
                }
            }
        }
    }

    fun onMediaReady(mediaResult: MediaCaptureResult) {
        _uiState.update { state ->
            state.copy(
                mediaResult = mediaResult,
                heritagePrediction = null,
                heritageValidation = HeritageMissionValidation.Pending,
                locationStatusMessage = null,
                locationIssue = null,
                isClassifyingHeritage = true,
                isSubmittingMission = false,
                hasNavigatedAfterSubmit = false,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                heritageClassifier.classify(mediaResult.imageUri)
            }.onSuccess { prediction ->
                val validation: HeritageMissionValidation = heritageMissionMatcher.evaluate(
                    expectedPlaceTitle = _uiState.value.expectedPlaceTitle,
                    prediction = prediction,
                )
                _uiState.update { state ->
                    if (validation is HeritageMissionValidation.Rejected) {
                        state.copy(
                            mediaResult = null,
                            heritagePrediction = null,
                            heritageValidation = HeritageMissionValidation.Pending,
                            locationStatusMessage = null,
                            locationIssue = null,
                            isClassifyingHeritage = false,
                            errorMessage = validation.message,
                        )
                    } else {
                        state.copy(
                            heritagePrediction = prediction,
                            heritageValidation = validation,
                            isClassifyingHeritage = false,
                        )
                    }
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        mediaResult = null,
                        heritagePrediction = null,
                        heritageValidation = HeritageMissionValidation.Rejected(
                            message = "사진을 판별하지 못했어요. 다시 촬영해 주세요.",
                        ),
                        isClassifyingHeritage = false,
                    )
                }
            }
        }
    }

    fun onImageReady(imageUri: Uri) {
        onMediaReady(MediaCaptureResult(imageUri = imageUri))
    }

    suspend fun resolveCaptureLocation(): Location? =
        if (hasMissionLocationPermission()) {
            resolveCurrentLocation()
        } else {
            null
        }

    fun resetImage() {
        _uiState.update { state ->
            state.copy(
                mediaResult = null,
                heritagePrediction = null,
                heritageValidation = HeritageMissionValidation.Pending,
                locationStatusMessage = null,
                locationIssue = null,
                isClassifyingHeritage = false,
                isSubmittingMission = false,
                hasNavigatedAfterSubmit = false,
                errorMessage = null,
            )
        }
    }

    fun showError(message: String) {
        _uiState.update { state ->
            state.copy(errorMessage = message)
        }
    }

    fun submitMissionImage(
        storyId: Long,
        chapterId: Long,
        allowUnverified: Boolean = false,
        onSubmitted: (Boolean) -> Unit,
    ) {
        val mediaResult: MediaCaptureResult = _uiState.value.mediaResult ?: return
        if (_uiState.value.hasNavigatedAfterSubmit || _uiState.value.isSubmittingMission) return
        if (!_uiState.value.canSubmitMission()) {
            _uiState.update { state ->
                state.copy(errorMessage = state.heritageValidation.toSubmitBlockMessage())
            }
            return
        }
        val remainingAfter: Int = storyRepository.remainingIncompleteChapterCountAfter(
            storyId = storyId,
            chapterId = chapterId,
        )
        val isAllSubmitted: Boolean = remainingAfter == 0
        _uiState.update { state ->
            state.copy(
                isSubmittingMission = true,
                locationStatusMessage = null,
                locationIssue = null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val locationVerification: MissionLocationVerification = verifyMissionLocation(mediaResult.imageUri)
            if (locationVerification is MissionLocationVerification.Blocked &&
                (!allowUnverified || !locationVerification.canSaveUnverified)
            ) {
                _uiState.update { state ->
                    state.copy(
                        mediaResult = if (locationVerification.shouldRetake) null else state.mediaResult,
                        heritagePrediction = if (locationVerification.shouldRetake) null else state.heritagePrediction,
                        heritageValidation = if (locationVerification.shouldRetake) {
                            HeritageMissionValidation.Pending
                        } else {
                            state.heritageValidation
                        },
                        isSubmittingMission = false,
                        locationStatusMessage = null,
                        locationIssue = locationVerification.toIssue(),
                        errorMessage = null,
                    )
                }
                return@launch
            }
            val isLocationVerified: Boolean = locationVerification !is MissionLocationVerification.Blocked
            val statusMessage: String = if (isLocationVerified) {
                locationVerification.message
            } else {
                UnverifiedMissionSavedMessage
            }
            runCatching {
                val uploadedMedia = mediaUploadRepository.uploadMissionImage(
                    storyId = storyId,
                    chapterId = chapterId,
                    imageUri = mediaResult.imageUri,
                )
                storyRepository.submitMissionFile(
                    storyId = storyId,
                    chapterId = chapterId,
                    fileUrl = uploadedMedia.fileUrl,
                    type = uploadedMedia.mediaType.apiValue,
                    locationVerificationStatus = locationVerification.apiValue,
                )
                if (VlogVideoSubmitEnabled && mediaResult.videoUri != null) {
                    val uploadedVideo = mediaUploadRepository.uploadMissionVideo(
                        storyId = storyId,
                        chapterId = chapterId,
                        videoUri = mediaResult.videoUri,
                        durationMillis = mediaResult.videoDurationMillis,
                    )
                    storyRepository.submitMissionFile(
                        storyId = storyId,
                        chapterId = chapterId,
                        fileUrl = uploadedVideo.fileUrl,
                        type = uploadedVideo.mediaType.apiValue,
                        locationVerificationStatus = locationVerification.apiValue,
                    )
                }
                storyRepository.submitMission(
                    storyId = storyId,
                    chapterId = chapterId,
                )
                storyRepository.setChapterLocationVerified(
                    storyId = storyId,
                    chapterId = chapterId,
                    isVerified = isLocationVerified,
                )
            }.onSuccess {
                storyRepository.markChapterCompletedOptimistically(chapterId = chapterId)
                if (isAllSubmitted) {
                    routeTracker.stop()
                }
                _uiState.update { state ->
                    state.copy(
                        isSubmittingMission = false,
                        hasNavigatedAfterSubmit = true,
                        locationStatusMessage = statusMessage,
                        locationIssue = null,
                        errorMessage = null,
                    )
                }
                if (!isLocationVerified) {
                    delay(UnverifiedSubmitMessageDelayMillis)
                }
                onSubmitted(isAllSubmitted)
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isSubmittingMission = false,
                        errorMessage = throwable.localizedMessage ?: "Mission submit failed. Please try again.",
                    )
                }
            }
        }
    }

    private suspend fun verifyMissionLocation(imageUri: Uri): MissionLocationVerification {
        val targetLatitude: Double = _uiState.value.expectedPlaceLatitude ?: return MissionLocationVerification.UnknownPlace
        val targetLongitude: Double = _uiState.value.expectedPlaceLongitude ?: return MissionLocationVerification.UnknownPlace
        val requiresStrictGpsVerification: Boolean = isStrictGpsMissionPlace(
            latitude = targetLatitude,
            longitude = targetLongitude,
        )
        val hasCurrentLocationPermission: Boolean = hasMissionLocationPermission()
        val currentLocation: Location? = if (hasCurrentLocationPermission) {
            resolveCurrentLocation()
        } else {
            null
        }
        if (requiresStrictGpsVerification) {
            if (currentLocation != null &&
                currentLocation.distanceMetersTo(targetLatitude, targetLongitude) <= StrictGpsVerificationRadiusMeters
            ) {
                return MissionLocationVerification.CurrentGps
            }
            val message: String = if (currentLocation == null) {
                StrictGpsUnavailableMessage
            } else {
                StrictGpsMismatchMessage
            }
            return MissionLocationVerification.Blocked(
                message = message,
                canRequestPermission = !hasCurrentLocationPermission,
                canSaveUnverified = false,
                shouldRetake = currentLocation != null,
            )
        }
        if (currentLocation != null &&
            currentLocation.distanceMetersTo(targetLatitude, targetLongitude) <= MissionPlaceVerificationRadiusMeters
        ) {
            return MissionLocationVerification.CurrentGps
        }
        val photoLocation: MissionGeoPoint? = readPhotoExifLocation(imageUri)
        if (photoLocation != null &&
            photoLocation.distanceMetersTo(targetLatitude, targetLongitude) <= MissionPlaceVerificationRadiusMeters
        ) {
            return MissionLocationVerification.PhotoExifPlace
        }
        if (!hasCurrentLocationPermission && photoLocation?.isInGyeongjuArea() == true) {
            return MissionLocationVerification.PhotoExifArea
        }
        val canRequestLocationPermission: Boolean = !hasCurrentLocationPermission || !hasPhotoLocationPermission()
        if (photoLocation == null) {
            return MissionLocationVerification.Blocked(
                message = MissingExifLocationMessage,
                canRequestPermission = canRequestLocationPermission,
            )
        }
        return MissionLocationVerification.Blocked(
            message = MismatchedExifLocationMessage,
            canRequestPermission = canRequestLocationPermission,
        )
    }

    private fun hasMissionLocationPermission(): Boolean =
        MissionLocationPermissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasPhotoLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private suspend fun resolveCurrentLocation(): Location? {
        val locationManager: LocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val recentLocation: Location? = resolveRecentKnownLocation(locationManager)
        if (recentLocation != null) return recentLocation
        val provider: String = selectMissionLocationProvider(locationManager) ?: return null
        return requestCurrentLocation(locationManager, provider)
    }

    @SuppressLint("MissingPermission")
    private fun resolveRecentKnownLocation(locationManager: LocationManager): Location? {
        if (!hasMissionLocationPermission()) return null
        return runCatching {
            locationManager.getProviders(true)
                .mapNotNull { provider ->
                    runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                }
                .filter(Location::isRecentForMission)
                .maxByOrNull { location -> location.time }
        }.getOrNull()
    }

    private fun selectMissionLocationProvider(locationManager: LocationManager): String? {
        val enabledProviders: List<String> = runCatching {
            locationManager.getProviders(true)
        }.getOrDefault(emptyList())
        if (enabledProviders.isEmpty()) return null
        val hasFineLocation: Boolean = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val preferredProviders: List<String> = if (hasFineLocation) {
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
        } else {
            listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
        }
        return preferredProviders.firstOrNull(enabledProviders::contains)
            ?: enabledProviders.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(
        locationManager: LocationManager,
        provider: String,
    ): Location? =
        withTimeoutOrNull(CurrentLocationFixTimeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                }
                continuation.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
                runCatching {
                    locationManager.requestLocationUpdates(
                        provider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                }.onFailure {
                    locationManager.removeUpdates(listener)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }

    private fun readPhotoExifLocation(imageUri: Uri): MissionGeoPoint? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !hasPhotoLocationPermission()) {
            return readPhotoExifLocationFromUri(imageUri)
        }
        val originalUri: Uri = MediaStore.setRequireOriginal(imageUri)
        return readPhotoExifLocationFromUri(originalUri)
            ?: readPhotoExifLocationFromUri(imageUri)
    }

    private fun readPhotoExifLocationFromUri(imageUri: Uri): MissionGeoPoint? =
        runCatching {
            context.contentResolver.openInputStream(imageUri).use { inputStream ->
                requireNotNull(inputStream) { "Image could not be opened." }
                val coordinates = FloatArray(2)
                if (ExifInterface(inputStream).getLatLong(coordinates)) {
                    MissionGeoPoint(
                        latitude = coordinates[0].toDouble(),
                        longitude = coordinates[1].toDouble(),
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
}

@Composable
fun QuestCameraRoute(
    storyId: Long,
    chapterId: Long,
    onBack: () -> Unit,
    onSubmitted: (Boolean) -> Unit,
    validationMode: Boolean = false,
    viewModel: QuestCameraViewModel = hiltViewModel(),
) {
    val uiState: QuestCameraUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val captureTimerEnabled: Boolean by viewModel.captureTimerEnabled.collectAsStateWithLifecycle()
    val captureTimerSeconds: Int by viewModel.captureTimerSeconds.collectAsStateWithLifecycle()
    LaunchedEffect(storyId, chapterId, validationMode) {
        if (!validationMode) {
            viewModel.loadMissionContext(
                storyId = storyId,
                chapterId = chapterId,
            )
        }
    }
    QuestCameraScreen(
        storyId = storyId,
        chapterId = chapterId,
        validationMode = validationMode,
        uiState = uiState,
        captureTimerEnabled = captureTimerEnabled,
        captureTimerSeconds = captureTimerSeconds,
        onCaptureTimerEnabledChange = viewModel::setCaptureTimerEnabled,
        onBack = onBack,
        onCreateVideoFile = viewModel::createCameraVideoFile,
        onMediaReady = viewModel::onMediaReady,
        onResolveCaptureLocation = viewModel::resolveCaptureLocation,
        onResetImage = viewModel::resetImage,
        onSubmit = { allowUnverified ->
            viewModel.submitMissionImage(
                storyId = storyId,
                chapterId = chapterId,
                allowUnverified = allowUnverified,
                onSubmitted = onSubmitted,
            )
        },
        onError = viewModel::showError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestCameraScreen(
    storyId: Long,
    chapterId: Long,
    validationMode: Boolean,
    uiState: QuestCameraUiState,
    captureTimerEnabled: Boolean,
    captureTimerSeconds: Int,
    onCaptureTimerEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCreateVideoFile: (Long, Long) -> File,
    onMediaReady: (MediaCaptureResult) -> Unit,
    onResolveCaptureLocation: suspend () -> Location?,
    onResetImage: () -> Unit,
    onSubmit: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    ImmersiveNavigationBarEffect()
    val context: Context = LocalContext.current
    val imageCapture: ImageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val videoCapture: VideoCapture<Recorder> = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        VideoCapture.withOutput(recorder)
    }
    var hasCameraPermission by remember {
        mutableStateOf(isCameraPermissionGranted(context))
    }
    var isCameraReady by rememberSaveable {
        mutableStateOf(false)
    }
    var isCapturing by rememberSaveable {
        mutableStateOf(false)
    }
    var countdown by rememberSaveable {
        mutableStateOf<Int?>(null)
    }
    var activeVlogRecording by remember {
        mutableStateOf<VlogRecordingState?>(null)
    }
    var isWaitingForCaptureLocationPermission by rememberSaveable {
        mutableStateOf(false)
    }
    fun startCaptureCountdown() {
        if (captureTimerEnabled && VlogVideoCaptureEnabled) {
            activeVlogRecording = startVlogVideoRecording(
                context = context,
                videoCapture = videoCapture,
                outputFile = onCreateVideoFile(storyId, chapterId),
                onError = onError,
            )
        }
        countdown = if (captureTimerEnabled) {
            captureTimerSeconds.coerceIn(
                AppPreferenceStore.MinCaptureTimerSeconds,
                AppPreferenceStore.MaxCaptureTimerSeconds,
            )
        } else {
            0
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            onError("카메라 권한이 필요해요.")
        }
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            onMediaReady(MediaCaptureResult(imageUri = uri))
        }
    }
    val captureLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        isWaitingForCaptureLocationPermission = false
        startCaptureCountdown()
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val isGranted: Boolean = permissions.values.any { granted -> granted }
        onSubmit(!isGranted)
    }
    LaunchedEffect(countdown) {
        val currentCountdown: Int = countdown ?: return@LaunchedEffect
        if (currentCountdown > 0) {
            delay(CountdownIntervalMillis)
            countdown = currentCountdown - 1
        } else {
            countdown = null
            isCapturing = true
            val finishedVlogRecording = activeVlogRecording
            activeVlogRecording = null
            finishedVlogRecording?.recording?.stop()
            val captureLocation: Location? = onResolveCaptureLocation()
            captureImage(
                context = context,
                imageCapture = imageCapture,
                storyId = storyId,
                chapterId = chapterId,
                location = captureLocation,
                onSuccess = { uri ->
                    isCapturing = false
                    onMediaReady(
                        MediaCaptureResult(
                            imageUri = uri,
                            videoUri = finishedVlogRecording?.outputUri,
                            videoDurationMillis = finishedVlogRecording?.durationMillis(),
                        ),
                    )
                },
                onFailure = { message ->
                    isCapturing = false
                    onError(message)
                },
            )
        }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (validationMode) "AI 비전 검증" else "사진 인증",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cameraBackgroundBrush())
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val mediaResult: MediaCaptureResult? = uiState.mediaResult
                if (mediaResult == null) {
                    CameraCaptureContent(
                        imageCapture = imageCapture,
                        videoCapture = if (VlogVideoCaptureEnabled && captureTimerEnabled) videoCapture else null,
                        hasCameraPermission = hasCameraPermission,
                        isCameraReady = isCameraReady,
                        isCapturing = isCapturing,
                        countdown = countdown?.takeIf { it > 0 },
                        captureTimerEnabled = captureTimerEnabled,
                        captureTimerSeconds = captureTimerSeconds,
                        onCaptureTimerEnabledChange = onCaptureTimerEnabledChange,
                        onCameraReady = { isCameraReady = true },
                        onCameraError = onError,
                        onRequestCameraPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onStartCountdown = {
                            if (!hasCameraPermission) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else if (isCameraReady &&
                                !isCapturing &&
                                countdown == null &&
                                !isWaitingForCaptureLocationPermission
                            ) {
                                if (hasMissionLocationPermission(context)) {
                                    startCaptureCountdown()
                                } else {
                                    isWaitingForCaptureLocationPermission = true
                                    captureLocationPermissionLauncher.launch(MissionLocationPermissions)
                                }
                            }
                        },
                        onPickImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    CapturedImageContent(
                        mediaResult = mediaResult,
                        heritagePrediction = uiState.heritagePrediction,
                        heritageValidation = uiState.heritageValidation,
                        expectedPlaceTitle = uiState.expectedPlaceTitle,
                        isClassifyingHeritage = uiState.isClassifyingHeritage,
                        isSubmittingMission = uiState.isSubmittingMission,
                        hasNavigatedAfterSubmit = uiState.hasNavigatedAfterSubmit,
                        validationMode = validationMode,
                        onResetImage = onResetImage,
                        onSubmit = { onSubmit(false) },
                        modifier = Modifier.weight(1f),
                    )
                }
                uiState.locationStatusMessage?.let { message ->
                    StatusCard(text = message)
                }
                uiState.locationIssue?.let { issue ->
                    LocationVerificationIssueCard(
                        issue = issue,
                        isSubmitting = uiState.isSubmittingMission,
                        onRequestLocationPermission = {
                            locationPermissionLauncher.launch(MissionLocationVerificationPermissions)
                        },
                        onSaveUnverified = {
                            onSubmit(true)
                        },
                    )
                }
                uiState.errorMessage?.let { message ->
                    StatusCard(text = message)
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureContent(
    imageCapture: ImageCapture,
    videoCapture: VideoCapture<Recorder>?,
    hasCameraPermission: Boolean,
    isCameraReady: Boolean,
    isCapturing: Boolean,
    countdown: Int?,
    captureTimerEnabled: Boolean,
    captureTimerSeconds: Int,
    onCaptureTimerEnabledChange: (Boolean) -> Unit,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onStartCountdown: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CameraPreviewFrame(
                imageCapture = imageCapture,
                videoCapture = videoCapture,
                hasCameraPermission = hasCameraPermission,
                countdown = countdown,
                onCameraReady = onCameraReady,
                onCameraError = onCameraError,
                onRequestCameraPermission = onRequestCameraPermission,
                modifier = Modifier
                    .weight(LandscapePreviewWeight)
                    .fillMaxSize(),
            )
            CameraActions(
                hasCameraPermission = hasCameraPermission,
                isCameraReady = isCameraReady,
                isCapturing = isCapturing,
                countdown = countdown,
                captureTimerEnabled = captureTimerEnabled,
                captureTimerSeconds = captureTimerSeconds,
                onCaptureTimerEnabledChange = onCaptureTimerEnabledChange,
                onStartCountdown = onStartCountdown,
                onPickImage = onPickImage,
                modifier = Modifier
                    .weight(LandscapeControlsWeight)
                    .align(Alignment.CenterVertically)
                    .verticalScroll(rememberScrollState()),
            )
        }
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CameraPreviewFrame(
            imageCapture = imageCapture,
            videoCapture = videoCapture,
            hasCameraPermission = hasCameraPermission,
            countdown = countdown,
            onCameraReady = onCameraReady,
            onCameraError = onCameraError,
            onRequestCameraPermission = onRequestCameraPermission,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        CameraActions(
            hasCameraPermission = hasCameraPermission,
            isCameraReady = isCameraReady,
            isCapturing = isCapturing,
            countdown = countdown,
            captureTimerEnabled = captureTimerEnabled,
            captureTimerSeconds = captureTimerSeconds,
            onCaptureTimerEnabledChange = onCaptureTimerEnabledChange,
            onStartCountdown = onStartCountdown,
            onPickImage = onPickImage,
        )
    }
}

@Composable
private fun CameraPreviewFrame(
    imageCapture: ImageCapture,
    videoCapture: VideoCapture<Recorder>?,
    hasCameraPermission: Boolean,
    countdown: Int?,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF2B1B23)),
        contentAlignment = Alignment.Center,
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                imageCapture = imageCapture,
                videoCapture = videoCapture,
                onCameraReady = onCameraReady,
                onCameraError = onCameraError,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PermissionRequiredContent(
                onRequestCameraPermission = onRequestCameraPermission,
            )
        }
        countdown?.let { currentCountdown ->
            CountdownOverlay(countdown = currentCountdown)
        }
    }
}

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    videoCapture: VideoCapture<Recorder>?,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context: Context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
    DisposableEffect(lifecycleOwner, imageCapture, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val targetRotation: Int = previewView.display?.rotation ?: Surface.ROTATION_0
                imageCapture.targetRotation = targetRotation
                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also { cameraPreview ->
                        cameraPreview.setSurfaceProvider(previewView.surfaceProvider)
                    }
                cameraProvider.unbindAll()
                if (videoCapture == null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        videoCapture,
                    )
                }
                onCameraReady()
            }.onFailure { throwable ->
                onCameraError(throwable.localizedMessage ?: "카메라를 준비하지 못했어요.")
            }
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }
}

@Composable
private fun PermissionRequiredContent(
    onRequestCameraPermission: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoCamera,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = "카메라 권한을 허용해 주세요.",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = onRequestCameraPermission,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Text(text = "권한 허용")
        }
    }
}

@Composable
private fun CountdownOverlay(
    countdown: Int,
) {
    Surface(
        modifier = Modifier.size(112.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.48f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = countdown.coerceAtLeast(1).toString(),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun CameraActions(
    hasCameraPermission: Boolean,
    isCameraReady: Boolean,
    isCapturing: Boolean,
    countdown: Int?,
    captureTimerEnabled: Boolean,
    captureTimerSeconds: Int,
    onCaptureTimerEnabledChange: (Boolean) -> Unit,
    onStartCountdown: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CaptureTimerToggleRow(
            enabled = captureTimerEnabled,
            seconds = captureTimerSeconds,
            canChange = !isCapturing && countdown == null,
            onEnabledChange = onCaptureTimerEnabledChange,
        )
        Button(
            onClick = onStartCountdown,
            enabled = !isCapturing && countdown == null && (!hasCameraPermission || isCameraReady),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onBackground,
                disabledContainerColor = Color(0xFFFFE8A2),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (countdown == null) "촬영하기" else "준비 중",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedButton(
            onClick = onPickImage,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Collections,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = "갤러리에서 선택")
        }
    }
}

@Composable
private fun CaptureTimerToggleRow(
    enabled: Boolean,
    seconds: Int,
    canChange: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val safeSeconds: Int = seconds.coerceIn(
        AppPreferenceStore.MinCaptureTimerSeconds,
        AppPreferenceStore.MaxCaptureTimerSeconds,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.86f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (enabled) Icons.Rounded.Timer else Icons.Rounded.TimerOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "촬영 타이머",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (enabled) "${safeSeconds}초 후 촬영" else "바로 촬영",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = canChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE4DDE1),
                ),
            )
        }
    }
}

@Composable
private fun CapturedImageContent(
    mediaResult: MediaCaptureResult,
    heritagePrediction: HeritagePrediction?,
    heritageValidation: HeritageMissionValidation,
    expectedPlaceTitle: String,
    isClassifyingHeritage: Boolean,
    isSubmittingMission: Boolean,
    hasNavigatedAfterSubmit: Boolean,
    validationMode: Boolean,
    onResetImage: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSubmit: Boolean = !hasNavigatedAfterSubmit &&
        !isSubmittingMission &&
        !isClassifyingHeritage &&
        heritageValidation.canSubmit()
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CapturedImagePreview(
                mediaResult = mediaResult,
                modifier = Modifier
                    .weight(LandscapePreviewWeight)
                    .fillMaxSize(),
            )
            Column(
                modifier = Modifier
                    .weight(LandscapeControlsWeight)
                    .align(Alignment.CenterVertically)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (VlogVideoCaptureEnabled && mediaResult.videoUri != null) {
                    Text(
                        text = "브이로그 클립도 함께 저장됐어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HeritagePredictionStatus(
                    prediction = heritagePrediction,
                    validation = heritageValidation,
                    expectedPlaceTitle = expectedPlaceTitle,
                    isClassifying = isClassifyingHeritage,
                    validationMode = validationMode,
                )
                CapturedImageActions(
                    canSubmit = canSubmit,
                    canReset = !hasNavigatedAfterSubmit && !isSubmittingMission,
                    showSubmit = !validationMode,
                    isSubmitting = isSubmittingMission,
                    onResetImage = onResetImage,
                    onSubmit = onSubmit,
                )
            }
        }
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            CultureAsyncImage(
                model = mediaResult.imageUri,
                contentDescription = "인증 사진",
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (VlogVideoCaptureEnabled && mediaResult.videoUri != null) {
            Text(
                text = "브이로그 클립도 함께 저장됐어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // AI_MODEL_INTEGRATION: Display local model prediction without blocking submit.
        HeritagePredictionStatus(
            prediction = heritagePrediction,
            validation = heritageValidation,
            expectedPlaceTitle = expectedPlaceTitle,
            isClassifying = isClassifyingHeritage,
            validationMode = validationMode,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onResetImage,
                enabled = !hasNavigatedAfterSubmit && !isSubmittingMission,
                modifier = Modifier
                    .weight(if (validationMode) 2f else 1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "다시")
            }
            if (!validationMode) {
                Button(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    Text(
                        text = "제출",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapturedImagePreview(
    mediaResult: MediaCaptureResult,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        CultureAsyncImage(
            model = mediaResult.imageUri,
            contentDescription = "인증 사진",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CapturedImageActions(
    canSubmit: Boolean,
    canReset: Boolean,
    showSubmit: Boolean = true,
    isSubmitting: Boolean = false,
    onResetImage: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onResetImage,
            enabled = canReset,
            modifier = Modifier
                .weight(if (showSubmit) 1f else 2f)
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = "다시")
        }
        if (showSubmit) {
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Text(
                    text = "제출",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HeritagePredictionStatus(
    prediction: HeritagePrediction?,
    validation: HeritageMissionValidation,
    expectedPlaceTitle: String,
    isClassifying: Boolean,
    validationMode: Boolean = false,
) {
    val text = when {
        validationMode && isClassifying -> "AI 비전 모델 분류 중..."
        validationMode && prediction != null -> prediction.toTop3ValidationText()
        validationMode -> "촬영하거나 갤러리에서 이미지를 선택하면 상위 3개 퍼센트가 표시돼요."
        isClassifying -> "AI가 ${expectedPlaceTitle.ifBlank { "미션 장소" }} 사진인지 확인하고 있어요."
        validation is HeritageMissionValidation.Accepted -> {
            val percent = ((prediction?.probability ?: 0f) * 100).toInt()
            "인증 가능: ${prediction?.labelKo.orEmpty()} $percent%"
        }
        validation is HeritageMissionValidation.Rejected -> validation.message
        validation is HeritageMissionValidation.Unknown -> "자동 판별 기준이 없는 장소라 사진 제출이 가능해요."
        prediction != null -> {
            val percent = (prediction.probability * 100).toInt()
            "AI 예측: ${prediction.labelKo} $percent%"
        }
        else -> "AI 판별 결과를 기다리고 있어요."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.86f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun HeritagePrediction.toTop3ValidationText(): String =
    topK
        .take(3)
        .mapIndexed { index, candidate ->
            "${index + 1}. ${candidate.labelKo} ${"%.1f".format(candidate.probability * 100f)}%"
        }
        .joinToString(separator = "\n", prefix = "상위 3개 예측\n")

private fun QuestCameraUiState.canSubmitMission(): Boolean =
    !isSubmittingMission &&
        !isClassifyingHeritage &&
        mediaResult != null &&
        when (heritageValidation) {
            HeritageMissionValidation.Accepted,
            HeritageMissionValidation.Unknown -> true
            HeritageMissionValidation.Pending,
            is HeritageMissionValidation.Rejected -> false
        }

private fun HeritageMissionValidation.canSubmit(): Boolean =
    when (this) {
        HeritageMissionValidation.Accepted,
        HeritageMissionValidation.Unknown -> true
        HeritageMissionValidation.Pending,
        is HeritageMissionValidation.Rejected -> false
    }

private fun HeritageMissionValidation.toSubmitBlockMessage(): String =
    when (this) {
        HeritageMissionValidation.Pending -> "사진 판별이 끝난 뒤 제출할 수 있어요."
        HeritageMissionValidation.Accepted,
        HeritageMissionValidation.Unknown -> ""
        is HeritageMissionValidation.Rejected -> message
    }

@Composable
private fun LocationVerificationIssueCard(
    issue: MissionLocationIssue,
    isSubmitting: Boolean,
    onRequestLocationPermission: () -> Unit,
    onSaveUnverified: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A314A),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (issue.canRequestPermission) {
                    Button(
                        onClick = onRequestLocationPermission,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    ) {
                        Text(text = "위치 권한 허용하기")
                    }
                }
                if (issue.canSaveUnverified) {
                    OutlinedButton(
                        onClick = onSaveUnverified,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(text = "임시 저장")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7A314A),
        )
    }
}

private data class VlogRecordingState(
    val recording: Recording,
    val outputUri: Uri,
    val startedAtMillis: Long,
) {
    fun durationMillis(): Long =
        (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
}

private data class CameraImageOutputTarget(
    val options: ImageCapture.OutputFileOptions,
    val legacyFile: File? = null,
)

private fun startVlogVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    outputFile: File,
    onError: (String) -> Unit,
): VlogRecordingState? {
    val outputOptions = FileOutputOptions.Builder(outputFile).build()
    val startedAtMillis = System.currentTimeMillis()
    return runCatching {
        val recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize && event.hasError()) {
                    onError("브이로그 클립 저장에 실패했어요. 사진 인증은 계속 진행할 수 있어요.")
                }
            }
        VlogRecordingState(
            recording = recording,
            outputUri = Uri.fromFile(outputFile),
            startedAtMillis = startedAtMillis,
        )
    }.getOrElse { throwable ->
        onError(throwable.localizedMessage ?: "브이로그 클립 녹화를 시작하지 못했어요.")
        null
    }
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    storyId: Long,
    chapterId: Long,
    location: Location?,
    onSuccess: (Uri) -> Unit,
    onFailure: (String) -> Unit,
) {
    val outputTarget: CameraImageOutputTarget = createCameraImageOutputTarget(
        context = context,
        storyId = storyId,
        chapterId = chapterId,
        location = location,
    )
    imageCapture.takePicture(
        outputTarget.options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputTarget.legacyFile?.let { file ->
                    scanCameraImageFile(
                        context = context,
                        file = file,
                    )
                }
                val savedUri: Uri? = outputFileResults.savedUri ?: outputTarget.legacyFile?.let(Uri::fromFile)
                if (savedUri == null) {
                    onFailure("?ъ쭊 ???寃쎈줈瑜? 李얠? 紐삵뻽?댁슂.")
                    return
                }
                onSuccess(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                onFailure(exception.localizedMessage ?: "사진을 저장하지 못했어요.")
            }
        },
    )
}

private fun createCameraImageOutputTarget(
    context: Context,
    storyId: Long,
    chapterId: Long,
    location: Location?,
): CameraImageOutputTarget {
    val fileName: String = createCameraImageFileName(
        storyId = storyId,
        chapterId = chapterId,
    )
    val metadata: ImageCapture.Metadata = createCameraImageMetadata(location)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, CameraImageMimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$CameraImageAlbumName",
            )
        }
        return CameraImageOutputTarget(
            options = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues,
            ).setMetadata(metadata).build(),
        )
    }
    val picturesDir: File = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ?: File(context.filesDir, Environment.DIRECTORY_PICTURES)
    val outputDir = File(picturesDir, CameraImageAlbumName).apply {
        mkdirs()
    }
    val outputFile = File(outputDir, fileName)
    return CameraImageOutputTarget(
        options = ImageCapture.OutputFileOptions.Builder(outputFile)
            .setMetadata(metadata)
            .build(),
        legacyFile = outputFile,
    )
}

private fun createCameraImageMetadata(location: Location?): ImageCapture.Metadata =
    ImageCapture.Metadata().apply {
        location?.let(::setLocation)
    }

private fun createCameraImageFileName(
    storyId: Long,
    chapterId: Long,
): String =
    "mission_${storyId}_${chapterId}_${System.currentTimeMillis()}.$CameraImageExtension"

private fun scanCameraImageFile(
    context: Context,
    file: File,
) {
    MediaScannerConnection.scanFile(
        context,
        arrayOf(file.absolutePath),
        arrayOf(CameraImageMimeType),
        null,
    )
}

private fun isCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

private fun hasMissionLocationPermission(context: Context): Boolean =
    MissionLocationPermissions.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

private fun MissionLocationVerification.Blocked.toIssue(): MissionLocationIssue =
    MissionLocationIssue(
        message = message,
        canRequestPermission = canRequestPermission,
        canSaveUnverified = canSaveUnverified,
    )

private fun isStrictGpsMissionPlace(
    latitude: Double,
    longitude: Double,
): Boolean =
    StrictGpsMissionPlaces.any { place ->
        place.distanceMetersTo(
            latitude = latitude,
            longitude = longitude,
        ) <= StrictGpsPlaceMatchToleranceMeters
    }

private fun Location.distanceMetersTo(
    latitude: Double,
    longitude: Double,
): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        this.latitude,
        this.longitude,
        latitude,
        longitude,
        results,
    )
    return results.first()
}

private fun MissionGeoPoint.distanceMetersTo(
    latitude: Double,
    longitude: Double,
): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        this.latitude,
        this.longitude,
        latitude,
        longitude,
        results,
    )
    return results.first()
}

private fun MissionGeoPoint.isInGyeongjuArea(): Boolean =
    distanceMetersTo(
        latitude = GyeongjuCenterLatitude,
        longitude = GyeongjuCenterLongitude,
    ) <= GyeongjuAreaVerificationRadiusMeters

private fun Location.isRecentForMission(): Boolean {
    val ageMillis: Long = System.currentTimeMillis() - time
    return time > 0L && ageMillis in 0..CurrentLocationMaxAgeMillis
}

private val MissionLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
private val MissionLocationVerificationPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MissionLocationPermissions + Manifest.permission.ACCESS_MEDIA_LOCATION
    } else {
        MissionLocationPermissions
    }
private const val CountdownStart = 3
private const val CountdownIntervalMillis = 1000L
private const val LandscapePreviewWeight = 1f
private const val LandscapeControlsWeight = 0.42f
private const val MissionPlaceVerificationRadiusMeters = 150f
private val StrictGpsMissionPlaces = listOf(
    MissionGeoPoint(latitude = 36.1071660, longitude = 128.4164430),
    MissionGeoPoint(latitude = 36.1071590, longitude = 128.4162900),
)
private const val StrictGpsVerificationRadiusMeters = 10f
private const val StrictGpsPlaceMatchToleranceMeters = 5_000f
private const val GyeongjuCenterLatitude = 35.8562
private const val GyeongjuCenterLongitude = 129.2247
private const val GyeongjuAreaVerificationRadiusMeters = 50_000f
private const val CurrentLocationMaxAgeMillis = 120_000L
private const val CurrentLocationFixTimeoutMillis = 5_000L
private const val UnverifiedSubmitMessageDelayMillis = 1_200L
private const val CameraImageAlbumName = "Atoria"
private const val CameraImageMimeType = "image/jpeg"
private const val CameraImageExtension = "jpg"
private const val MissingExifLocationMessage =
    "이 사진에는 위치 정보가 없어 장소 인증을 확인할 수 없어요.\n\n위치 권한을 허용하거나, 위치 정보가 포함된 사진을 선택해 주세요."
private const val MismatchedExifLocationMessage =
    "이 사진에는 위치 정보가 일치 하지않아 장소 인증을 확인할 수 없어요.\n\n위치 권한을 허용하거나, 위치 정보가 일치하는 사진을 선택해 주세요."
private const val UnverifiedMissionSavedMessage = "사진은 저장했지만, 위치 인증이 완료되지 않았어요."
private const val StrictGpsUnavailableMessage =
    "이 장소는 현재 GPS 위치 확인이 필요해요. 위치 권한과 GPS를 켠 뒤 다시 제출해 주세요."
private const val StrictGpsMismatchMessage =
    "현재 GPS 위치가 목표 지점 10m 밖이에요. 목표 지점 10m 이내에서 다시 촬영해 주세요."
// VLOG_READY: set this to true to record a silent mp4 clip during the countdown.
private const val VlogVideoCaptureEnabled = false
// VLOG_READY: set this to true after the backend accepts mission VIDEO file records.
private const val VlogVideoSubmitEnabled = false

@Composable
private fun cameraBackgroundBrush(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.background,
        ),
    )

