package com.six.iot.network

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.IOException

interface UnbindHandlerHook {
    fun onUnbindSuccess()
    fun onUnbindFailure()
}

class UnbindDeviceUtil {

    private val tag = javaClass.name
    private val objectMapper: ObjectMapper = ObjectMapper()

    fun unbindDevice(token: String, deviceGuid: String, hook: UnbindHandlerHook) {
        // TODO: Move this URL to your BuildConfig or a centralized Config file
        val baseUrl = "https://ext.iot.shuhenglianchang.com/iot/device/unbind"
        val httpUrl = baseUrl.toHttpUrlOrNull()

        if (httpUrl == null) {
            Log.e(tag, "Invalid base URL for device unbinding.")
            hook.onUnbindFailure()
            return
        }

        val formBody = FormBody.Builder()
            .add("deviceGuid", deviceGuid)
            .build()

        val request: Request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", "Bearer $token")
            .post(formBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(tag, "Device unbinding request failed", e)
                hook.onUnbindFailure()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(tag, "Device unbinding request returned error code: ${response.code}")
                    hook.onUnbindFailure()
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e(tag, "Device unbinding response body was empty.")
                    hook.onUnbindFailure()
                    return
                }

                try {
                    var responseMap: Map<String, Any> = HashMap()
                    responseMap = objectMapper.readValue(responseBody, responseMap.javaClass)
                    val status = responseMap["status"] as? Int
                    if (status == 200) {
                        hook.onUnbindSuccess()
                    } else {
                        hook.onUnbindFailure()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse device unbinding response", e)
                    hook.onUnbindFailure()
                }
            }
        })
    }
}