# Add project-specific ProGuard rules here.
# See http://developer.android.com/guide/developing/tools/proguard.html

# Sherpa-ONNX JNI classes used by the native library loaded via System.loadLibrary("sherpa-onnx-jni").
# The JNI code looks up these JVM classes/methods by name, so ProGuard must not obfuscate or strip them.
-keep class com.k2fsa.sherpa.onnx.** { *; }
