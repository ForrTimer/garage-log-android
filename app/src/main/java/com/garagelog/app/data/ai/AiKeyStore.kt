package com.garagelog.app.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the user's Anthropic API key, encrypted with an AES-GCM key that lives in the Android
 * Keystore (it never leaves the device's secure hardware); only the ciphertext is in
 * SharedPreferences.
 *
 * Deliberately kept out of Room: every Room table is swept into the JSON backup export and the
 * sync, so a key stored there would ride along into files meant to be shared or restored onto
 * another device. This stays on the one device it was typed into — and if Android's own backup
 * restores the ciphertext onto a new phone, the Keystore key isn't there to open it, so it reads
 * as "no key set" rather than failing.
 *
 * Replaces androidx.security's EncryptedSharedPreferences, which Google deprecated with no further
 * releases. [migrateFromEncryptedPrefs] moves a key saved by the old store once, then deletes it.
 */
class AiKeyStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateFromEncryptedPrefs()
    }

    private val _hasKey = MutableStateFlow(apiKey().isNullOrBlank().not())
    val hasKey: StateFlow<Boolean> = _hasKey

    fun apiKey(): String? {
        val stored = prefs.getString(KEY_API, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun setApiKey(value: String) {
        val trimmed = value.trim()
        prefs.edit { putString(KEY_API, encrypt(trimmed)) }
        _hasKey.value = trimmed.isNotEmpty()
    }

    fun clear() {
        prefs.edit { remove(KEY_API) }
        _hasKey.value = false
    }

    /** Last 4 characters only — enough to tell two keys apart without showing the secret. */
    fun keyHint(): String? = apiKey()?.takeIf { it.length > 4 }?.let { "…${it.takeLast(4)}" }

    // ---- AES-256-GCM, a fresh 12-byte IV per write, stored as base64(iv + ciphertext) ----

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    /**
     * One-time move from the deprecated EncryptedSharedPreferences file. The old file is deleted
     * only after it was read successfully, so a failed read leaves it for the next launch. Remove
     * this (and the security-crypto dependency) once every install has run a build that has it.
     */
    @Suppress("DEPRECATION")
    private fun migrateFromEncryptedPrefs() {
        if (prefs.contains(KEY_API)) return
        val legacyFile = appContext.dataDir.resolve("shared_prefs/$LEGACY_PREFS_NAME.xml")
        if (!legacyFile.exists()) return
        val moved = runCatching {
            val legacy = EncryptedSharedPreferences.create(
                appContext,
                LEGACY_PREFS_NAME,
                MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val key = legacy.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
            if (key != null) {
                prefs.edit(commit = true) { putString(KEY_API, encrypt(key)) }
                check(apiKey() == key) { "Migrated key didn't read back." }
            }
        }
        if (moved.isSuccess) {
            appContext.deleteSharedPreferences(LEGACY_PREFS_NAME)
        } else {
            // Don't leave a half-written copy that would stop the next launch from retrying.
            prefs.edit(commit = true) { remove(KEY_API) }
        }
    }

    private companion object {
        const val PREFS_NAME = "garage_log_secrets"
        const val LEGACY_PREFS_NAME = "garage_log_ai"
        const val KEY_API = "anthropic_api_key"
        const val KEY_ALIAS = "garage_log_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
