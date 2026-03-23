package dev.klazomenai.deckchat

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests [SecureStorage] with plain SharedPreferences and [PlaintextTokenEncryptor]
 * to bypass Android Keystore / EncryptedSharedPreferences (not available in JVM tests).
 * Keystore encryption is verified on-device in instrumented tests (issue #9).
 */
@RunWith(RobolectricTestRunner::class)
class SecureStorageTest {

    private fun createStorage(): SecureStorage {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return SecureStorage(prefs, SecureStorage.PlaintextTokenEncryptor())
    }

    @Test
    fun `homeserverUrl round-trips through storage`() {
        val storage = createStorage()
        assertNull(storage.homeserverUrl)
        storage.homeserverUrl = "https://matrix.example.com"
        assertEquals("https://matrix.example.com", storage.homeserverUrl)
    }

    @Test
    fun `hasSession returns false when no tokens stored`() {
        val storage = createStorage()
        assertFalse(storage.hasSession())
    }

    @Test
    fun `hasSession returns true when required fields present`() {
        val storage = createStorage()
        storage.homeserverUrl = "https://matrix.example.com"
        storage.userId = "@user:example.com"
        storage.accessToken = "token123"
        assertTrue(storage.hasSession())
    }

    @Test
    fun `hasSession returns false with only homeserver URL`() {
        val storage = createStorage()
        storage.homeserverUrl = "https://matrix.example.com"
        assertFalse(storage.hasSession())
    }

    @Test
    fun `clearSession removes session fields but keeps homeserver URL`() {
        val storage = createStorage()
        storage.homeserverUrl = "https://matrix.example.com"
        storage.userId = "@user:example.com"
        storage.accessToken = "token123"
        storage.refreshToken = "refresh123"
        storage.sqlitePassphrase = "passphrase"

        storage.clearSession()

        assertEquals("https://matrix.example.com", storage.homeserverUrl)
        assertNull(storage.userId)
        assertNull(storage.accessToken)
        assertNull(storage.refreshToken)
        assertNull(storage.sqlitePassphrase)
        assertFalse(storage.hasSession())
    }

    @Test
    fun `accessToken round-trips`() {
        val storage = createStorage()
        storage.accessToken = "syt_secret_token_value"
        assertEquals("syt_secret_token_value", storage.accessToken)
    }

    @Test
    fun `refreshToken round-trips`() {
        val storage = createStorage()
        storage.refreshToken = "refresh_token_value"
        assertEquals("refresh_token_value", storage.refreshToken)
    }

    @Test
    fun `sqlitePassphrase round-trips`() {
        val storage = createStorage()
        storage.sqlitePassphrase = "passphrase123"
        assertEquals("passphrase123", storage.sqlitePassphrase)
    }

    @Test
    fun `setting token to null removes it`() {
        val storage = createStorage()
        storage.accessToken = "token"
        assertEquals("token", storage.accessToken)
        storage.accessToken = null
        assertNull(storage.accessToken)
    }

    @Test
    fun `userId and deviceId round-trip`() {
        val storage = createStorage()
        storage.userId = "@deckchat:example.com"
        storage.deviceId = "ABCDEF"
        assertEquals("@deckchat:example.com", storage.userId)
        assertEquals("ABCDEF", storage.deviceId)
    }
}
