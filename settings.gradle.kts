pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/releases") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/releases") }

        maven { url = uri("https://mirrors.cloud.tencent.com/flutter/download.flutter.io") }
        // JitPack
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
        // Flutter
        maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
    }
}

rootProject.name = "six-iot-sdk-android"

// Include the Flutter module using the correct 'apply' method for settings scripts
apply(from = File(
    settingsDir,
    "../six_iot_flutter/.android/include_flutter.groovy"
))

// Include native Android modules
include(":app")
include(":lib-esp32-blufi-core")
include(":lib-auth")
include(":lib-esp32-blufi-app")
include(":lib-auth-appauth-core")
include(":lib-esp32-npm-app")