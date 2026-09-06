package com.silvertongue.paraphraser.overlay

sealed interface OverlayUiState {
    data object Collapsed : OverlayUiState
    data object Loading : OverlayUiState
    data class Suggestions(val items: List<String>, val fallbackProvider: String? = null) : OverlayUiState
    data class Failed(val message: String) : OverlayUiState
}
