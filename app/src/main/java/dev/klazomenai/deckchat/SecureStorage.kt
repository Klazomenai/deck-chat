package dev.klazomenai.deckchat

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure storage for DeckChat configuration and credentials.
 *
 * - General config (homeserver URL, user ID): [EncryptedSharedPreferences]
 *   with AES256-SIV key encryption and AES256-GCM value encryption.
 * - Sensitive tokens (Matrix session): AES-GCM encryption via Android Keystore
 *   with StrongBox fallback. Ciphertext + IV stored in the encrypted prefs.
 *
 * No hardcoded URLs or credentials — all configured at runtime via SettingsActivity.
 *
 * @param prefs injectable SharedPreferences — production uses EncryptedSharedPreferences,
 *   tests can inject a plain SharedPreferences.
 * @param tokenEncryptor injectable encryption strategy — production uses Android Keystore,
 *   tests can use a passthrough.
 */
class SecureStorage(
    private val prefs: SharedPreferences,
    private val tokenEncryptor: TokenEncryptor = KeystoreTokenEncryptor(),
) {

    constructor(context: Context) : this(
        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ),
    )

    // --- General config ---

    var homeserverUrl: String?
        get() = prefs.getString(KEY_HOMESERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_HOMESERVER_URL, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var slidingSyncVersion: String?
        get() = prefs.getString(KEY_SLIDING_SYNC_VERSION, null)
        set(value) = prefs.edit().putString(KEY_SLIDING_SYNC_VERSION, value).apply()

    var roomId: String?
        get() = prefs.getString(KEY_ROOM_ID, null)
        set(value) = prefs.edit().putString(KEY_ROOM_ID, value).apply()

    var voiceProfile: String?
        get() = prefs.getString(KEY_VOICE_PROFILE, null)
        set(value) = prefs.edit().putString(KEY_VOICE_PROFILE, value).apply()

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    // --- Sensitive tokens (encrypted via TokenEncryptor) ---

    var accessToken: String?
        get() = tokenEncryptor.decrypt(prefs, KEY_ACCESS_TOKEN)
        set(value) = tokenEncryptor.encrypt(prefs, KEY_ACCESS_TOKEN, value)

    var refreshToken: String?
        get() = tokenEncryptor.decrypt(prefs, KEY_REFRESH_TOKEN)
        set(value) = tokenEncryptor.encrypt(prefs, KEY_REFRESH_TOKEN, value)

    var sqlitePassphrase: String?
        get() = tokenEncryptor.decrypt(prefs, KEY_SQLITE_PASSPHRASE)
        set(value) = tokenEncryptor.encrypt(prefs, KEY_SQLITE_PASSPHRASE, value)

    fun hasSession(): Boolean {
        return accessToken != null && homeserverUrl != null && userId != null
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove("${KEY_ACCESS_TOKEN}_iv")
            .remove(KEY_REFRESH_TOKEN)
            .remove("${KEY_REFRESH_TOKEN}_iv")
            .remove(KEY_SQLITE_PASSPHRASE)
            .remove("${KEY_SQLITE_PASSPHRASE}_iv")
            .remove(KEY_USER_ID)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_SLIDING_SYNC_VERSION)
            .apply()
    }

    /**
     * Encryption strategy for sensitive tokens. Production uses Android Keystore,
     * tests inject a passthrough.
     */
    interface TokenEncryptor {
        fun encrypt(prefs: SharedPreferences, key: String, value: String?)
        fun decrypt(prefs: SharedPreferences, key: String): String?
    }

    /**
     * Passthrough encryptor for JVM tests — stores values as plain strings.
     * NOT for production use.
     */
    class PlaintextTokenEncryptor : TokenEncryptor {
        override fun encrypt(prefs: SharedPreferences, key: String, value: String?) {
            if (value == null) {
                prefs.edit().remove(key).apply()
            } else {
                prefs.edit().putString(key, value).apply()
            }
        }

        override fun decrypt(prefs: SharedPreferences, key: String): String? {
            return prefs.getString(key, null)
        }
    }

    /**
     * Production encryptor using Android Keystore AES-GCM with StrongBox fallback.
     */
    class KeystoreTokenEncryptor : TokenEncryptor {

        override fun encrypt(prefs: SharedPreferences, key: String, value: String?) {
            if (value == null) {
                prefs.edit().remove(key).remove("${key}_iv").apply()
                return
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(key, android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP))
                .putString("${key}_iv", android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
                .apply()
        }

        override fun decrypt(prefs: SharedPreferences, key: String): String? {
            val encoded = prefs.getString(key, null) ?: return null
            val ivEncoded = prefs.getString("${key}_iv", null) ?: return null
            return try {
                val ciphertext = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
                val iv = android.util.Base64.decode(ivEncoded, android.util.Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (_: Exception) {
                prefs.edit().remove(key).remove("${key}_iv").apply()
                null
            }
        }

        private fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            return try {
                kg.init(buildKeySpec(strongBox = true))
                kg.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                kg.init(buildKeySpec(strongBox = false))
                kg.generateKey()
            }
        }

        private fun buildKeySpec(strongBox: Boolean): KeyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply { if (strongBox) setIsStrongBoxBacked(true) }
                .build()
    }

    companion object {
        private const val PREFS_NAME = "deckchat_prefs"
        private const val KEYSTORE_ALIAS = "deckchat_token_key"
        private const val KEY_HOMESERVER_URL = "homeserver_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SLIDING_SYNC_VERSION = "sliding_sync_version"
        private const val KEY_ROOM_ID = "room_id"
        private const val KEY_VOICE_PROFILE = "voice_profile"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_SQLITE_PASSPHRASE = "sqlite_passphrase"
    }
}
