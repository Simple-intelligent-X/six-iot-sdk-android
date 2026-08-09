plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    // Replace with your actual application ID
    namespace = "com.six.iot"
    compileSdk = 36

    defaultConfig {
        // Replace with your actual application ID
        applicationId = "com.six.iot"

        // This MUST be the same as or higher than the library's minSdk.
        minSdk = 29

        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // MQTT broker for the product connect to the default platform
        buildConfigField("String", "MQTT_BROKER_URL", "\"wss://shuhenglianchang.com:30084/mqtt\"")
        //buildConfigField("String", "MQTT_BROKER_URL", "\"wss://a2o5o645mb29bc-ats.iot.ap-southeast-1.amazonaws.com/mqtt\"")
        buildConfigField("String", "AWS_IOT_CUSTOM_AUTHZ_USERNAME", "\"username?x-amz-customauthorizer-name=six-iot-authorizer\"")
        buildConfigField("String", "USER_INFO_URL", "\"https://iam.shuhenglianchang.com/userinfo\"")
        buildConfigField("String", "USER_DEVICES_URL", "\"https://ext.iot.shuhenglianchang.com/iot/device/user/devices?search=&pageCurrentIndex=1\"")
        buildConfigField("String", "DEVICE_UNBIND_URL", "\"https://ext.iot.shuhenglianchang.com/iot/device/unbind\"")

        buildConfigField("String", "WECHAT_LOGIN_APPID", "\"wx7f61123c6f571dd2\"")
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
        isCoreLibraryDesugaringEnabled = true
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
    // Flutter dependency
    implementation(project(":flutter"))

    implementation(project(":lib-auth"))
    implementation(project(":lib-esp32-npm-app"))

    // Add your other app-level dependencies here
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.tools.core)
    implementation(libs.androidx.localbroadcastmanager)

    implementation(libs.material)
    implementation(libs.ok.http)
    implementation(libs.jackson.databind)
    implementation(libs.appauth)
    implementation(libs.picasso)
    implementation(libs.eventbus)
    implementation(libs.tencent.wechat.sdk)

    //implementation(libs.org.eclipse.paho.android.service)
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.paho.mqtt.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
