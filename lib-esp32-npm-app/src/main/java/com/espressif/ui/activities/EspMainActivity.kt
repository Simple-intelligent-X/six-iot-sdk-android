package com.espressif.ui.activities

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import com.espressif.AppConstants
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.wifi_provisioning.BuildConfig
import com.espressif.wifi_provisioning.R
import com.espressif.wifi_provisioning.databinding.ActivityEspMainBinding

class EspMainActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = EspMainActivity::class.java.simpleName

        // Request codes
        private const val REQUEST_LOCATION = 1
        private const val REQUEST_ENABLE_BT = 2
    }

    private lateinit var binding: ActivityEspMainBinding

    private var provisionManager: ESPProvisionManager? = null
    private var sharedPreferences: SharedPreferences? = null
    private var deviceType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityEspMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, MODE_PRIVATE)
        provisionManager = ESPProvisionManager.getInstance(applicationContext)
    }

    fun setTitleBar(){
        val toolbar: Toolbar = binding.titleBar.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            // Use the Toolbar's built-in navigation icon which is correctly sized.
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            // We no longer set a custom drawable, allowing the system to use its default.
            setDisplayShowTitleEnabled(false)
        }
        toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back_ios_24dp)
        binding.titleBar.toolbarTitle.text = getString(R.string.title_provision_device)
    }

    override fun onResume() {
        super.onResume()
        deviceType = sharedPreferences!!.getString(AppConstants.KEY_DEVICE_TYPES, AppConstants.DEVICE_TYPE_DEFAULT)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (BuildConfig.isSettingsAllowed) {
            menuInflater.inflate(R.menu.menu_settings, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_LOCATION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (isLocationEnabled) {
                    addDeviceClick()
                }
            }
        }

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth is turned ON, you can provision device now.", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        setTitleBar();
        // Customize the included button layout
        binding.addDeviceBtn.textBtn.text = getString(R.string.btn_provision_device)
        binding.addDeviceBtn.ivArrow.visibility = View.VISIBLE
        binding.addDeviceBtn.layoutBtn.setOnClickListener(addDeviceBtnClickListener)
        
        var version = ""
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        val appVersion = getString(R.string.app_version) + " - v" + version
        binding.tvAppVersion.text = appVersion
    }

    private val addDeviceBtnClickListener: View.OnClickListener = View.OnClickListener @androidx.annotation.RequiresPermission(
        android.Manifest.permission.BLUETOOTH_CONNECT
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!isLocationEnabled) {
                askForLocation()
                return@OnClickListener
            }
        }
        addDeviceClick()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun addDeviceClick() {
        if (BuildConfig.isQrCodeSupported) {
            gotoQrCodeActivity()
        } else {
            if (deviceType == AppConstants.DEVICE_TYPE_BLE || deviceType == AppConstants.DEVICE_TYPE_BOTH) {
                val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                val bleAdapter = bluetoothManager.adapter

                if (!bleAdapter.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    startProvisioningFlow()
                }
            } else {
                startProvisioningFlow()
            }
        }
    }

    private fun startProvisioningFlow() {
        deviceType = sharedPreferences!!.getString(AppConstants.KEY_DEVICE_TYPES, AppConstants.DEVICE_TYPE_DEFAULT)
        val isSec1 = sharedPreferences!!.getBoolean(AppConstants.KEY_SECURITY_TYPE, true)
        val securityType = if (isSec1) 1 else 0

        when (deviceType) {
            AppConstants.DEVICE_TYPE_BLE -> {
                provisionManager!!.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, if (isSec1) ESPConstants.SecurityType.SECURITY_1 else ESPConstants.SecurityType.SECURITY_0)
                goToBLEProvisionLandingActivity(securityType)
            }
            AppConstants.DEVICE_TYPE_SOFTAP -> {
                provisionManager!!.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, if (isSec1) ESPConstants.SecurityType.SECURITY_1 else ESPConstants.SecurityType.SECURITY_0)
                goToWiFiProvisionLandingActivity(securityType)
            }
            else -> {
                val deviceTypes = arrayOf("BLE", "SoftAP")
                AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setTitle(R.string.dialog_msg_device_selection)
                    .setItems(deviceTypes) { dialog, position ->
                        when (position) {
                            0 -> {
                                provisionManager!!.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, if (isSec1) ESPConstants.SecurityType.SECURITY_1 else ESPConstants.SecurityType.SECURITY_0)
                                goToBLEProvisionLandingActivity(securityType)
                            }
                            1 -> {
                                provisionManager!!.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, if (isSec1) ESPConstants.SecurityType.SECURITY_1 else ESPConstants.SecurityType.SECURITY_0)
                                goToWiFiProvisionLandingActivity(securityType)
                            }
                        }
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun askForLocation() {
        AlertDialog.Builder(this)
            .setCancelable(true)
            .setMessage(R.string.dialog_msg_gps)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                startActivityForResult(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_LOCATION)
            }
            .setNegativeButton(R.string.btn_cancel) { dialog, _ -> dialog.cancel() }
            .show()
    }

    private val isLocationEnabled: Boolean
        get() {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    private fun gotoQrCodeActivity() {
        val intent = Intent(this@EspMainActivity, AddDeviceActivity::class.java)
        startActivity(intent)
    }

    private fun goToBLEProvisionLandingActivity(securityType: Int) {
        val intent = Intent(this@EspMainActivity, BLEProvisionLanding::class.java)
        intent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType)
        startActivity(intent)
    }

    private fun goToWiFiProvisionLandingActivity(securityType: Int) {
        val intent = Intent(this@EspMainActivity, ProvisionLanding::class.java)
        intent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType)
        startActivity(intent)
    }
}
