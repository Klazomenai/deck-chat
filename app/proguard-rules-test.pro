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

# Kotlin stdlib — R8 may inline delegates (lazy{}, etc.) and remove the
# direct references that would otherwise anchor these in the call graph.
-keep class kotlin.** { *; }

# Kotlin coroutines — used by test helpers and flow/coroutine test utilities.
-keep class kotlinx.** { *; }
