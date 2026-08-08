package com.six.iot.ui.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.espressif.ui.activities.EspMainActivity
import com.six.iam.AuthManager
import com.six.iot.BuildConfig
import com.six.iot.Device
import com.six.iot.DeviceHandlerHook
import com.six.iot.DeviceRender
import com.six.iot.DeviceUtil
import com.six.iot.MainActivity
import com.six.iot.R
import com.six.iot.UserUtil
import com.six.iot.databinding.FragmentDeviceBinding
import com.six.iot.events.AuthnFailEvent
import com.six.iot.events.MqttConnectedEvent
import com.six.iot.events.ShadowGetAcceptedEvent
import com.six.iot.events.ShadowUpdateAcceptedEvent
import com.six.iot.events.StartMqttServiceEvent
import com.six.iot.services.MqttClientService
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject

class DeviceFragment : Fragment(), DeviceHandlerHook {
    companion object {
        private val TAG = DeviceFragment::class.java.simpleName
        val KEY_USER_OPEN_ID: String = "key_user_open_id"
    }

    private var _binding: FragmentDeviceBinding? = null
    private val binding get() = _binding!!
    private lateinit var deviceUtil: DeviceUtil
    private lateinit var userUtil: UserUtil
    private lateinit var deviceAdapter: DeviceAdapter
    private var mqttService: MqttClientService? = null
    private var isMqttServiceBound = false
    private var pendingMqttEvent: StartMqttServiceEvent? = null
    private var isLoadingDevices = false

    private val loadDevicesRunnable = Runnable { loadDevices() }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "MqttClientService bound successfully")
            val binder = service as MqttClientService.MqttBinder
            mqttService = binder.getService()
            isMqttServiceBound = true

            pendingMqttEvent?.let {
                processMqttConnections(it)
                pendingMqttEvent = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "MqttClientService disconnected")
            mqttService = null
            isMqttServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceBinding.inflate(inflater, container, false)

        deviceUtil = DeviceUtil()
        userUtil = UserUtil()

        deviceAdapter = DeviceAdapter(requireContext()) { device ->
            val intent = DeviceRender.getDevicePanelIntent(requireContext(), device)
            if (intent != null) {
                startActivity(intent)
            }
        }
        binding.deviceRecyclerView.adapter = deviceAdapter

        binding.swipeRefreshLayout.setOnRefreshListener { loadDevices() }

        binding.addDeviceBtn.setOnClickListener {
            val context = requireContext()
            if (AuthManager.authenticated(context)) {
                val intent = Intent(context, EspMainActivity::class.java).apply {
                    putExtra(KEY_USER_OPEN_ID, userUtil.readUserOpenId(requireActivity()))
                }
                startActivity(intent)
            } else {
                (activity as? MainActivity)?.startAuth()
            }
        }
        return binding.root
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.device_fragment_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_location -> {
                Toast.makeText(context, "Location menu clicked", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        if (!AuthManager.authenticated(requireContext())) {
            (activity as? MainActivity)?.startAuth()
            return
        }
        view?.postDelayed(loadDevicesRunnable, 200)
    }

    override fun onPause() {
        super.onPause()
        view?.removeCallbacks(loadDevicesRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMqttService()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStartMqttServiceEvent(event: StartMqttServiceEvent) {
        if (!AuthManager.authenticated(requireContext())) {
            EventBus.getDefault().post(AuthnFailEvent("User not authenticated"))
            return
        }
        pendingMqttEvent = event
        if (!isMqttServiceBound) {
            val intent = Intent(this.context, MqttClientService::class.java)
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            return
        }
        processMqttConnections(event)
    }

    //When the MQTT connection is established, subscribe to the topics
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMqttConnectedEvent(event: MqttConnectedEvent) {
        deviceAdapter.getSubTopics().forEach { topic ->
            mqttService?.subscribe(event.url, topic)
        }
        deviceAdapter.getPubTopics().forEach { topic ->
            mqttService?.publish(event.url, topic, "{}")
        }
    }

    private fun loadDevices() {
        if (isLoadingDevices || _binding == null || context == null) return
        if (!AuthManager.authenticated(requireContext())) {
            binding.swipeRefreshLayout.isRefreshing = false
            EventBus.getDefault().post(AuthnFailEvent("User not authenticated"))
            return
        }
        binding.swipeRefreshLayout.isRefreshing = true
        val token: String? = AuthManager.authenticatedAccessToken(requireContext())
        if (token != null) {
            isLoadingDevices = true
            deviceUtil.getUserDevices(token, this)
        } else {
            binding.swipeRefreshLayout.isRefreshing = false
            EventBus.getDefault().post(AuthnFailEvent("User not authenticated"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun userDevicesGetSucceed(devicesResp: Map<String, Any>) {
        isLoadingDevices = false
        if (_binding == null) return
        activity?.runOnUiThread {
            binding.swipeRefreshLayout.isRefreshing = false
            val deviceList = devicesResp["content"] as? List<Map<String, Any>>

            val devices = deviceList?.map { deviceMap ->
                val productMap = deviceMap["product"] as? Map<String, Any>
                val thingMap = deviceMap["thing"] as? Map<String, Any>
                Device(
                    name = thingMap?.get("name") as? String ?: "Unnamed device",
                    location = thingMap?.get("location") as? String ?: "Living Room",
                    status = thingMap?.get("status") as? String ?: "Unknown",
                    iconUrl = productMap?.get("icon") as? String ?: "",
                    productId = productMap?.get("id") as? String ?: "",
                    guid = deviceMap["deviceGuid"] as? String ?: "",
                    product = productMap ?: emptyMap(),
                    thing = thingMap ?: emptyMap(),
                    shadow = thingMap?.get("shadow") as? Map<String, Any> ?: emptyMap()
                )
            } ?: emptyList()

            deviceAdapter.submitList(devices)

            val urlToProductIds = mutableMapOf<String, MutableList<String>>()
            val urlToAuthConfig = mutableMapOf<String, Map<String, Any>>()

            devices.forEach { device ->
                val productId = device.productId
                val federateIot = device.product["federateIot"] as? Map<*, *>
                val platform = federateIot?.get("platform") as? String

                val (resolvedUrl, authConfig) = when (platform) {
                    "AwsIotCore" -> {
                        val target = federateIot["target"] as? Map<*, *>
                        val endpoint = target?.get("iotEndpoint") as? String ?: ""
                        val url = "wss://$endpoint/mqtt"
                        val config = mapOf("customAuthz" to true, "customAuthzUsername" to BuildConfig.AWS_IOT_CUSTOM_AUTHZ_USERNAME)
                        url to config
                    }
                    else -> {
                        val url = BuildConfig.MQTT_BROKER_URL
                        val config = mapOf("customAuthz" to false, "customAuthzUsername" to "")
                        url to config
                    }
                }
                urlToProductIds.getOrPut(resolvedUrl) { mutableListOf() }.add(productId)
                urlToAuthConfig[resolvedUrl] = authConfig
            }

            EventBus.getDefault().post(StartMqttServiceEvent(
                urlToProductIds = urlToProductIds,
                urlToAuthConfig = urlToAuthConfig,
                subTopics = deviceAdapter.getSubTopics(),
                pubTopics = deviceAdapter.getPubTopics()
            ))
        }
    }

    override fun userDevicesGetFail() {
        isLoadingDevices = false;
        if (_binding == null) return
        activity?.runOnUiThread {
            binding.swipeRefreshLayout.isRefreshing = false
            Toast.makeText(context, "Can't get devices", Toast.LENGTH_SHORT).show()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onShadowUpdateAcceptedEvent(event: ShadowUpdateAcceptedEvent) {
        if (_binding == null) return
        val guid = event.deviceGuid
        val shadow = event.json?.toMap()
        val reported = (shadow?.get("state") as? Map<*, *>)?.get("reported") as? Map<*, *>
        if (reported?.containsKey("status") == true) {
            val newStatus = reported["status"]?.toString() ?: "Unknown"
            val updatedIndex = deviceAdapter.updateDeviceState(guid, newStatus, shadow)
            if (updatedIndex != -1) {
                val viewHolder = binding.deviceRecyclerView.findViewHolderForAdapterPosition(updatedIndex) as? DeviceAdapter.DeviceViewHolder
                viewHolder?.bindProductSpecificUI(deviceAdapter.devices[updatedIndex])
                viewHolder?.updateStatusIcon(newStatus)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onShadowGetAcceptedEvent(event: ShadowGetAcceptedEvent) {
        if (_binding == null) return
        val guid = event.deviceGuid
        val shadow = event.json?.toMap()
        val reported = (shadow?.get("state") as? Map<*, *>)?.get("reported") as? Map<*, *>
        if (reported?.containsKey("status") == true) {
            val newStatus = reported["status"]?.toString() ?: "Unknown"
            val updatedIndex = deviceAdapter.updateDeviceState(guid, newStatus, shadow)
            if (updatedIndex != -1) {
                val viewHolder = binding.deviceRecyclerView.findViewHolderForAdapterPosition(updatedIndex) as? DeviceAdapter.DeviceViewHolder
                viewHolder?.bindProductSpecificUI(deviceAdapter.devices[updatedIndex])
                viewHolder?.updateStatusIcon(newStatus)
            }
        }
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keysItr: Iterator<String> = this.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value: Any = this.get(key)
            if (value is JSONObject) value = value.toMap()
            map[key] = value
        }
        return map
    }

    private fun processMqttConnections(event: StartMqttServiceEvent) {
        val context = context ?: return
        val idToken = AuthManager.authenticatedIdToken(context) ?: return

        event.urlToProductIds.forEach { (url, productIds) ->
            if (isMqttServiceBound) {
                val authConfig = event.urlToAuthConfig[url]
                val useAuthz = authConfig?.get("customAuthz") as? Boolean ?: false
                val authzUser = authConfig?.get("customAuthzUsername") as? String ?: ""
                MqttClientService.startService(
                    context = context,
                    idToken = idToken,
                    mqttUrl = url,
                    customAuthz = useAuthz,
                    customAuthzUserName = authzUser
                )
                val relevantSubTopics = event.subTopics.filter { topic -> productIds.any { pid -> topic.startsWith(pid) } }
                val relevantPubTopics = event.pubTopics.filter { topic -> productIds.any { pid -> topic.startsWith(pid) } }

                relevantSubTopics.forEach { topic -> mqttService?.subscribe(url, topic) }
                relevantPubTopics.forEach { topic -> mqttService?.publish(url, topic, "{}") }
            }
        }
    }

    private fun stopMqttService() {
        if (isMqttServiceBound) {
            requireContext().unbindService(serviceConnection)
            MqttClientService.stopService(requireContext())
            isMqttServiceBound = false
        }
    }
}

class DeviceAdapter(
    private val context: Context,
    private val onDeviceClicked: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    val devices = mutableListOf<Device>()

    fun submitList(newDevices: List<Device>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    fun getSubTopics(): List<String> = devices.flatMap { listOf("${it.productId}/${it.guid}/shadow/update/accepted", "${it.productId}/${it.guid}/shadow/get/accepted") }
    fun getPubTopics(): List<String> = devices.map { "${it.productId}/${it.guid}/shadow/get" }

    fun updateDeviceState(guid: String, newStatus: String, newShadow: Map<String, Any>?): Int {
        val index = devices.indexOfFirst { it.guid == guid }
        if (index != -1) {
            val oldDevice = devices[index]
            val mergedShadow = deepMerge(oldDevice.shadow, newShadow)
            devices[index] = oldDevice.copy(status = newStatus, shadow = mergedShadow)
        }
        return index
    }

    private fun deepMerge(original: Map<String, Any>, new: Map<String, Any>?): Map<String, Any> {
        val result = original.toMutableMap()
        if (new != null) {
            for ((key, value) in new) {
                val originalValue = original[key]
                if (value is Map<*, *> && originalValue is Map<*, *>) {
                    result[key] = deepMerge(originalValue as Map<String, Any>, value as Map<String, Any>)
                } else {
                    result[key] = value
                }
            }
        }
        return result
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceImage: ImageView = itemView.findViewById(R.id.device_image)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.image_progress_bar)
        private val deviceName: TextView = itemView.findViewById(R.id.device_name)
        private val deviceStatusIcon: ImageView = itemView.findViewById(R.id.device_status_icon)
        private val deviceLocation: TextView = itemView.findViewById(R.id.device_location)
        private val shadowPanelContainer: FrameLayout = itemView.findViewById(R.id.device_specific_ui_container)

        fun bind(device: Device, onDeviceClicked: (Device) -> Unit) {
            deviceName.text = device.name
            deviceLocation.text = device.location
            updateStatusIcon(device.status)

            progressBar.visibility = View.VISIBLE
            Picasso.get().load(device.iconUrl).placeholder(R.drawable.placeholder_device_image)
                .error(R.drawable.placeholder_device_image).into(deviceImage, object : Callback {
                override fun onSuccess() { progressBar.visibility = View.GONE }
                override fun onError(e: Exception?) { progressBar.visibility = View.GONE }
            })

            shadowPanelContainer.removeAllViews()
            DeviceRender.renderDeviceShadowPanel(shadowPanelContainer, device)
            itemView.setOnClickListener { onDeviceClicked(device) }
        }

        fun updateStatusIcon(status: String) {
            val statusIconRes = if (status.equals("Online", ignoreCase = true)) R.drawable.ic_baseline_wifi_24 else R.drawable.ic_baseline_wifi_off_24
            deviceStatusIcon.setImageResource(statusIconRes)
        }

        fun bindProductSpecificUI(device: Device) {
            shadowPanelContainer.removeAllViews()
            DeviceRender.renderDeviceShadowPanel(shadowPanelContainer, device)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_card, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position], onDeviceClicked)
    }

    override fun getItemCount() = devices.size
}
