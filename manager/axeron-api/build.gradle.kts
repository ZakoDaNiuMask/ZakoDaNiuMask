plugins {
    alias(libs.plugins.agp.library)
}

android {
    namespace = "com.zakodaniumask.manager.axeron.api"

    compileSdk = rootProject.extra["androidCompileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["androidMinSdkVersion"] as Int
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
        targetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
    }
}


dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation.jvm)
}
