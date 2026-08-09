package com.six.iot.mqtt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.six.iot.UserUtil
import com.six.iot.events.MqttConnectedEvent
import com.six.iot.events.MqttMessageArriveEvent
import com.six.iot.events.ShadowGetAcceptedEvent
import com.six.iot.events.ShadowUpdateAcceptedEvent
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object MqttClientManager {

    private const val TAG = "MqttClientManager"

    private data class ConnCtx(
        var idToken: String,
        val customAuthz: Boolean,
        val customAuthzUserName: String
    )

    private lateinit var appContext: Context

    private val clients = ConcurrentHashMap<String, MqttAndroidClient>()
    private val connectingStates = ConcurrentHashMap<String, AtomicBoolean>()
    private val connCtx = ConcurrentHashMap<String, ConnCtx>()
    private val subscribedTopicsMap = ConcurrentHashMap<String, MutableSet<String>>()
    private val publishQueuesMap = ConcurrentHashMap<String, ConcurrentLinkedQueue<Pair<String, String>>>()

    /** Call once, e.g. from Application.onCreate(). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Ensures a client exists and is connecting/connected for this URL.
     * Safe to call repeatedly (e.g. every time the user taps a device pane) —
     * it will not create duplicate clients or duplicate in-flight connects.
     */
    fun connect(
        url: String,
        idToken: String,
        customAuthz: Boolean = false,
        customAuthzUserName: String? = null
    ) {
        require(::appContext.isInitialized) { "MqttClientManager.init(context) must be called first" }
        if (idToken.isEmpty() || url.isEmpty()) return

        val ctx = ConnCtx(idToken, customAuthz, customAuthzUserName ?: "")

        synchronized(clients) {
            val existing = clients[url]
            if (existing == null) {
                val clientId = MqttClient.generateClientId()
                val newClient = MqttAndroidClient(appContext, url, clientId)
                clients[url] = newClient
                connectingStates[url] = AtomicBoolean(false)
                connCtx[url] = ctx
                subscribedTopicsMap[url] = HashSet()
                publishQueuesMap[url] = ConcurrentLinkedQueue()

                // Callback bound exactly once per client, at creation time.
                newClient.setCallback(buildCallback(url))

                connectInternal(url)
                Log.d(TAG, "New MqttAndroidClient created for URL: $url")
            } else {
                // Client already exists — refresh the stored token regardless,
                // and only actually (re)connect if we're not already
                // connected/connecting.
                connCtx[url] = ctx
                if (!existing.isConnected && connectingStates[url]?.get() != true) {
                    Log.d(TAG, "Existing client for $url not connected — reconnecting with fresh token")
                    connectInternal(url)
                }
            }
        }
    }

    /** Call this when you obtain a fresh idToken for a URL that's already registered. */
    fun refreshToken(url: String, newIdToken: String) {
        connCtx[url]?.let { it.idToken = newIdToken }
    }

    fun disconnect(url: String) {
        clients[url]?.let { client ->
            try {
                if (client.isConnected) client.disconnect()
            } catch (e: MqttException) {
                Log.e(TAG, "Disconnect failed for $url", e)
            }
        }
    }

    fun disconnectAll() {
        clients.keys.toList().forEach { disconnect(it) }
        clients.clear()
        connectingStates.clear()
        connCtx.clear()
        subscribedTopicsMap.clear()
        publishQueuesMap.clear()
    }

    fun publish(url: String, topic: String, payload: String) {
        val client = clients[url]
        if (client == null || !client.isConnected) {
            publishQueuesMap[url]?.add(Pair(topic, payload))
            return
        }
        try {
            val message = MqttMessage(payload.toByteArray()).apply { qos = 1 }
            client.publish(topic, message)
        } catch (e: MqttException) {
            Log.e(TAG, "Publish failed for $url", e)
        }
    }

    fun subscribe(url: String, topic: String) {
        val topicSet = subscribedTopicsMap.computeIfAbsent(url) { ConcurrentHashMap.newKeySet() }
        topicSet.add(topic)

        val client = clients[url]
        if (client == null || !client.isConnected) {
            Log.d(TAG, "Client offline. Cached topic for subscription later: $topic")
            return
        }

        try {
            client.subscribe(topic, 1)
            Log.d(TAG, "Successfully subscribed to: $topic on $url")
        } catch (e: MqttException) {
            Log.e(TAG, "Subscribe failed for $topic on $url", e)
        }
    }

    // ---- internal ----

    private fun buildCallback(url: String) = object : MqttCallbackExtended {

        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            connectingStates[url]?.set(false)
            Log.d(TAG, "Connected to $url. Reconnect: $reconnect")
            processSubscribeQueue(url)
            processPublishQueue(url)
        }

        override fun connectionLost(cause: Throwable?) {
            connectingStates[url]?.set(false)
            Log.e(TAG, "Connection lost for $url", cause)
            // Paho's automatic reconnect reuses the SAME MqttConnectOptions it
            // was given at connect() time (same token). We proactively
            // reconnect ourselves instead, using whatever token is currently
            // stored — so an expired token gets refreshed rather than reused.
            connectInternal(url)
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            handleIncomingMessage(topic, message)
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    private fun connectInternal(url: String) {
        val client = clients[url] ?: return
        val isConnecting = connectingStates[url] ?: return
        val ctx = connCtx[url] ?: return

        if (client.isConnected) return
        if (!isConnecting.compareAndSet(false, true)) return

        val options = MqttConnectOptions().apply {
            serverURIs = arrayOf(url)
            isAutomaticReconnect = true
            isCleanSession = true
            userName = if (ctx.customAuthz) ctx.customAuthzUserName
            else UserUtil.parseOpenidFromIdToken(ctx.idToken)
            password = ctx.idToken.toCharArray()
        }

        try {
            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnecting.set(false)
                    EventBus.getDefault().post(MqttConnectedEvent(url))
                    Log.d(TAG, "Connection Success: $url")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting.set(false)
                    Log.e(TAG, "Connection Failure: $url", exception)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(appContext, "Failed to connect to MQTT broker for: $url", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } catch (e: MqttException) {
            isConnecting.set(false)
        }
    }

    private fun handleIncomingMessage(topic: String, message: MqttMessage) {
        try {
            val payload = String(message.payload)
            val json = JSONObject(payload)
            val topicParts = topic.split("/")
            if (topicParts.size > 4) {
                val productId = topicParts[0]
                val guid = topicParts[1]
                if ("shadow" == topicParts[2] && "get" == topicParts[3] && "accepted" == topicParts[4]) {
                    EventBus.getDefault().post(ShadowGetAcceptedEvent(productId, guid, json))
                    return
                } else if ("shadow" == topicParts[2] && "update" == topicParts[3] && "accepted" == topicParts[4]) {
                    EventBus.getDefault().post(ShadowUpdateAcceptedEvent(productId, guid, json))
                    return
                }
            }
            EventBus.getDefault().post(MqttMessageArriveEvent(topic, json))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }

    private fun processPublishQueue(url: String) {
        val queue = publishQueuesMap[url] ?: return
        while (queue.isNotEmpty()) {
            queue.poll()?.let { (topic, payload) ->
                publish(url, topic, payload)
            }
        }
    }

    private fun processSubscribeQueue(url: String) {
        val cachedTopics = subscribedTopicsMap[url] ?: return
        for (topic in cachedTopics) {
            subscribe(url, topic)
        }
    }
}