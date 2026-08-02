https://github.com/user-attachments/assets/ff19583d-2273-4a44-b0b3-f365adaa44be

https://github.com/user-attachments/assets/2482e68e-8d01-4a64-9038-46e0aa84f2d8

> [!TIP]
> This SDK works seamlessly alongside the [six-iot-sdk-esp32](https://github.com/Simple-intelligent-X/six-iot-sdk-esp32) firmware repository.

# Overview

Building modern digital applications often introduces two complex, recurring challenges: architecting a sophisticated, compliant Identity and Access Management (IAM) framework from scratch, and managing the tedious pipelines required to securely provision and bridge Internet of Things (IoT) hardware to the cloud.

To eliminate these friction points, we have built a suite of production-ready digital solutions. These services accelerate development lifecycles, eliminate operational overhead, and empower developers and enterprises to focus entirely on their core business logic.

---

## 🔐 SiX IDaaS & IAM

A cloud-native Identity-as-a-Service (IDaaS) and access management ecosystem engineered on standards-based security protocols (including OIDC, OAuth2, and SAML 2.0). It offers robust Multi-Factor Authentication (MFA), federated enterprise logins, and localized social authentication workflows (such as WeChat Website integration) to secure applications seamlessly.

*   **Documentation:** [SiX IDaaS & IAM User Guide](https://doc.iam.shuhenglianchang.com/)

---

## 🔌 SiX IoT Platform

An enterprise-ready IoT infrastructure built for high scalability and seamless system-on-chip (SoC) integration. Featuring native support for EMQX broker clusters, AWS IoT Core federation, and modern smart-home standards, the platform completely abstracts the complexity of hardware provisioning, device-to-cloud networking, and secure telemetry management.

*   **Documentation:** [SiX IoT Platform Reference](https://doc.iot.shuhenglianchang.com/)

---

## About six-iot-sdk-android

This SDK demonstrates how to quickly integrate sophisticated identity authentication into a native Android application, leverage QR code scanning to provision an IoT device onto a local network, and securely control the hardware from the app.

---

## Integration Guide

> [!TIP]
> We highly recommend reviewing the [Quick Start Documentation](https://doc.iot.shuhenglianchang.com/quick-start/quick-start) before proceeding with mobile application integration.

### 1. Download the Companion UI Project
Clone or download the UI dependency workspace from [six_iot_flutter](https://github.com/Simple-intelligent-X/six_iot_flutter). This Flutter project is used to render device-specific interfaces and will be imported directly into the `six-iot-sdk-android` ecosystem.

### 2. Configure IDp and IoT Platform Credentials
Update your tenant parameters inside your local code files to match your deployment environment.

First, configure your endpoint fields inside `app/build.gradle.kts` (modify these values to your specific parameters, especially if you are utilizing a dedicated AWS IoT Core tenant for a federated product infrastructure):

```kotlin
buildConfigField("String", "MQTT_BROKER_URL", "\"wss://shuhenglianchang.com:30084/mqtt\"")
//buildConfigField("String", "MQTT_BROKER_URL", "\"wss://a2o5o645mb29bc-ats.iot.ap-southeast-1.amazonaws.com/mqtt\"")
buildConfigField("String", "AWS_IOT_CUSTOM_AUTHZ_USERNAME", "\"username?x-amz-customauthorizer-name=six-iot-authorizer\"")
buildConfigField("String", "USER_INFO_URL", "\"https://iam.shuhenglianchang.com/userinfo\"")
buildConfigField("String", "USER_DEVICES_URL", "\"https://ext.iot.shuhenglianchang.com/iot/device/user/devices?search=&pageCurrentIndex=1\"")
```

Next, update your Identity Provider (IdP) endpoints in `lib-auth/src/main/res/raw/auth_config.json`:

```json
{
  "client_id": "six-iot-sdk-android",
  "redirect_uri": "com.six.iot:/oauth2redirect",
  "end_session_redirect_uri": "com.six.iot:/oauth2redirect",
  "authorization_scope": "openid profile",
  "authorization_endpoint_uri": "https://abc123.app.shuhenglianchang.com/oauth2/authorize",
  "token_endpoint_uri": "https://abc123.app.shuhenglianchang.com/oauth2/token",
  "registration_endpoint_uri": "https://abc123.app.shuhenglianchang.com/oauth2/authorize",
  "end_session_endpoint": "https://abc123.app.shuhenglianchang.com/connect/logout",
  "user_info_endpoint_uri": "https://abc123.app.shuhenglianchang.com/userinfo",
  "https_required": true
}
```

### 3. Build and Run the Project
Connect your physical Android device or start an emulator instance, open the codebase in Android Studio, sync your Gradle files, and deploy the application.

---

## 🛠️ Open-Source Acknowledgments & Reference Projects

To ensure strict alignment with global industry benchmarks, parts of our mobile companion SDKs and reference architectures inherit, leverage, or extend core design patterns from the following industry-standard open-source implementations:

*   **[AppAuth-Android](https://github.com/openid/AppAuth-Android):** Leveraged to implement secure, reliable OpenID Connect (OIDC) and OAuth 2.0 user authentication flows inside native Android environments.
*   **[ESP-IDF Provisioning Android](https://github.com/espressif/esp-idf-provisioning-android):** Referenced to streamline secure Wi-Fi/BLE network provisioning pipelines for Espressif system-on-chip (SoC) hardware targets.

---

## Technical Support & Feedback

For technical queries, architectural discussions, or ecosystem feedback, please reach out via email:

*   **Engineering Support:** stephen.yu@six-inno.cn

---

## Dedicated IoT Platform Setup

For dedicated enterprise IoT platform deployments, infrastructure hosting, or private cloud setups, please contact our solutions team:

*   **Enterprise Service:** service@six-inno.cn
