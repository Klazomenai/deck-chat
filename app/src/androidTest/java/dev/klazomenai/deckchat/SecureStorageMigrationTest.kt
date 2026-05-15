package dev.klazomenai.deckchat

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end instrumented migration tests for [MigrationGate] + [TinkAeadPrefs].
 *
 * Requires a real device or emulator because both [EncryptedSharedPreferences] (the old
 * store being drained) and [TinkAeadPrefs] (the new store being populated) require a
 * real AndroidKeyStore.
 *
 * Acceptance criteria covered:
 *   AC-#127.1 → [migration_writesNewStore_andDeletesOldFile_andPreservesKeystoreAlias]
 *   AC-#127.3 → [migration_isNoOp_onSecondBoot]
 *   AC-#127.5 → [migration_recoversFromCrashBeforeOldFileDeletion]
 *
 * Issue #214 (instrumented migration test) is closed by these three tests.
 */
@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class SecureStorageMigrationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteSharedPreferences("deckchat_prefs")
        context.deleteSharedPreferences("deckchat_secure_v2")
        context.deleteSharedPreferences("deckchat_aead_keyset")
        context.deleteSharedPreferences("migration_test_temp")
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias("deckchat_aead_master")) ks.deleteEntry("deckchat_aead_master")
        if (ks.containsAlias("deckchat_token_key")) ks.deleteEntry("deckchat_token_key")
    }

    @After
    fun tearDown() = setUp()

    private fun populateOldStore(vararg pairs: Pair<String, String>) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val editor = EncryptedSharedPreferences.create(
            context,
            "deckchat_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).edit()
        for ((key, value) in pairs) editor.putString(key, value)
        editor.commit()
    }

    private fun oldPrefsEmpty(): Boolean =
        context.getSharedPreferences("deckchat_prefs", Context.MODE_PRIVATE).all.isEmpty()

    // ── AC-#127.1: Happy path ─────────────────────────────────────────────────

    @Test
    fun migration_writesNewStore_andDeletesOldFile_andPreservesKeystoreAlias() = runBlocking {
        populateOldStore(
            "homeserver_url" to "https://matrix.example.com",
            "user_id" to "@captain:example.com",
        )
        // Pre-create the token Keystore alias so we can verify migration does not delete it
        val tempPrefs = context.getSharedPreferences("migration_test_temp", Context.MODE_PRIVATE)
        SecureStorage.KeystoreTokenEncryptor().encrypt(tempPrefs, "test", "value")

        MigrationGate.migrateIfNeeded(context)

        val newPrefs = TinkAeadPrefs(context)
        assertEquals("https://matrix.example.com", newPrefs.getString("homeserver_url", null))
        assertEquals("@captain:example.com", newPrefs.getString("user_id", null))
        assertTrue("Old prefs must be empty after migration", oldPrefsEmpty())
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue("deckchat_token_key must survive migration", ks.containsAlias("deckchat_token_key"))
    }

    // ── AC-#127.3: Second-boot no-op ─────────────────────────────────────────

    @Test
    fun migration_isNoOp_onSecondBoot() = runBlocking {
        populateOldStore("homeserver_url" to "https://matrix.example.com")

        MigrationGate.migrateIfNeeded(context)  // first boot
        MigrationGate.migrateIfNeeded(context)  // second boot — sentinel present, must be no-op

        assertEquals(
            "Data must survive second-boot no-op",
            "https://matrix.example.com",
            TinkAeadPrefs(context).getString("homeserver_url", null),
        )
        assertTrue("Old prefs must remain empty after second-boot no-op", oldPrefsEmpty())
    }

    // ── AC-#127.5: Crash-mid-migration recovery ───────────────────────────────

    @Test
    fun migration_recoversFromCrashBeforeOldFileDeletion() = runBlocking {
        // Simulate the pre-crash state: commit succeeded (data + sentinel in new store)
        // but the process was killed before step 3 (old file not yet deleted).
        populateOldStore("homeserver_url" to "https://matrix.example.com")
        TinkAeadPrefs(context).edit()
            .putString("homeserver_url", "https://matrix.example.com")
            .putBoolean("__migrated_from_esp_v1__", true)
            .commit()

        // Gate sees sentinel → runs step-3 recovery only (delete old file)
        MigrationGate.migrateIfNeeded(context)

        assertTrue("Old prefs must be deleted on step-3 recovery", oldPrefsEmpty())
        assertEquals(
            "New store data must be intact after recovery",
            "https://matrix.example.com",
            TinkAeadPrefs(context).getString("homeserver_url", null),
        )
    }
}
