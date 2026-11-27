package com.fashionstudio.aipro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionstudio.aipro.core.ai.AIProviderInterface
import com.fashionstudio.aipro.core.ai.ConnectionTestResult
import com.fashionstudio.aipro.core.database.UserPreferencesRepository
import com.fashionstudio.aipro.core.database.UserSettings
import com.fashionstudio.aipro.core.database.dao.ApiUsageDao
import com.fashionstudio.aipro.core.database.dao.ProjectDao
import com.fashionstudio.aipro.core.database.entity.ApiUsageLogEntity
import com.fashionstudio.aipro.core.security.ApiKeyVault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val maskedKey: String = "",
    val hasStoredKey: Boolean = false,
    val connectionStatus: ConnectionTestResult? = null,
    val isTestingConnection: Boolean = false,
    val monthlySpentUsd: Double = 0.0,
    val recentLogs: List<ApiUsageLogEntity> = emptyList(),
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val apiKeyVault: ApiKeyVault,
    private val aiProvider: AIProviderInterface,
    private val apiUsageDao: ApiUsageDao,
    private val projectDao: ProjectDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        viewModelScope.launch {
            combine(
                preferences.settingsFlow,
                apiUsageDao.observeSpentSince(thirtyDaysAgo),
                apiUsageDao.observeRecentLogs()
            ) { settings, spent, logs ->
                Triple(settings, spent, logs)
            }.collect { (settings, spent, logs) ->
                val raw = apiKeyVault.getApiKey(settings.activeProviderId)
                _uiState.update {
                    it.copy(
                        settings = settings,
                        hasStoredKey = !raw.isNullOrBlank(),
                        maskedKey = apiKeyVault.maskKeyForDisplay(raw),
                        monthlySpentUsd = spent,
                        recentLogs = logs
                    )
                }
            }
        }
    }

    fun saveApiKey(rawKey: String) {
        viewModelScope.launch {
            if (rawKey.isBlank()) return@launch
            val providerId = _uiState.value.settings.activeProviderId
            apiKeyVault.saveApiKey(providerId, rawKey)
            _uiState.update {
                it.copy(
                    hasStoredKey = true,
                    maskedKey = apiKeyVault.maskKeyForDisplay(rawKey),
                    statusMessage = "API key encrypted in Android Keystore."
                )
            }
        }
    }

    fun deleteApiKey() {
        viewModelScope.launch {
            apiKeyVault.deleteApiKey(_uiState.value.settings.activeProviderId)
            _uiState.update {
                it.copy(
                    hasStoredKey = false,
                    maskedKey = "",
                    connectionStatus = null,
                    statusMessage = "API key removed from device."
                )
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, statusMessage = null) }
            val key = apiKeyVault.getApiKey(_uiState.value.settings.activeProviderId).orEmpty()
            val result = aiProvider.testConnection(key)
            _uiState.update { it.copy(isTestingConnection = false, connectionStatus = result) }
        }
    }

    fun updateModels(analysis: String, generation: String, fallback: String, quality: String) {
        viewModelScope.launch { preferences.updateModels(analysis, generation, fallback, quality) }
    }

    fun updateResolutionTier(tier: String) {
        viewModelScope.launch { preferences.updateResolutionTier(tier) }
    }

    fun updateBudgetLimit(limitUsd: Double) {
        viewModelScope.launch { preferences.updateMonthlyBudget(limitUsd) }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferences.updateDarkTheme(enabled) }
    }

    fun updateLanguage(code: String) {
        viewModelScope.launch { preferences.updateLanguage(code) }
    }

    fun wipeAllLocalData() {
        viewModelScope.launch {
            apiKeyVault.clearAllKeys()
            projectDao.deleteAll()
            apiUsageDao.clearAll()
            _uiState.update { it.copy(statusMessage = "All local projects, logs, and encrypted keys erased.") }
        }
    }
}
