// Fully-local Nyora extension: bundles kotatsu-parsers-redo and parses on-device.
// Built inside a Mihon fork checkout (provides :source-api, :core:common, the
// `mihonx` plugins). Flavor identity + source-list are passed with -P at build
// time by scripts/build.sh.
plugins {
    alias(mihonx.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

fun prop(name: String, default: String) = (project.findProperty(name) as String?) ?: default
val nyoraPkgSuffix = prop("nyoraPkgSuffix", "local")          // local | local18
val nyoraName = prop("nyoraName", "Nyora-Sources")
val nyoraNsfw = prop("nyoraNsfw", "0")
val nyoraList = prop("nyoraList", "sources-sfw.json")          // asset file baked in
val parsersRef = prop("parsersRef", "59c033ecfd")

android {
    namespace = "eu.kanade.tachiyomi.extension.all.nyoralocal"

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.all.nyora$nyoraPkgSuffix"
        versionCode = 18
        versionName = "1.7.12"
        buildConfigField("String", "NYORA_NAME", "\"$nyoraName\"")
        buildConfigField("String", "NYORA_LIST", "\"$nyoraList\"")
        buildConfigField("boolean", "NYORA_NSFW", (nyoraNsfw != "0").toString())
        manifestPlaceholders["nyoraName"] = nyoraName
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        create("nyora") {
            // Stable signing key so extension UPDATES install without a reinstall.
            // build.sh passes -PnyoraKeystore=<repo>/signing/nyora.keystore (kept
            // locally, gitignored); falls back to the Android debug keystore.
            storeFile = file(prop("nyoraKeystore", System.getProperty("user.home") + "/.android/debug.keystore"))
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("nyora")
        }
    }
}

dependencies {
    // Host-provided at runtime (child-first classloader).
    compileOnly(projects.sourceApi)
    compileOnly(projects.core.common)
    compileOnly(libs.okhttp.core)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.coroutines.core)

    // The on-device engine — bundled into the APK.
    implementation("com.github.clquwu:kotatsu-parsers-redo:$parsersRef")
    implementation("org.json:json:20240303")
    implementation("org.jsoup:jsoup:1.21.2")
}

// Bundle kotatsu-parsers' own classes, but NOT the Kotlin stdlib / coroutines —
// the host provides them, and shipping a second copy makes the child-first
// extension classloader throw LinkageError on suspend-fun overrides.
configurations.configureEach {
    if (name.endsWith("RuntimeClasspath")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "com.squareup.okhttp3", module = "okhttp")  // host provides
    }
}
