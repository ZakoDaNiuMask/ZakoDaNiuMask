plugins {
    alias(libs.plugins.agp.library)
}

android {
    namespace = "com.zakodaniumask.manager.axeron.server"

    compileSdk = rootProject.extra["androidCompileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
    ndkVersion = rootProject.extra["androidCompileNdkVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["androidMinSdkVersion"] as Int

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildFeatures {
        prefab = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
        targetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
    }
}


dependencies {
    implementation(project(":axeron-api"))
    implementation(libs.lsposed.cxx)
    implementation(libs.androidx.annotation.jvm)
    implementation(libs.org.lsposed.hiddenapibypass)
}
