# Add project-specific ProGuard rules here.
# See http://developer.android.com/guide/developing/tools/proguard.html

# Sherpa-ONNX JNI classes used by the native library loaded via System.loadLibrary("sherpa-onnx-jni").
# The JNI code looks up these JVM classes/methods by name, so ProGuard must not obfuscate or strip them.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# matrix-rust-sdk uses JNA for the native bridge (UniFFI-generated Kotlin bindings).
# The SDK's consumer-rules.pro is empty, so these must be declared here.
-keep class net.java.dev.jna.** { *; }
-keep class org.matrix.rustcomponents.sdk.** { *; }
-keep class uniffi.** { *; }
