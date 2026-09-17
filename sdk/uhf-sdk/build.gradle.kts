// UHF-SRS-001 SDK-01/02/10. Structural — not built by this patch series (no Gradle/AGP available
// in the environment this was written in; see README.md's "What's verified" section for what was
// and wasn't actually run). Standard Android library module shape; the one non-standard piece is
// compileOnlyApi'ing a stub jar instead of the usual compileSdk, since UhfManager/UhfSession are
// @SystemApi and are not part of any public Android SDK level.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rockchip.uhf.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 26 // arbitrary floor; the real constraint is "a device with this SRS applied"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // SDK-02: compile-only stub jar generated from the platform's frameworks/base/api/core/
    // current.txt + system-current.txt via `m sdk_stub` (or equivalent metalava invocation) —
    // NOT produced by this patch series. Until it exists, point this at a locally built one:
    //   compileOnly(files("libs/uhf-platform-stubs.jar"))
    // This module's own verification during development used the real compiled platform classes
    // directly (see README.md), which proves the SDK source is correct against the real API
    // shape without yet having the metalava-generated stub artifact SDK-02 calls for.

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
