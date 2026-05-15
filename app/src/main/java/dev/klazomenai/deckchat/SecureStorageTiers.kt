package dev.klazomenai.deckchat

/**
 * DeckChat secret-tier hierarchy.
 *
 * Three tiers of increasing sensitivity, each with its own encryption boundary:
 *
 * ## Tier 0 — General config
 *
 * Fields: `homeserverUrl`, `userId`, `deviceId`, `slidingSyncVersion`, `roomId`,
 * `voiceProfile`, `onboardingComplete`, `debugMode`, `showTimings`, `responseTimeoutSec`.
 *
 * Storage: [TinkAeadPrefs] — key names encrypted with [DeterministicAead] (AES-SIV);
 * values encrypted with [Aead] (AES-GCM, random IV per write). AAD = package name,
 * binding ciphertexts to this app. Master keyset wrapped by Android Keystore alias
 * `deckchat_aead_master`.
 *
 * ## Tier 1 — Sensitive tokens
 *
 * Fields: `accessToken`, `refreshToken`, `sqlitePassphrase`.
 *
 * Storage: double-encrypted. Inner layer: raw AES-GCM via [SecureStorage.KeystoreTokenEncryptor]
 * using Keystore alias `deckchat_token_key`. Outer layer: stored within [TinkAeadPrefs]
 * (Tier 0 envelope). The inner encryption is defence-in-depth; the outer layer provides
 * key-name confidentiality and value authentication.
 *
 * See F2 in the sub-plan for a future unification onto Tink for the inner layer too.
 *
 * ## Tier 2 — E2EE session keys (future)
 *
 * Fields: matrix-rust-sdk session keys, recovery key, cross-signing material.
 *
 * Storage: TBD — see #21 (E2EE key backup) for the planned recovery-key flow. This tier
 * will likely expose a `StateFlow<RecoveryKeyState>` as the first async-shaped accessor
 * in [SecureStorage] rather than forcing the sync callers to migrate (see F3).
 *
 * ---
 *
 * ### AAD invariant
 *
 * All Tier 0 and Tier 1 ciphertexts include `context.packageName` as Additional
 * Authenticated Data (AAD). A package rename — even with the same signing key — will
 * invalidate all stored ciphertexts and require a re-onboarding release. See #228 for
 * the planned runbook.
 *
 * ### Downgrade invariant
 *
 * Once an install has completed the [MigrationGate] migration to [TinkAeadPrefs], a
 * downgrade to an APK that still uses [EncryptedSharedPreferences] will find the new
 * backing file (`deckchat_secure_v2`) and silently lose all stored config (the old APK
 * will not be able to decrypt it). APK downgrade is a one-way operation. Document this
 * in release notes.
 */
internal object SecureStorageTiers
