package dev.klazomenai.deckchat

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import org.json.JSONArray

/**
 * [SharedPreferences] implementation that encrypts every key and value before writing
 * to [backing].
 *
 * Key encryption uses [DeterministicAead] (AES-SIV) so [contains] and in-place updates
 * work without an equality oracle on ciphertext. Value encryption uses [Aead] (AES-GCM)
 * with a fresh random IV per write.
 *
 * Values are serialised as `"<tag>|<encoded>"` before encryption so the type survives
 * the round-trip:
 * - String → `s|<raw>`
 * - Int    → `i|<decimal>`
 * - Long   → `l|<decimal>`
 * - Float  → `f|<decimal>`
 * - Boolean → `b|1` or `b|0`
 * - StringSet → `ss|<JSON array>`
 *
 * [aad] binds ciphertexts to the app's package identity. Production uses [Context.packageName]
 * so a future package rename intentionally invalidates stored data (see #228 runbook).
 * Per-installation uniqueness comes from the Tink keyset, generated fresh per install and
 * wrapped by the Android Keystore master key — not from the AAD value itself.
 *
 * The injectable-primitives primary constructor is the test seam used by [TinkAeadPrefsTest].
 * Production code uses [TinkAeadPrefs(context: Context)].
 */
class TinkAeadPrefs(
    private val backing: SharedPreferences,
    private val aead: Aead,
    private val daead: DeterministicAead,
    aad: ByteArray,
) : SharedPreferences {

    private val aad: ByteArray = aad.copyOf()

    constructor(context: Context) : this(
        backing = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        aead = buildAead(context),
        daead = buildDaead(context),
        aad = context.packageName.toByteArray(Charsets.UTF_8),
    )

    // ── Key / value helpers ──────────────────────────────────────────────────

    private fun encKey(key: String): String =
        Base64.encodeToString(
            daead.encryptDeterministically(key.toByteArray(Charsets.UTF_8), aad),
            Base64.NO_WRAP,
        )

    private fun encVal(tag: String, encoded: String): String =
        Base64.encodeToString(
            aead.encrypt("$tag|$encoded".toByteArray(Charsets.UTF_8), aad),
            Base64.NO_WRAP,
        )

    private fun decVal(raw: String): Pair<String, String>? = try {
        val plain = String(aead.decrypt(Base64.decode(raw, Base64.NO_WRAP), aad), Charsets.UTF_8)
        val idx = plain.indexOf('|')
        if (idx < 0) null else plain.substring(0, idx) to plain.substring(idx + 1)
    } catch (_: Exception) { null }

    // ── SharedPreferences ────────────────────────────────────────────────────

    override fun contains(key: String): Boolean = backing.contains(encKey(key))

    override fun getString(key: String, defValue: String?): String? {
        val raw = backing.getString(encKey(key), null) ?: return defValue
        val (tag, encoded) = decVal(raw) ?: return defValue
        return if (tag == "s") encoded else defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val raw = backing.getString(encKey(key), null) ?: return defValues
        val (tag, encoded) = decVal(raw) ?: return defValues
        if (tag != "ss") return defValues
        return try {
            val arr = JSONArray(encoded)
            (0 until arr.length()).mapTo(LinkedHashSet()) { arr.getString(it) }
        } catch (_: Exception) { defValues }
    }

    override fun getInt(key: String, defValue: Int): Int {
        val raw = backing.getString(encKey(key), null) ?: return defValue
        val (tag, encoded) = decVal(raw) ?: return defValue
        return if (tag == "i") encoded.toIntOrNull() ?: defValue else defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        val raw = backing.getString(encKey(key), null) ?: return defValue
        val (tag, encoded) = decVal(raw) ?: return defValue
        return if (tag == "l") encoded.toLongOrNull() ?: defValue else defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val raw = backing.getString(encKey(key), null) ?: return defValue
        val (tag, encoded) = decVal(raw) ?: return defValue
        return if (tag == "f") encoded.toFloatOrNull() ?: defValue else defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val raw = backing.getString(encKey(key), null) ?: return defValue
        val (tag, encoded) = decVal(raw) ?: return defValue
        return if (tag == "b") when (encoded) { "1" -> true; "0" -> false; else -> defValue } else defValue
    }

    // getAll() cannot be implemented without iterating all backing entries and decrypting each
    // key. No production code in this app enumerates prefs; callers must use typed getters.
    override fun getAll(): Map<String, *> = throw UnsupportedOperationException(
        "TinkAeadPrefs.getAll() is not supported — use typed getters (getString, getInt, …)"
    )

    override fun edit(): SharedPreferences.Editor = EncryptedEditor()

    // Listener callbacks receive encrypted key names, not plaintext keys, which makes them
    // unusable. No production code in this app registers listeners on TinkAeadPrefs.
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ): Unit = throw UnsupportedOperationException(
        "TinkAeadPrefs.registerOnSharedPreferenceChangeListener() is not supported"
    )

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ): Unit = throw UnsupportedOperationException(
        "TinkAeadPrefs.unregisterOnSharedPreferenceChangeListener() is not supported"
    )

    // ── Editor ───────────────────────────────────────────────────────────────

    private inner class EncryptedEditor : SharedPreferences.Editor {
        private val delegate = backing.edit()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) delegate.remove(encKey(key))
            else delegate.putString(encKey(key), encVal("s", value))
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            if (values == null) delegate.remove(encKey(key))
            else delegate.putString(encKey(key), encVal("ss", JSONArray(values.toList()).toString()))
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            delegate.putString(encKey(key), encVal("i", value.toString()))
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            delegate.putString(encKey(key), encVal("l", value.toString()))
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            delegate.putString(encKey(key), encVal("f", value.toString()))
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            delegate.putString(encKey(key), encVal("b", if (value) "1" else "0"))
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            delegate.remove(encKey(key))
            return this
        }

        override fun clear(): SharedPreferences.Editor { delegate.clear(); return this }
        override fun commit(): Boolean = delegate.commit()
        override fun apply() = delegate.apply()
    }

    companion object {
        internal const val PREFS_NAME = "deckchat_secure_v2"
        private const val KEYSET_PREFS_NAME = "deckchat_aead_keyset"
        private const val AEAD_KEY_URI = "android-keystore://deckchat_aead_master"

        internal fun buildAead(context: Context): Aead {
            AeadConfig.register()
            return AndroidKeysetManager.Builder()
                .withSharedPref(context, "aead_keyset", KEYSET_PREFS_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(AEAD_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        }

        internal fun buildDaead(context: Context): DeterministicAead {
            DeterministicAeadConfig.register()
            return AndroidKeysetManager.Builder()
                .withSharedPref(context, "daead_keyset", KEYSET_PREFS_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_SIV"))
                .withMasterKeyUri(AEAD_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(DeterministicAead::class.java)
        }
    }
}
