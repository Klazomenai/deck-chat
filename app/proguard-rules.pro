# Add project-specific ProGuard rules here.
# See http://developer.android.com/guide/developing/tools/proguard.html

# Sherpa-ONNX JNI classes used by the native library loaded via System.loadLibrary("sherpa-onnx-jni").
# The JNI code looks up these JVM classes/methods by name, so ProGuard must not obfuscate or strip them.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# JNA — transitive dependency of org.matrix.rustcomponents:sdk-android (UniFFI Kotlin bindings).
# JNA ships no consumer-rules.pro, so these must be declared here.
# Rules sourced from JNA FAQ: java-native-access/jna/www/FrequentlyAskedQuestions.md
-keep class com.sun.jna.** { *; }
-keep class org.matrix.rustcomponents.sdk.** { *; }
-keep class uniffi.** { *; }

# JNA's Native$AWT references java.awt classes not available on Android.
# These code paths are never reached (JNA detects Android at runtime via Platform.ANDROID).
-dontwarn java.awt.**

# Google Tink (transitive via androidx.security:security-crypto / EncryptedSharedPreferences)
# references JSR-305 annotations that are not present at runtime. Safe to suppress.
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# Classes required when connectedAndroidTest targets the release build type.
# The test APK runs in the main APK's process and links against the release
# APK's classloader; R8 must not strip or rename any class the test runner
# resolves by name at runtime.
#
# androidx.tracing.Trace — used by AndroidX test runner; no production references
# so R8 strips it without this rule.
-keep class androidx.tracing.Trace { *; }
#
# Kotlin stdlib — R8 may inline lazy{} and similar delegates, removing the
# direct references that would otherwise anchor LazyKt and friends in the
# call graph. Keep the full public API so the test APK's class resolution
# succeeds regardless of R8's inlining decisions.
-keep class kotlin.Lazy { *; }
-keep class kotlin.LazyKt { *; }
-keep class kotlin.jvm.internal.** { *; }
