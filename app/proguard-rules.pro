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

# DeckChatApplication: R8 applies member-level optimisations to the class that change
# the initialisation timing of the `secureStorage` lazy property. The most likely
# transformation is that R8 initialises the lazy eagerly during Application.onCreate()
# rather than deferring to first access. In production this is harmless (the Keystore
# key created by MigrationGate is never invalidated between onCreate and first access),
# but in the releaseTest build TinkAeadPrefsKeystoreTest.tearDown() deletes the Keystore
# entry between process startup and the first SettingsActivity launch — leaving the
# eagerly-cached TinkAeadPrefs holding a reference to a deleted Keystore key, which
# throws on first use and produces RootViewWithoutFocusException in Espresso.
# Rule belongs in the production ProGuard config (not test-only) so that releaseTest
# validates the same build as production.
-keep class dev.klazomenai.deckchat.DeckChatApplication { *; }

# Google Tink — protobuf reflection is used by AndroidKeysetManager to serialise and
# deserialise keysets. R8 strips proto classes without this rule, causing a runtime crash
# in the release build on first TinkAeadPrefs access. Verified via connectedReleaseAndroidTest.
-keep class com.google.crypto.tink.proto.** { *; }

# Tink references JSR-305 annotations not present at runtime. Safe to suppress.
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
