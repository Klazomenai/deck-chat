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

# R$id: R8 inlines R.id.* integer fields in the main APK and removes R$id; the
# test APK (compiled with non-final R fields by AGP) still references R$id at
# runtime via Espresso's withId() matchers and finds the class gone.
-keep class dev.klazomenai.deckchat.R$id { *; }

# ListenableFuture (Guava/concurrent-futures): used by Espresso's ActivityScenario;
# R8 strips the interface when only concrete implementations survive the main APK's
# call graph (e.g. from matrix-sdk-android transitive usage).
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
