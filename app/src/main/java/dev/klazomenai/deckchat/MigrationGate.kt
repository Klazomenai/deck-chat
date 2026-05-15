package dev.klazomenai.deckchat

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One-time migration from the deprecated [EncryptedSharedPreferences] backing store
 * ("deckchat_prefs") to the new [TinkAeadPrefs] backing store ("deckchat_secure_v2").
 *
 * Called once per boot via `runBlocking { MigrationGate.migrateIfNeeded(context) }` from
 * [DeckChatApplication.onCreate] before any [SecureStorage] access. The [Mutex] prevents
 * a hypothetical multi-process re-entry from double-running the migration.
 *
 * Migration order (crash-safe):
 *   (1) read-all from old store
 *   (2) write-all + sentinel to new store — synchronous [commit] (fsync)
 *   (3) delete old prefs file
 *
 * - Killed between (1) and (2): sentinel absent → full re-run on next boot.
 * - Killed between (2) and (3): sentinel present → skip to (3) on next boot.
 * - Second boot (normal): sentinel present → idempotent delete + return.
 */
internal object MigrationGate {

    private val mutex = Mutex()
    private const val SENTINEL_KEY = "__migrated_from_esp_v1__"
    private const val OLD_PREFS_NAME = "deckchat_prefs"
    private const val TAG = "DeckChat.MigrationGate"

    suspend fun migrateIfNeeded(context: Context) {
        mutex.withLock {
            val newPrefs = TinkAeadPrefs(context)

            if (newPrefs.contains(SENTINEL_KEY)) {
                // Migration complete — clean up old file if crash left it behind (step 3 recovery)
                context.deleteSharedPreferences(OLD_PREFS_NAME)
                return
            }

            val oldData = readOldPrefs(context)
            if (oldData == null) {
                // Fresh install or unreadable old store — write sentinel and proceed
                newPrefs.edit().putBoolean(SENTINEL_KEY, true).commit()
                return
            }

            // (2) write-all + sentinel atomically; commit() is synchronous so next read
            //     sees migrated data before this function returns
            val editor = newPrefs.edit()
            for ((key, value) in oldData) {
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value as MutableSet<String>)
                }
            }
            editor.putBoolean(SENTINEL_KEY, true)
            editor.commit()

            // (3) delete old prefs file — idempotent; crash here is recovered on next boot
            context.deleteSharedPreferences(OLD_PREFS_NAME)
        }
    }

    @Suppress("DEPRECATION")
    private fun readOldPrefs(context: Context): Map<String, *>? {
        // Quick pre-check: if the plain SharedPreferences (which backs ESP) has no entries,
        // there is nothing to migrate — skip ESP construction to avoid an unnecessary Keystore op.
        if (context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE).all.isEmpty()) {
            return null
        }
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                OLD_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ).all.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read old EncryptedSharedPreferences; treating as no-data", e)
            null
        }
    }
}
