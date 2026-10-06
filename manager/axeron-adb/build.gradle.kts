plugins {
    alias(libs.plugins.agp.library)
}

android {
    namespace = "com.zakodaniumask.manager.axeron.adb"

    compileSdk = rootProject.extra["androidCompileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
    ndkVersion = rootProject.extra["androidCompileNdkVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["androidMinSdkVersion"] as Int
        consumerProguardFiles("consumer-rules.pro")

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

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        prefab = true
    }

    compileOptions {
        sourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
        targetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
    }
}


dependencies {
    implementation(project(":axeron-api"))
    implementation(libs.androidx.annotation.jvm)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.boringssl)
    implementation(libs.lsposed.cxx)
    implementation(libs.rikka.hidden.compat)
    compileOnly(libs.rikka.hidden.stub)
}
