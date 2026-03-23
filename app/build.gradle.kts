plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.klazomenai.deckchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.klazomenai.deckchat"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        !file("src/main/assets/stt/tiny.en-encoder.int8.onnx").exists() ||
        !file("src/main/assets/stt/tiny.en-decoder.int8.onnx").exists()
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
    implementation(libs.sherpa.onnx.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.espresso.core)
}
