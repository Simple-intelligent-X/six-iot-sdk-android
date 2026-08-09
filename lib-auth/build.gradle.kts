plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.six.auth"
    compileSdk = 36

    defaultConfig {
        minSdk = 29

        //Default to use WebView to hold the authentication page, another option is to use Custom Tab of the browser
        buildConfigField("String", "AUTH_HANDLER_TYPE", "\"WEBVIEW\"")
        buildConfigField("String", "WEBVIEW_AUTH_ACTIVITY_PKG", "\"com.six.iot\"")
        buildConfigField("String", "WEBVIEW_AUTH_ACTIVITY_CLASS", "\"com.six.iot.ui.auth.webview.WebViewAuthActivity\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":lib-auth-appauth-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.appauth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

