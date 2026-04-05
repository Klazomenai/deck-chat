import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Derives a monotonically increasing versionCode from a semver versionName.
 *
 * Formula: major * 1_000_000 + minor * 10_000 + patch * 100 + prerelease
 *   "0.1.0-alpha.6" → 10006
 *   "0.2.0"         → 20000
 *   "1.0.0"         → 1000000
 *
 * Supports up to 99 prereleases per patch, 99 patches per minor, 99 minors per major.
 */
fun computeVersionCode(version: String): Int {
    val base = version.substringBefore("-")
    val parts = base.split(".").map { it.toInt() }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    val pre = Regex("""alpha\.(\d+)""").find(version)?.groupValues?.get(1)?.toInt() ?: 0
    return major * 1_000_000 + minor * 10_000 + patch * 100 + pre
}

android {
    namespace = "dev.klazomenai.deckchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.klazomenai.deckchat"
        minSdk = 28
        targetSdk = 36
        val versionStr = "0.1.0-alpha.6" // x-release-please-version
        versionName = versionStr
        versionCode = computeVersionCode(versionStr)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val envKeystore = System.getenv("RELEASE_KEYSTORE_FILE")
            if (envKeystore != null) {
                storeFile = file(envKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                val propsFile = rootProject.file("keystore.properties")
                if (propsFile.exists()) {
                    val props = Properties().apply { load(propsFile.inputStream()) }
                    storeFile = file(props["storeFile"] as String)
                    storePassword = props["storePassword"] as String
                    keyAlias = props["keyAlias"] as String
                    keyPassword = props["keyPassword"] as String
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Downloads Whisper Tiny EN int8 ONNX models from HuggingFace if not already present.
// Models are gitignored — run this task manually before building for device use.
// CI builds succeed without models since tests use MockSttEngine (no JNI).
// Uses Exec task type — project.exec() was removed in Gradle 9.
tasks.register<Exec>("downloadSttModels") {
    group = "DeckChat"
    description = "Download Whisper Tiny EN ONNX models for on-device STT"
    workingDir = rootProject.rootDir
    commandLine("bash", "${rootProject.rootDir}/scripts/download-stt-models.sh")
    onlyIf {
        val sttDir = "src/main/assets/stt"
        listOf("tiny.en-encoder.int8.onnx", "tiny.en-decoder.int8.onnx", "tiny.en-tokens.txt")
            .any { !file("$sttDir/$it").exists() || file("$sttDir/$it").length() == 0L }
    }
}

// Downloads Piper TTS voice models from k2-fsa/sherpa-onnx GitHub releases.
// Models are gitignored — run this task manually before building for device use.
// CI builds succeed without models since tests use MockTtsEngine (no JNI).
tasks.register<Exec>("downloadTtsModels") {
    group = "DeckChat"
    description = "Download Piper TTS voice models for on-device speech synthesis"
    workingDir = rootProject.rootDir
    commandLine("bash", "${rootProject.rootDir}/scripts/download-tts-models.sh")
    onlyIf {
        val cori = file("src/main/assets/tts/vits-piper-en_GB-cori-high/en_GB-cori-high.onnx")
        val lessac = file("src/main/assets/tts/vits-piper-en_US-lessac-high/en_US-lessac-high.onnx")
        !cori.exists() || cori.length() == 0L || !lessac.exists() || lessac.length() == 0L
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.matrix.sdk.android)
    implementation(libs.sherpa.onnx.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
