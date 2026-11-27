package com.fashionstudio.aipro.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure BYOK credential storage backed by Android Keystore AES-256-GCM.
 * The underlying SharedPreferences file is explicitly excluded in backup_rules.xml & data_extraction_rules.xml.
 */
@Singleton
class ApiKeyVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    companion object {
        private const val PREFS_NAME = "fs_secure_byok_vault"
        private const val KEY_PREFIX = "enc_api_key_"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun saveApiKey(providerId: String, rawApiKey: String) = withContext(Dispatchers.IO) {
        val trimmed = rawApiKey.trim()
        require(trimmed.isNotEmpty()) { "API key cannot be blank" }
        val encrypted = cryptoManager.encrypt(trimmed)
        prefs.edit().putString("$KEY_PREFIX$providerId", encrypted).apply()
    }

    suspend fun getApiKey(providerId: String): String? = withContext(Dispatchers.IO) {
        val encrypted = prefs.getString("$KEY_PREFIX$providerId", null) ?: return@withContext null
        cryptoManager.decrypt(encrypted)
    }

    suspend fun hasApiKey(providerId: String): Boolean = withContext(Dispatchers.IO) {
        !prefs.getString("$KEY_PREFIX$providerId", null).isNullOrBlank()
    }

    suspend fun deleteApiKey(providerId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove("$KEY_PREFIX$providerId").apply()
    }

    suspend fun clearAllKeys() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    /**
     * Masks an API key for UI display without exposing sensitive middle characters.
     */
    fun maskKeyForDisplay(rawKey: String?): String {
        if (rawKey.isNullOrBlank()) return ""
        if (rawKey.length <= 8) return "••••••••"
        return "${rawKey.take(4)}••••••••••••${rawKey.takeLast(4)}"
    }
}
