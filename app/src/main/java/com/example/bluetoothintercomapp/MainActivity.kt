package com.example.bluetoothintercomapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var discoverButton: Button
    private lateinit var deviceListView: ListView
    private lateinit var toggleIntercomButton: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var devicesArrayAdapter: ArrayAdapter<String>
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    private var intercomService: BluetoothIntercomService? = null
    private var audioHandler: AudioHandler? = null

    private var isIntercomActive = false

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All required permissions granted")
            setupBluetooth()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        discoverButton = findViewById(R.id.discoverButton)
        deviceListView = findViewById(R.id.deviceListView)
        toggleIntercomButton = findViewById(R.id.toggleIntercomButton)

        devicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        deviceListView.adapter = devicesArrayAdapter

        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val device = discoveredDevices[position]
            intercomService?.connect(device)
        }

        discoverButton.setOnClickListener { startDiscovery() }
        toggleIntercomButton.setOnClickListener { toggleIntercom() }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            )
        }
        requestPermissionsLauncher.launch(permissions)
    }

    private fun setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            initIntercomService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                initIntercomService()
            } else {
                Toast.makeText(this, "Bluetooth must be enabled to use this app", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initIntercomService() {
        intercomService = BluetoothIntercomService(this, handler)
        audioHandler = AudioHandler { audioData ->
            intercomService?.write(audioData)
        }
        intercomService?.start()
    }

    private fun startDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "BLUETOOTH_SCAN permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }

        devicesArrayAdapter.clear()
        discoveredDevices.clear()
        bluetoothAdapter?.startDiscovery()
        Toast.makeText(this, "Starting device discovery...", Toast.LENGTH_SHORT).show()
    }

    private fun toggleIntercom() {
        if (intercomService?.getState() != BluetoothIntercomService.STATE_CONNECTED) {
            Toast.makeText(this, "Not connected to any device", Toast.LENGTH_SHORT).show()
            return
        }

        if (isIntercomActive) {
            audioHandler?.stopRecording()
            audioHandler?.stopPlaying()
            toggleIntercomButton.text = "Start Intercom"
            isIntercomActive = false
            Toast.makeText(this, "Intercom stopped", Toast.LENGTH_SHORT).show()
        } else {
            audioHandler?.startRecording()
            audioHandler?.startPlaying()
            toggleIntercomButton.text = "Stop Intercom"
            isIntercomActive = true
            Toast.makeText(this, "Intercom started", Toast.LENGTH_SHORT).show()
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (ActivityCompat.checkSelfPermission(context!!, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            return
                        }
                        if (it.name != null && !discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                            devicesArrayAdapter.add("${it.name}\n${it.address}")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Toast.makeText(context, "Discovery finished", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                BluetoothIntercomService.MESSAGE_STATE_CHANGE -> {
                    when (msg.arg1) {
                        BluetoothIntercomService.STATE_NONE -> statusTextView.text = "Not Connected"
                        BluetoothIntercomService.STATE_LISTEN -> statusTextView.text = "Listening..."
                        BluetoothIntercomService.STATE_CONNECTING -> statusTextView.text = "Connecting..."
                        BluetoothIntercomService.STATE_CONNECTED -> statusTextView.text = "Connected to: ${msg.data.getString(BluetoothIntercomService.DEVICE_NAME)}"
                    }
                }
                BluetoothIntercomService.MESSAGE_WRITE -> {
                    // Optionally handle written data (e.g., for debugging)
                }
                BluetoothIntercomService.MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    val readBytes = msg.arg1
                    audioHandler?.playAudio(readBuf.copyOf(readBytes))
                }
                BluetoothIntercomService.MESSAGE_DEVICE_NAME -> {
                    val connectedDeviceName = msg.data.getString(BluetoothIntercomService.DEVICE_NAME)
                    Toast.makeText(applicationContext, "Connected to $connectedDeviceName", Toast.LENGTH_SHORT).show()
                    statusTextView.text = "Connected to: $connectedDeviceName"
                }
                BluetoothIntercomService.MESSAGE_TOAST -> {
                    Toast.makeText(applicationContext, msg.data.getString(BluetoothIntercomService.TOAST), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoveryReceiver, filter)

        if (intercomService != null) {
            if (intercomService?.getState() == BluetoothIntercomService.STATE_NONE) {
                intercomService?.start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(discoveryReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        intercomService?.stop()
        audioHandler?.release()
    }
}
