package dev.klazomenai.deckchat

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [TinkAeadPrefs] using the production
 * [com.google.crypto.tink.integration.android.AndroidKeysetManager] constructor.
 *
 * Covers keyset persistence across instances, Keystore master-key wrap, and graceful
 * degradation after keyset loss — properties that cannot be verified with the
 * injectable-primitives constructor used by [TinkAeadPrefsTest].
 *
 * Run under both `:connectedDebugAndroidTest` and `:connectedReleaseAndroidTest` (the CI
 * job added by issue #227) to catch R8 stripping of Tink protobuf reflection (B5 risk).
 */
@RunWith(AndroidJUnit4::class)
class TinkAeadPrefsKeystoreTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteSharedPreferences("deckchat_secure_v2")
        context.deleteSharedPreferences("deckchat_aead_keyset")
        deleteKeystoreEntry("deckchat_aead_master")
    }

    @After
    fun tearDown() = setUp()

    private fun deleteKeystoreEntry(alias: String) {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    @Test
    fun keyset_persistsAcrossInstances() {
        TinkAeadPrefs(context).edit().putString("token", "secret-value").commit()
        assertEquals("secret-value", TinkAeadPrefs(context).getString("token", null))
    }

    @Test
    fun keyset_newInstance_createsSuccessfully_afterKeysetPrefsCleared() {
        val prefs1 = TinkAeadPrefs(context)
        prefs1.edit().putString("token", "old-value").commit()

        // Simulate keyset loss (e.g. Keystore eviction, factory reset clear)
        context.deleteSharedPreferences("deckchat_aead_keyset")

        // New instance must not crash — auto-generates a fresh keyset
        val prefs2 = TinkAeadPrefs(context)

        // Old ciphertext is unreadable with the new keyset → graceful degradation, not a crash
        assertNull("Old value must be unreadable after keyset loss", prefs2.getString("token", null))

        // New writes round-trip correctly with the new keyset
        prefs2.edit().putString("new_key", "new_value").commit()
        assertEquals("new_value", prefs2.getString("new_key", null))
    }

    @Test
    fun backing_prefs_contains_only_encrypted_values() {
        TinkAeadPrefs(context).edit()
            .putString("homeserver_url", "https://matrix.example.com")
            .commit()

        val raw = context.getSharedPreferences("deckchat_secure_v2", Context.MODE_PRIVATE)
        assertFalse("Plaintext key must not appear in backing store", raw.contains("homeserver_url"))
        assertFalse(
            "Plaintext value must not appear in any backing entry",
            raw.all.values.any { it == "https://matrix.example.com" },
        )
    }
}
