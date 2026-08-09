package com.six.iot

import android.app.Application
import com.six.iot.mqtt.MqttClientManager

class SixIotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MqttClientManager.init(this)
    }
}