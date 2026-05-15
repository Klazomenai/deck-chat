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
 * concurrent in-process callers (e.g. a hypothetical second thread reaching onCreate before
 * the first completes) from double-running the migration. It provides no cross-process
 * isolation — DeckChat has a single process so that is not a current concern.
 *
 * Migration order (crash-safe):
 *   (1) read-all from old store
 *   (2) write-all + sentinel to new store — synchronous [commit] (fsync)
 *   (3) delete old prefs file
 *
 * - Killed between (1) and (2): sentinel absent → full re-run on next boot.
 * - Killed between (2) and (3): sentinel present → skip to (3) on next boot.
 * - Second boot (normal): sentinel present → idempotent delete + return.
 * - [readOldPrefs] throws (transient Keystore/IO error): no sentinel written → retry next boot.
 * - [commit] fails (disk full): old prefs preserved; sentinel not written → retry next boot.
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

            // readOldPrefs returns null for "empty/absent" and throws for actual errors.
            // A throw here means no sentinel is written — next boot retries the migration.
            val oldData: Map<String, *>? = try {
                readOldPrefs(context)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read old store — will retry on next boot", e)
                return
            }

            if (oldData == null) {
                // Fresh install or empty old store — mark done and proceed
                if (!newPrefs.edit().putBoolean(SENTINEL_KEY, true).commit()) {
                    Log.e(TAG, "Sentinel write failed for empty/absent old store — will retry on next boot")
                }
                return
            }

            // (2) write-all + sentinel; commit() is synchronous so next read sees migrated data.
            // Skip keys already present — if migration failed on a prior boot and the user
            // re-authenticated in the meantime, their new credentials take precedence over the
            // stale values in the old ESP store.
            val editor = newPrefs.edit()
            for ((key, value) in oldData) {
                if (newPrefs.contains(key)) continue
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, (value as Set<String>).toMutableSet())
                }
            }
            editor.putBoolean(SENTINEL_KEY, true)

            // (3) only delete old store if commit succeeds — preserve for retry on I/O failure
            if (editor.commit()) {
                context.deleteSharedPreferences(OLD_PREFS_NAME)
            } else {
                Log.e(TAG, "Migration commit failed — old prefs preserved for next boot retry")
            }
        }
    }

    /**
     * Returns the contents of the old ESP prefs, or null if the prefs are absent or empty.
     * Throws on any error opening or decrypting the old store — callers must not write the
     * sentinel in that case so the migration retries on next boot.
     */
    @Suppress("DEPRECATION")
    private fun readOldPrefs(context: Context): Map<String, *>? {
        // Quick pre-check: if the plain SharedPreferences (which backs ESP) has no entries,
        // there is nothing to migrate — skip ESP construction to avoid an unnecessary Keystore op.
        if (context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE).all.isEmpty()) {
            return null
        }
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            OLD_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).all.takeIf { it.isNotEmpty() }
    }
}
