package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CrashDiagnosticEntry
import com.example.model.SoundProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.nordBudsDataStore: DataStore<Preferences> by preferencesDataStore(name = "nordbuds_prefs")

/**
 * Local persistence using Jetpack DataStore for user audio profiles/settings and
 * AndroidKeyStore AES-256-GCM encrypted file storage for crash diagnostics.
 */
class SecureStorageRepository(
    private val context: Context,
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val profilesAdapter = moshi.adapter<List<SoundProfile>>(
        Types.newParameterizedType(List::class.java, SoundProfile::class.java),
    )

    private val diagnosticsAdapter = moshi.adapter<List<CrashDiagnosticEntry>>(
        Types.newParameterizedType(List::class.java, CrashDiagnosticEntry::class.java),
    )

    private object Keys {
        val CUSTOM_PROFILES_JSON = stringPreferencesKey("custom_profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val DIAGNOSTICS_CONSENT = booleanPreferencesKey("diagnostics_consent")
        val UPDATE_MANIFEST_URL = stringPreferencesKey("update_manifest_url")
        val LAST_UPDATE_CHECK_MS = longPreferencesKey("last_update_check_ms")
    }

    val customProfilesFlow: Flow<List<SoundProfile>> = context.nordBudsDataStore.data.map { prefs ->
        val raw = prefs[Keys.CUSTOM_PROFILES_JSON] ?: return@map emptyList()
        runCatching { profilesAdapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }

    val activeProfileIdFlow: Flow<String> = context.nordBudsDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PROFILE_ID] ?: "balanced"
    }

    val diagnosticsConsentFlow: Flow<Boolean> = context.nordBudsDataStore.data.map { prefs ->
        prefs[Keys.DIAGNOSTICS_CONSENT] ?: false
    }

    val updateManifestUrlFlow: Flow<String> = context.nordBudsDataStore.data.map { prefs ->
        prefs[Keys.UPDATE_MANIFEST_URL] ?: DEFAULT_GITHUB_RELEASE_URL
    }

    val lastUpdateCheckFlow: Flow<Long> = context.nordBudsDataStore.data.map { prefs ->
        prefs[Keys.LAST_UPDATE_CHECK_MS] ?: 0L
    }

    suspend fun saveCustomProfiles(profiles: List<SoundProfile>) {
        val json = profilesAdapter.toJson(profiles)
        context.nordBudsDataStore.edit { it[Keys.CUSTOM_PROFILES_JSON] = json }
    }

    suspend fun setActiveProfileId(profileId: String) {
        context.nordBudsDataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = profileId }
    }

    suspend fun setDiagnosticsConsent(enabled: Boolean) {
        context.nordBudsDataStore.edit { it[Keys.DIAGNOSTICS_CONSENT] = enabled }
    }

    suspend fun isDiagnosticsConsentEnabledSync(): Boolean =
        runCatching { diagnosticsConsentFlow.first() }.getOrDefault(false)

    suspend fun setUpdateManifestUrl(url: String) {
        context.nordBudsDataStore.edit { it[Keys.UPDATE_MANIFEST_URL] = url.trim() }
    }

    suspend fun setLastUpdateCheckMillis(timestamp: Long) {
        context.nordBudsDataStore.edit { it[Keys.LAST_UPDATE_CHECK_MS] = timestamp }
    }

    fun saveEncryptedDiagnostics(entries: List<CrashDiagnosticEntry>) {
        val file = File(context.filesDir, DIAGNOSTICS_FILE_NAME)
        val payload = diagnosticsAdapter.toJson(entries).toByteArray(Charsets.UTF_8)
        val encrypted = encryptWithKeystore(payload)
        if (encrypted != null) {
            file.writeBytes(encrypted)
        } else {
            // In non-KeyStore JVM test environments only; on Android hardware KeyStore is available.
            file.writeBytes(payload)
        }
    }

    fun loadEncryptedDiagnostics(): List<CrashDiagnosticEntry> {
        val file = File(context.filesDir, DIAGNOSTICS_FILE_NAME)
        if (!file.exists()) return emptyList()
        val raw = file.readBytes()
        val decrypted = decryptWithKeystore(raw) ?: raw
        val json = decrypted.toString(Charsets.UTF_8)
        return runCatching { diagnosticsAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    fun clearDiagnostics() {
        val file = File(context.filesDir, DIAGNOSTICS_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun getOrCreateAesKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@runCatching existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }.getOrNull()

    private fun encryptWithKeystore(plainBytes: ByteArray): ByteArray? = runCatching {
        val key = getOrCreateAesKey() ?: return null
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainBytes)
        // Header: [1 byte magic 0xA5] [1 byte ivLen] [iv...] [ciphertext...]
        byteArrayOf(MAGIC_ENCRYPTED, iv.size.toByte()) + iv + cipherBytes
    }.getOrNull()

    private fun decryptWithKeystore(storedBytes: ByteArray): ByteArray? = runCatching {
        if (storedBytes.size < 4 || storedBytes[0] != MAGIC_ENCRYPTED) return null
        val ivLen = storedBytes[1].toInt() and 0xFF
        if (storedBytes.size <= 2 + ivLen) return null
        val iv = storedBytes.copyOfRange(2, 2 + ivLen)
        val cipherBytes = storedBytes.copyOfRange(2 + ivLen, storedBytes.size)
        val key = getOrCreateAesKey() ?: return null
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.doFinal(cipherBytes)
    }.getOrNull()

    companion object {
        const val DEFAULT_GITHUB_RELEASE_URL =
            "https://api.github.com/repos/nordbuds-companion/nordbuds-premium/releases/latest"
        private const val DIAGNOSTICS_FILE_NAME = "crash_diagnostics.enc"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nordbuds_diagnostics_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAGIC_ENCRYPTED: Byte = 0xA5.toByte()
    }
}
