package com.six.iot.events

import org.json.JSONObject


/**
 * Authentication is failed at any place, an AuthnFailEvent will be triggered
 * */
data class AuthnFailEvent(val message: String)

/**
 * Posted from the MqttClientService to the UI when a /shadow/get/accepted is received.
 */
data class ShadowGetAcceptedEvent(val productId: String, val deviceGuid: String, val json: JSONObject?)

/**
 * Posted from the MqttClientService to the UI when a /shadow/update/accepted is received.
 * */
data class ShadowUpdateAcceptedEvent(val productId: String, val deviceGuid: String, val json: JSONObject?)

/**
 * Posted from the MqttClientService to the UI when any message is received.
 */
data class MqttMessageArriveEvent(val topic: String, val json: JSONObject?)

/**
 * Posted when the MqttClient is connected, when the MqttClientService is started, it needs some time to connect
 * */
data class MqttConnectedEvent(val url: String)

/**
 * Event to start the MqttService.
 * */
//data class StartMqttServiceEvent(val urlToProductIds: Map<String, List<String>>, val urlToAuthConfig: Map<String, Map<String, Any>>, val subTopics: List<String>, val pubTopics: List<String>)


