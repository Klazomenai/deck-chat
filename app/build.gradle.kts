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
tasks.register("downloadSttModels") {
    group = "DeckChat"
    description = "Download Whisper Tiny EN ONNX models for on-device STT"
    doLast {
        val encoder = file("src/main/assets/stt/tiny.en-encoder.int8.onnx")
        val decoder = file("src/main/assets/stt/tiny.en-decoder.int8.onnx")
        if (encoder.exists() && decoder.exists()) {
            println("STT models already present, skipping download")
            return@doLast
        }
        exec {
            commandLine("bash", "${rootProject.rootDir}/scripts/download-stt-models.sh")
        }
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
