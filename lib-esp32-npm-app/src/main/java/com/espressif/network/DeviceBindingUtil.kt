package com.espressif.network

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.IOException

// A simple interface for handling the binding response
interface BindingHandlerHook {
    fun onBindingSuccess(responseMap: Map<String, Any>)
    fun onBindingFailure()
}

class DeviceBindingUtil {

    private val tag = javaClass.name
    private val objectMapper: ObjectMapper = ObjectMapper()

    fun bindDevice(
        token: String,
        deviceGuid: String,
        productId: String,
        challenge: String,
        signature: String,
        hook: BindingHandlerHook
    ) {
        // TODO: Move this URL to your BuildConfig or a centralized Config file
        val baseUrl = "https://ext.iot.shuhenglianchang.com/iot/device/binding"
        val httpUrl = baseUrl.toHttpUrlOrNull()

        if (httpUrl == null) {
            Log.e(tag, "Invalid base URL for device binding.")
            hook.onBindingFailure()
            return
        }

        val formBody = FormBody.Builder()
            .add("deviceGuid", deviceGuid)
            .add("productId", productId)
            .add("challenge", challenge)
            .add("signature", signature)
            .build()

        val request: Request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", "Bearer $token")
            .post(formBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(tag, "Device binding request failed", e)
                hook.onBindingFailure()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(tag, "Device binding request returned error code: ${response.code}")
                    hook.onBindingFailure()
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e(tag, "Device binding response body was empty.")
                    hook.onBindingFailure()
                    return
                }

                try {
                    var responseMap: Map<String, Any> = HashMap()
                    responseMap = objectMapper.readValue(responseBody, responseMap.javaClass)
                    hook.onBindingSuccess(responseMap)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse device binding response", e)
                    hook.onBindingFailure()
                }
            }
        })
    }
}
