# ProGuard rules applied ONLY to the releaseTest build type.
# The releaseTest build type inherits R8 minification from release so that
# instrumented tests run against an APK that is as close to production as
# possible. These keep rules exist purely to satisfy the test runner; they
# are NOT applied to the production release build.
#
# Root cause: the test APK runs in the main APK's process and resolves
# classes from the main APK's classloader by name. R8 may strip or rename
# classes that have no production call-graph references but are required by
# the test runner framework at runtime.

# AndroidX test runner and related infrastructure
-keep class androidx.test.** { *; }
# androidx.tracing is used by the test runner but lives outside androidx.test.**
-keep class androidx.tracing.** { *; }

# Kotlin stdlib — R8 may inline delegates (lazy{}, etc.) and remove the
# direct references that would otherwise anchor these in the call graph.
-keep class kotlin.** { *; }

# Kotlin coroutines — used by test helpers and flow/coroutine test utilities.
-keep class kotlinx.** { *; }

# androidx.core helpers used directly in androidTest classes.
# R8 inlines ContextCompat.checkSelfPermission() to Context.checkSelfPermission()
# on minSdk 28+ and may eliminate the class from the main APK dex entirely; test
# APK references it by name so it must survive in the main APK.
# NotificationManagerCompat is similarly at risk.
-keep class androidx.core.content.ContextCompat { *; }
-keep class androidx.core.app.NotificationManagerCompat { *; }

# Production classes that R8 obfuscates (renames) in the release build.
# The unminified test APK references these by their original names, so they must
# retain their names in the releaseTest main APK. Production release remains free
# to obfuscate — production callers are also renamed consistently by R8.
#
# SecureStorage: heavily referenced from production code (MainActivity, SettingsActivity,
# etc.) so R8 never strips it, but does rename the class and its property accessors
# (e.g. setHomeserverUrl → some obfuscated name). Tests access original names.
-keep class dev.klazomenai.deckchat.SecureStorage { *; }
#
# RecordingService companion: R8 renames RecordingService$Companion and its methods.
# The unminified test APK calls setOnRecordingCompleteListener via the Companion field
# using the original class and method names. Keep the Companion class in full and the
# OnRecordingCompleteListener interface (appears in the method descriptor the test APK
# references by original name).
-keep class dev.klazomenai.deckchat.RecordingService$Companion { *; }
-keep class dev.klazomenai.deckchat.RecordingService$OnRecordingCompleteListener { *; }

# R$id: R8 inlines R.id.* integer fields in the main APK and removes R$id; the
# test APK (compiled with non-final R fields by AGP) still references R$id at
# runtime via Espresso's withId() matchers and finds the class gone.
-keep class dev.klazomenai.deckchat.R$id { *; }

# TinkAeadPrefs + MigrationGate: called directly by TinkAeadPrefsKeystoreTest and
# SecureStorageMigrationTest. Both classes are reachable from the production call graph
# but R8 may still rename them; the test APK references them by their original names.
-keep class dev.klazomenai.deckchat.TinkAeadPrefs { *; }
-keep class dev.klazomenai.deckchat.MigrationGate { *; }

# ListenableFuture (Guava/concurrent-futures): used by Espresso's ActivityScenario;
# R8 strips the interface when only concrete implementations survive the main APK's
# call graph (e.g. from matrix-sdk-android transitive usage).
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
