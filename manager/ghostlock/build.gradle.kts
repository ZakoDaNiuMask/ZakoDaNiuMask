@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.library)
}

android {
    namespace = "com.zakodaniumask.manager.ghostlock"

    compileSdk = rootProject.extra["androidCompileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["androidMinSdkVersion"] as Int
        buildConfigField("String", "VERSION_NAME", "\"${rootProject.extra["managerVersionName"]}\"")
        buildConfigField("int", "VERSION_CODE", "${rootProject.extra["managerVersionCode"]}")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
        targetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
    }
}

dependencies {
    implementation(project(":axeron-api"))
    implementation(libs.typesafe.config)
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation.jvm)
}
