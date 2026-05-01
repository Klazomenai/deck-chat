import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.licensee)
}

/**
 * Licence audit (CI gate).
 *
 * DeckChat is AGPL-3.0-or-later. The CLA caps onward sublicensing to
 * OSI-approved open-source licences only (see CONTRIBUTING.md +
 * STEWARDSHIP.md). This block is the runtime gate: every transitive
 * runtime dependency must carry a licence on the allowlist below, or
 * the Gradle build fails before an APK is produced.
 *
 * Allowlist policy: permissive licences (Apache, MIT, BSD-family, MPL-2,
 * ISC, Unlicense, CC0) plus copyleft variants compatible with AGPL-3.0
 * (GPL-3.0+, LGPL-3.0+, AGPL-3.0+). Notably absent: GPL-2.0-only and
 * LGPL-2.1-only — neither has an "or-later" upgrade path and so
 * neither is compatible with AGPL-3.0 redistribution.
 *
 * If a future PR introduces a dep with a licence not on this list, the
 * audit fails. Two choices then: (a) swap the dep for an allowlisted
 * alternative, or (b) explicitly extend the allowlist with a `because`
 * justification — never silently.
 *
 * The CI step `./gradlew :app:licenseeRelease` runs this gate against
 * the release configuration (the actual shipped artefact).
 */
licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("MPL-2.0")
    allow("ISC")
    allow("Unlicense")
    allow("CC0-1.0")

    // Copyleft licences compatible with AGPL-3.0 (we ARE AGPL):
    allow("GPL-3.0-or-later")
    allow("LGPL-3.0-or-later")
    allow("AGPL-3.0-or-later")
}

/**
 * Derives a monotonically increasing versionCode from a semver versionName.
 *
 * Formula: major * 1_000_000 + minor * 10_000 + patch * 100 + prerelease
 *   "0.1.0-alpha"   -> 10000  (bare label, first prerelease after bump)
 *   "0.1.0-alpha.6" -> 10006
 *   "0.1.0"         -> 10099  (stable = 99, highest in patch range)
 *   "0.3.0-beta.2"  -> 30002
 *   "1.0.0"         -> 1000099
 *
 * Accepted formats: `major.minor.patch`, `major.minor.patch-label`, and
 * `major.minor.patch-label.N`, where label is lowercase letters ([a-z]+) and
 * only the optional trailing number affects the prerelease slot.
 *
 * Stable releases use pre=99 so they always beat prereleases of the same version.
 * Supports up to 98 prereleases per patch (99 reserved for stable), 99 patches per minor,
 * 99 minors per major.
 * Build fails if any slot exceeds its range.
 */
fun computeVersionCode(version: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-[a-z]+(?:\.(\d+))?)?$""").matchEntire(version)
        ?: throw GradleException("Unsupported version format '$version'")
    val major = match.groupValues[1].toLong()
    val minor = match.groupValues[2].toLong()
    val patch = match.groupValues[3].toLong()
    val pre = when {
        !version.contains("-") -> 99L                              // stable
        match.groupValues[4].isNotEmpty() -> match.groupValues[4].toLong() // label.N
        else -> 0L                                                 // bare label
    }
    require(minor in 0L..99L) { "minor $minor exceeds 0..99 in '$version'" }
    require(patch in 0L..99L) { "patch $patch exceeds 0..99 in '$version'" }
    require(pre in 0L..98L) { "prerelease $pre exceeds 0..98 in '$version' (99 is reserved for stable)" }
    val code = major * 1_000_000L + minor * 10_000L + patch * 100L + pre
    require(code <= Int.MAX_VALUE.toLong()) { "versionCode $code exceeds Int.MAX_VALUE for '$version'" }
    return code.toInt()
}

android {
    namespace = "dev.klazomenai.deckchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.klazomenai.deckchat"
        minSdk = 28
        targetSdk = 36
        val versionStr = "0.1.0-alpha.7" // x-release-please-version
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
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
