// SDK-05 reference app. Structural, unbuilt in this environment — see ../uhf-sdk/README.md.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rockchip.uhf.sdk.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rockchip.uhf.sdk.sample"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    implementation(project(":uhf-sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
