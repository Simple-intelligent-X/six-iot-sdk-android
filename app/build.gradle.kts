plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.six.iot" // Replace with your actual application ID
    compileSdk = 36

    defaultConfig {
        applicationId = "com.six.iot" // Replace with your actual application ID

        // This MUST be the same as or higher than the library's minSdk.
        // Aligning with the library's new minSdk.
        minSdk = 29

        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add the MQTT Broker URL to BuildConfig
        buildConfigField("String", "MQTT_BROKER_URL", "\"wss://shuhenglianchang.com:30084/mqtt\"")
        //buildConfigField("String", "MQTT_BROKER_URL", "\"wss://a2o5o645mb29bc-ats.iot.ap-southeast-1.amazonaws.com/mqtt\"")
        buildConfigField("String", "AWS_IOT_CUSTOM_AUTHZ_USERNAME", "\"username?x-amz-customauthorizer-name=six-iot-authorizer\"")
        buildConfigField("String", "USER_INFO_URL", "\"https://iam.shuhenglianchang.com/userinfo\"")
        buildConfigField("String", "USER_DEVICES_URL", "\"https://ext.iot.shuhenglianchang.com/iot/device/user/devices?search=&pageCurrentIndex=1\"")

        buildConfigField("String", "WECHAT_LOGIN_APPID", "\"wx7f61123c6f571dd2\"")
//        missingDimensionStrategy("authMethod", "webview")
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
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

//    flavorDimensions += "authMethod"
//
//    productFlavors {
//        create("appauth") {
//            dimension = "authMethod"
//            // This replaces your manual buildConfigField
//            buildConfigField("String", "AUTH_HANDLER_TYPE", "\"APPAUTH\"")
//            manifestPlaceholders["useWebView"] = "false"
//            manifestPlaceholders["useAppAuth"] = "true"
//        }
//        create("webview") {
//            dimension = "authMethod"
//            buildConfigField("String", "AUTH_HANDLER_TYPE", "\"WEBVIEW\"")
//            manifestPlaceholders["useWebView"] = "true"
//            manifestPlaceholders["useAppAuth"] = "false"
//        }
//    }
}

//Force a specific version of androidx.activity to resolve conflicts
//configurations.all {
//    resolutionStrategy {
//        force("androidx.activity:activity:1.11.0")
//        force("androidx.activity:activity-ktx:1.11.0")
//    }
//}

dependencies {
    // Flutter dependency
    implementation(project(":flutter"))

    implementation(project(":lib-auth"))
    implementation(project(":lib-esp32-blufi-app"))
    implementation(project(":lib-esp32-npm-app"))

    // Add your other app-level dependencies here
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.ok.http)
    implementation(libs.jackson.databind)
    implementation(libs.appauth)
    implementation(libs.picasso)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.tools.core)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.eventbus)
    implementation(libs.tencent.wechat.sdk)

//    implementation(libs.androidx.activity)
//    implementation(libs.androidx.activity.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.org.eclipse.paho.android.service)
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.androidx.localbroadcastmanager)
}
