package com.fashionstudio.aipro.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "fs_user_settings")

data class UserSettings(
    val activeProviderId: String = "gemini",
    val analysisModelId: String = "gemini-3.8-flash",
    val generationModelId: String = "gemini-nano-banana-2.1",
    val fallbackGenerationModelId: String = "gemini-2.5-flash-image",
    val qualityAuditModelId: String = "gemini-3.5-flash-lite",
    val defaultResolutionTier: String = "2K",
    val monthlyBudgetLimitUsd: Double = 25.0,
    val darkThemeEnabled: Boolean = true,
    val languageCode: String = "ru",           // en, ru, ro
    val defaultExportFormat: String = "PNG",   // PNG (lossless default), JPG, WEBP
    val cloudProcessingConsentGiven: Boolean = false
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val ANALYSIS_MODEL = stringPreferencesKey("analysis_model")
        val GENERATION_MODEL = stringPreferencesKey("generation_model")
        val FALLBACK_MODEL = stringPreferencesKey("fallback_model")
        val QUALITY_MODEL = stringPreferencesKey("quality_model")
        val RESOLUTION_TIER = stringPreferencesKey("resolution_tier")
        val MONTHLY_BUDGET_USD = doublePreferencesKey("monthly_budget_usd")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
        val CLOUD_CONSENT = booleanPreferencesKey("cloud_consent")
    }

    val settingsFlow: Flow<UserSettings> = context.userSettingsDataStore.data.map { prefs ->
        UserSettings(
            activeProviderId = prefs[Keys.ACTIVE_PROVIDER] ?: "gemini",
            analysisModelId = prefs[Keys.ANALYSIS_MODEL] ?: "gemini-3.8-flash",
            generationModelId = prefs[Keys.GENERATION_MODEL] ?: "gemini-nano-banana-2.1",
            fallbackGenerationModelId = prefs[Keys.FALLBACK_MODEL] ?: "gemini-2.5-flash-image",
            qualityAuditModelId = prefs[Keys.QUALITY_MODEL] ?: "gemini-3.5-flash-lite",
            defaultResolutionTier = prefs[Keys.RESOLUTION_TIER] ?: "2K",
            monthlyBudgetLimitUsd = prefs[Keys.MONTHLY_BUDGET_USD] ?: 25.0,
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "ru",
            defaultExportFormat = prefs[Keys.EXPORT_FORMAT] ?: "PNG",
            cloudProcessingConsentGiven = prefs[Keys.CLOUD_CONSENT] ?: false
        )
    }

    suspend fun updateModels(analysis: String, generation: String, fallback: String, quality: String) {
        context.userSettingsDataStore.edit {
            it[Keys.ANALYSIS_MODEL] = analysis
            it[Keys.GENERATION_MODEL] = generation
            it[Keys.FALLBACK_MODEL] = fallback
            it[Keys.QUALITY_MODEL] = quality
        }
    }

    suspend fun updateResolutionTier(tier: String) {
        context.userSettingsDataStore.edit { it[Keys.RESOLUTION_TIER] = tier }
    }

    suspend fun updateMonthlyBudget(limitUsd: Double) {
        context.userSettingsDataStore.edit { it[Keys.MONTHLY_BUDGET_USD] = limitUsd.coerceAtLeast(0.0) }
    }

    suspend fun updateDarkTheme(enabled: Boolean) {
        context.userSettingsDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun updateLanguage(code: String) {
        context.userSettingsDataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun updateExportFormat(format: String) {
        context.userSettingsDataStore.edit { it[Keys.EXPORT_FORMAT] = format }
    }

    suspend fun setCloudConsent(given: Boolean) {
        context.userSettingsDataStore.edit { it[Keys.CLOUD_CONSENT] = given }
    }
}
