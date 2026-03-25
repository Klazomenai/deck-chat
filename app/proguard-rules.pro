# Add project-specific ProGuard rules here.
# See http://developer.android.com/guide/developing/tools/proguard.html

# Sherpa-ONNX JNI classes — loaded by name via System.loadLibrary("sherpa-onnx-jni").
# ProGuard must not obfuscate or strip these classes.
-keep class com.k2fsa.sherpa.onnx.** { *; }
