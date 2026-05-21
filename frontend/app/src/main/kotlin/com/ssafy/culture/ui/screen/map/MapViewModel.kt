package com.ssafy.culture.ui.screen.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.repository.KakaoLocalRepository
import com.ssafy.culture.data.repository.KakaoPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MapViewModel @Inject constructor(
    private val kakaoLocalRepository: KakaoLocalRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var hasLoadedInitialSearch: Boolean = false

    fun loadInitialSearch() {
        if (hasLoadedInitialSearch) return
        hasLoadedInitialSearch = true
        searchAtCurrentCenter(keyword = _uiState.value.activeKeyword)
    }

    fun updateMapCenter(center: MapCenter, isUserGesture: Boolean = false) {
        _uiState.update { state ->
            state.copy(
                currentCenter = center,
                isFollowingCurrentLocation = if (isUserGesture) false else state.isFollowingCurrentLocation,
            )
        }
    }

    fun stopFollowingCurrentLocation() {
        _uiState.update { state ->
            if (state.isFollowingCurrentLocation) state.copy(isFollowingCurrentLocation = false) else state
        }
    }

    fun searchByKeyword(keywordId: String) {
        val keyword = _uiState.value.keywordChips.firstOrNull { chip ->
            chip.id == keywordId
        } ?: return
        _uiState.update { state ->
            state.copy(
                activeKeyword = keyword.keyword,
                keywordChips = state.keywordChips.map { chip ->
                    chip.copy(isSelected = chip.id == keywordId)
                },
            )
        }
        searchAtCurrentCenter(keyword = keyword.keyword)
    }

    fun researchAtCurrentCenter() {
        searchAtCurrentCenter(keyword = _uiState.value.activeKeyword)
    }

    fun selectHeritageMarker(marker: HeritageMarker) {
        _uiState.update { state ->
            state.copy(selectedOverlay = marker.toSelectedOverlay())
        }
    }

    fun selectSearchResult(result: KeywordSearchResult) {
        _uiState.update { state ->
            state.copy(selectedOverlay = result.toSelectedOverlay())
        }
    }

    fun selectOverlay(overlay: SelectedMarkerOverlay?) {
        _uiState.update { state ->
            state.copy(selectedOverlay = overlay)
        }
    }

    fun clearSelectedOverlay() {
        _uiState.update { state ->
            state.copy(selectedOverlay = null)
        }
    }

    fun setCurrentLocation(center: MapCenter) {
        _uiState.update { state ->
            state.copy(
                currentLocation = center,
                currentCenter = center,
                isFollowingCurrentLocation = true,
            )
        }
    }

    private fun searchAtCurrentCenter(keyword: String) {
        if (keyword.isBlank()) return
        val center = _uiState.value.currentCenter
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isSearching = true,
                    errorMessage = null,
                )
            }
            runCatching {
                kakaoLocalRepository.searchKeyword(
                    query = keyword,
                    x = center.longitude,
                    y = center.latitude,
                    radius = SearchRadiusMeters,
                    size = SearchPageSize,
                )
            }.onSuccess { result ->
                _uiState.update { state ->
                    state.copy(
                        searchResults = result.places.mapNotNull { place ->
                            place.toKeywordSearchResult()
                        },
                        isSearching = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        errorMessage = throwable.localizedMessage ?: "주변 장소를 검색하지 못했어요.",
                    )
                }
            }
        }
    }

    private fun KakaoPlace.toKeywordSearchResult(): KeywordSearchResult? {
        val longitude = x ?: return null
        val latitude = y ?: return null
        return KeywordSearchResult(
            id = id.ifBlank { "$latitude,$longitude" },
            title = name,
            latitude = latitude,
            longitude = longitude,
            category = categoryGroupName.ifBlank { categoryName.ifBlank { "장소" } },
            address = roadAddressName.ifBlank { addressName },
            distanceMeters = distanceMeters,
            placeUrl = placeUrl,
        )
    }

    private companion object {
        const val SearchRadiusMeters = 5000
        const val SearchPageSize = 15
    }
}
