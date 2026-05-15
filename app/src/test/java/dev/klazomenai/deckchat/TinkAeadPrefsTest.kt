package dev.klazomenai.deckchat

import android.content.Context
import android.content.SharedPreferences
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [TinkAeadPrefs] using the injectable-primitives constructor.
 *
 * Avoids [com.google.crypto.tink.integration.android.AndroidKeysetManager] so these
 * tests run on the JVM without a real AndroidKeyStore.  The production
 * [AndroidKeysetManager] + Keystore-wrap path is covered by [TinkAeadPrefsKeystoreTest]
 * (instrumented, real device/emulator).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TinkAeadPrefsTest {

    private lateinit var aead: Aead
    private lateinit var daead: DeterministicAead
    private lateinit var backing: SharedPreferences
    private lateinit var prefs: TinkAeadPrefs
    private val aad = "dev.klazomenai.deckchat.test".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        AeadConfig.register()
        DeterministicAeadConfig.register()
        aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(Aead::class.java)
        daead = KeysetHandle.generateNew(KeyTemplates.get("AES256_SIV"))
            .getPrimitive(DeterministicAead::class.java)
        backing = RuntimeEnvironment.getApplication()
            .getSharedPreferences("tink_test_prefs", Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        prefs = TinkAeadPrefs(backing, aead, daead, aad)
    }

    // ── Round-trips ──────────────────────────────────────────────────────────

    @Test
    fun `string round-trips through encryption`() {
        prefs.edit().putString("k", "hello world").commit()
        assertEquals("hello world", prefs.getString("k", null))
    }

    @Test
    fun `int round-trips through encryption`() {
        prefs.edit().putInt("k", 42).commit()
        assertEquals(42, prefs.getInt("k", 0))
    }

    @Test
    fun `long round-trips through encryption`() {
        prefs.edit().putLong("k", Long.MAX_VALUE).commit()
        assertEquals(Long.MAX_VALUE, prefs.getLong("k", 0L))
    }

    @Test
    fun `boolean true round-trips through encryption`() {
        prefs.edit().putBoolean("k", true).commit()
        assertTrue(prefs.getBoolean("k", false))
    }

    @Test
    fun `boolean false round-trips through encryption`() {
        prefs.edit().putBoolean("k", false).commit()
        assertFalse(prefs.getBoolean("k", true))
    }

    @Test
    fun `float round-trips through encryption`() {
        prefs.edit().putFloat("k", 3.14f).commit()
        assertEquals(3.14f, prefs.getFloat("k", 0f), 0.0f)
    }

    @Test
    fun `stringSet round-trips through encryption`() {
        val values = mutableSetOf("alpha", "beta", "gamma")
        prefs.edit().putStringSet("k", values).commit()
        assertEquals(values, prefs.getStringSet("k", null))
    }

    @Test
    fun `stringSet preserves insertion order`() {
        val values = linkedSetOf("first", "second", "third")
        prefs.edit().putStringSet("k", values).commit()
        assertEquals(values.toList(), prefs.getStringSet("k", null)?.toList())
    }

    // ── Absent / null handling ───────────────────────────────────────────────

    @Test
    fun `getString returns null default when key absent`() {
        assertNull(prefs.getString("missing", null))
    }

    @Test
    fun `getString returns non-null default when key absent`() {
        assertEquals("fallback", prefs.getString("missing", "fallback"))
    }

    @Test
    fun `getInt returns default when key absent`() {
        assertEquals(-1, prefs.getInt("missing", -1))
    }

    @Test
    fun `getLong returns default when key absent`() {
        assertEquals(-1L, prefs.getLong("missing", -1L))
    }

    @Test
    fun `getBoolean returns true default when key absent`() {
        assertTrue(prefs.getBoolean("missing", true))
    }

    @Test
    fun `getBoolean returns false default when key absent`() {
        assertFalse(prefs.getBoolean("missing", false))
    }

    @Test
    fun `getFloat returns default when key absent`() {
        assertEquals(1.5f, prefs.getFloat("missing", 1.5f), 0.0f)
    }

    @Test
    fun `getStringSet returns null default when key absent`() {
        assertNull(prefs.getStringSet("missing", null))
    }

    // ── contains ─────────────────────────────────────────────────────────────

    @Test
    fun `contains returns false before write`() {
        assertFalse(prefs.contains("k"))
    }

    @Test
    fun `contains returns true after write`() {
        prefs.edit().putString("k", "v").commit()
        assertTrue(prefs.contains("k"))
    }

    // ── remove / putString null ───────────────────────────────────────────────

    @Test
    fun `remove deletes key`() {
        prefs.edit().putString("k", "v").commit()
        prefs.edit().remove("k").commit()
        assertNull(prefs.getString("k", null))
        assertFalse(prefs.contains("k"))
    }

    @Test
    fun `putString null removes the key`() {
        prefs.edit().putString("k", "v").commit()
        prefs.edit().putString("k", null).commit()
        assertFalse(prefs.contains("k"))
        assertNull(prefs.getString("k", null))
    }

    // ── Type-tag mismatch ────────────────────────────────────────────────────

    @Test
    fun `getInt returns default when key was written as String`() {
        prefs.edit().putString("k", "not-an-int").commit()
        assertEquals(-1, prefs.getInt("k", -1))
    }

    @Test
    fun `getString returns default when key was written as Int`() {
        prefs.edit().putInt("k", 42).commit()
        assertNull(prefs.getString("k", null))
    }

    // ── AAD mismatch ─────────────────────────────────────────────────────────

    @Test
    fun `data written with one AAD cannot be read with a different AAD`() {
        prefs.edit().putString("secret", "sensitive-value").commit()

        // Same backing, same primitives, different AAD — mimics a package rename
        val wrongAad = TinkAeadPrefs(backing, aead, daead, "other.package".toByteArray())

        // DeterministicAead encrypts the key under the wrong AAD → different backing key → not found
        assertFalse(wrongAad.contains("secret"))
        assertNull(wrongAad.getString("secret", null))
    }

    // ── Cross-instance reads ──────────────────────────────────────────────────

    @Test
    fun `second instance with same primitives reads data from first`() {
        prefs.edit().putString("k", "v").commit()
        val prefs2 = TinkAeadPrefs(backing, aead, daead, aad)
        assertEquals("v", prefs2.getString("k", null))
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test(expected = UnsupportedOperationException::class)
    fun `getAll throws UnsupportedOperationException`() {
        prefs.getAll()
    }

    // ── Backing store isolation ───────────────────────────────────────────────

    @Test
    fun `backing prefs contains no plaintext keys or values`() {
        prefs.edit().putString("homeserver_url", "https://matrix.example.com").commit()
        assertFalse("Plaintext key must not appear in backing store", backing.contains("homeserver_url"))
        assertFalse(
            "Plaintext value must not appear in any backing entry",
            backing.all.values.any { it == "https://matrix.example.com" },
        )
    }
}
