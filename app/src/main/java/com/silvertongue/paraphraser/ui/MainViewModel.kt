package com.silvertongue.paraphraser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silvertongue.paraphraser.AppGraph
import com.silvertongue.paraphraser.paraphrase.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isAccessibilityEnabled: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val isServiceRunning: Boolean = false,
    val hasVendorOverlayGate: Boolean = false,
    val canSelfRepair: Boolean = false,
    val isRepairing: Boolean = false,
    val activeProvider: ProviderId = ProviderId.GROQ,
    val configuredProviders: Set<ProviderId> = emptySet(),
    val keyOverrides: Map<ProviderId, String> = ProviderId.entries.associateWith { "" },
    val testInput: String = "",
    val isParaphrasing: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val answeredBy: ProviderId? = null,
    val usedFallback: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel : ViewModel() {

    private val settings = AppGraph.settings
    private val repository = AppGraph.paraphraseRepository

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.activeProvider.collect { provider ->
                _uiState.update { it.copy(activeProvider = provider) }
            }
        }
        viewModelScope.launch {
            combine(ProviderId.entries.map { settings.hasKey(it) }) { flags ->
                ProviderId.entries.filterIndexed { index, _ -> flags[index] }.toSet()
            }.collect { configured ->
                _uiState.update { it.copy(configuredProviders = configured) }
            }
        }
        viewModelScope.launch {
            combine(ProviderId.entries.map { settings.keyOverride(it) }) { values ->
                ProviderId.entries.mapIndexed { index, provider -> provider to values[index] }.toMap()
            }.collect { overrides ->
                _uiState.update { it.copy(keyOverrides = overrides) }
            }
        }
    }

    fun refreshPermissions(
        isAccessibilityEnabled: Boolean,
        isServiceRunning: Boolean,
        canDrawOverlays: Boolean,
        hasVendorOverlayGate: Boolean,
        canSelfRepair: Boolean
    ) {
        _uiState.update {
            it.copy(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isServiceRunning = isServiceRunning,
                canDrawOverlays = canDrawOverlays,
                hasVendorOverlayGate = hasVendorOverlayGate,
                canSelfRepair = canSelfRepair
            )
        }
    }

    fun setRepairing(isRepairing: Boolean) {
        _uiState.update { it.copy(isRepairing = isRepairing) }
    }

    fun onTestInputChanged(value: String) {
        _uiState.update { it.copy(testInput = value) }
    }

    fun onProviderSelected(provider: ProviderId) {
        viewModelScope.launch { settings.setActiveProvider(provider) }
    }

    fun onKeyOverrideChanged(provider: ProviderId, key: String) {
        _uiState.update { state ->
            state.copy(keyOverrides = state.keyOverrides + (provider to key))
        }
        viewModelScope.launch { settings.setKeyOverride(provider, key) }
    }

    fun paraphraseTestInput() {
        if (_uiState.value.isParaphrasing) return
        val input = _uiState.value.testInput.trim()
        if (input.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Type something to rewrite first") }
            return
        }

        _uiState.update {
            it.copy(
                isParaphrasing = true,
                errorMessage = null,
                suggestions = emptyList(),
                answeredBy = null,
                usedFallback = false
            )
        }
        viewModelScope.launch {
            val result = repository.paraphrase(input)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        state.copy(
                            isParaphrasing = false,
                            suggestions = outcome.suggestions,
                            answeredBy = outcome.provider,
                            usedFallback = outcome.usedFallback
                        )
                    },
                    onFailure = {
                        state.copy(
                            isParaphrasing = false,
                            errorMessage = it.message ?: "Request failed"
                        )
                    }
                )
            }
        }
    }
}
