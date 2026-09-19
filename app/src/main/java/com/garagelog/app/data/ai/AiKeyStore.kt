package com.garagelog.app.data.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the user's Anthropic API key.
 *
 * Deliberately kept out of Room: every Room table is swept into the JSON backup export and the
 * Drive sync snapshot, so a key stored there would ride along into files meant to be shared or
 * restored onto another device. This stays on the one device it was typed into.
 */
class AiKeyStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _hasKey = MutableStateFlow(apiKey().isNullOrBlank().not())
    val hasKey: StateFlow<Boolean> = _hasKey

    fun apiKey(): String? = prefs.getString(KEY_API, null)

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API, value.trim()).apply()
        _hasKey.value = value.isNotBlank()
    }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
        _hasKey.value = false
    }

    /** Last 4 characters only — enough to tell two keys apart without showing the secret. */
    fun keyHint(): String? = apiKey()?.takeIf { it.length > 4 }?.let { "…${it.takeLast(4)}" }

    private companion object {
        const val PREFS_NAME = "garage_log_ai"
        const val KEY_API = "anthropic_api_key"
    }
}
