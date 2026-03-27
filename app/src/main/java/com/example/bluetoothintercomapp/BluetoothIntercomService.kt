package com.example.bluetoothintercomapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothIntercomService(private val context: Context, private val handler: Handler) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var secureAcceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var state: Int = STATE_NONE
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private const val TAG = "BluetoothIntercomService"
        private const val APP_NAME = "BluetoothIntercomApp"
        private val MY_UUID_SECURE: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID

        const val STATE_NONE = 0       // we're doing nothing
        const val STATE_LISTEN = 1     // now listening for incoming connections
        const val STATE_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_CONNECTED = 3  // now connected to a remote device

        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5

        const val DEVICE_NAME = "device_name"
        const val TOAST = "toast"
    }

    @Synchronized
    private fun setState(state: Int) {
        Log.d(TAG, "setState() $this.state -> $state")
        this.state = state
        handler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
    }

    @Synchronized
    fun getState(): Int {
        return state
    }

    @Synchronized
    fun start() {
        Log.d(TAG, "start")

        // Cancel any thread attempting to make a connection
        connectThread?.cancel()
        connectThread = null

        // Cancel any thread currently running a connection
        connectedThread?.cancel()
        connectedThread = null

        setState(STATE_LISTEN)

        // Start the thread to listen on a BluetoothServerSocket
        if (secureAcceptThread == null) {
            secureAcceptThread = AcceptThread()
            secureAcceptThread?.start()
        }
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connect to: $device")

        // Cancel any thread attempting to make a connection
        connectThread?.cancel()
        connectThread = null

        // Cancel any thread currently running a connection
        connectedThread?.cancel()
        connectedThread = null

        // Start the thread to connect with the given device
        connectThread = ConnectThread(device)
        connectThread?.start()
        setState(STATE_CONNECTING)
    }

    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        Log.d(TAG, "connected")

        // Cancel the thread that completed the connection
        connectThread?.cancel()
        connectThread = null

        // Cancel any thread currently running a connection
        connectedThread?.cancel()
        connectedThread = null

        // Cancel the accept thread because we only want to connect to one device
        secureAcceptThread?.cancel()
        secureAcceptThread = null

        // Start the thread to manage the connection and perform transmissions
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        // Send the name of the connected device back to the UI Activity
        val msg = handler.obtainMessage(MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(DEVICE_NAME, device.name)
        msg.data = bundle
        handler.sendMessage(msg)

        setState(STATE_CONNECTED)

        // Start SCO audio for earbuds
        startBluetoothSco()
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop")

        connectThread?.cancel()
        connectThread = null

        connectedThread?.cancel()
        connectedThread = null

        secureAcceptThread?.cancel()
        secureAcceptThread = null

        setState(STATE_NONE)

        stopBluetoothSco()
    }

    fun write(out: ByteArray) {
        var r: ConnectedThread?
        synchronized(this) {
            if (state != STATE_CONNECTED) return
            r = connectedThread
        }
        r?.write(out)
    }

    private fun connectionFailed() {
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Unable to connect device")
        msg.data = bundle
        handler.sendMessage(msg)

        // Start the service over to restart listening mode
        this@BluetoothIntercomService.start()
    }

    private fun connectionLost() {
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Device connection was lost")
        msg.data = bundle
        handler.sendMessage(msg)

        // Start the service over to restart listening mode
        this@BluetoothIntercomService.start()
    }

    private fun checkBluetoothPermission(permission: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        // Below Android 12, BLUETOOTH and BLUETOOTH_ADMIN are granted at install time
        return true
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            if (!checkBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                return@lazy null
            }
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID_SECURE)
        }

        override fun run() {
            Log.d(TAG, "Secure AcceptThread started")
            name = "AcceptThreadSecure"

            var socket: BluetoothSocket?

            while (this@BluetoothIntercomService.state != STATE_CONNECTED) {
                socket = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Secure accept() failed", e)
                    break
                }

                if (socket != null) {
                    synchronized(this@BluetoothIntercomService) {
                        when (this@BluetoothIntercomService.state) {
                            STATE_LISTEN, STATE_CONNECTING -> {
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.remoteDevice)
                            }
                            STATE_NONE, STATE_CONNECTED -> {
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }
                            }
                        }
                    }
                }
            }
            Log.i(TAG, "End AcceptThread")
        }

        fun cancel() {
            Log.d(TAG, "Cancel AcceptThread")
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Close of server failed", e)
            }
        }
    }

    private inner class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            if (!checkBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                return@lazy null
            }
            mmDevice.createRfcommSocketToServiceRecord(MY_UUID_SECURE)
        }

        override fun run() {
            Log.d(TAG, "ConnectThread started")
            name = "ConnectThread"

            // Always cancel discovery because it will slow down a connection
            if (!checkBluetoothPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
                // On older versions we still want to try cancelDiscovery
            }
            
            // For discovery, we need location permission on Android 6.0 to 11
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "ACCESS_FINE_LOCATION permission not granted for discovery")
                }
            }

            bluetoothAdapter?.cancelDiscovery()

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception.
                mmSocket?.connect()
            } catch (connectException: IOException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket?.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "Could not close the client socket", closeException)
                }
                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothIntercomService) {
                connectThread = null
            }

            // Start the connected thread
            connected(mmSocket!!, mmDevice)
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        override fun run() {
            Log.i(TAG, "ConnectedThread started")
            var bytes: Int // bytes returned from read()

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(mmBuffer)

                    // Send the obtained bytes to the UI Activity
                    handler.obtainMessage(MESSAGE_READ, bytes, -1, mmBuffer)
                        .sendToTarget()
                } catch (e: IOException) {
                    Log.e(TAG, "Disconnected", e)
                    connectionLost()
                    break
                }
            }
        }

        fun write(buffer: ByteArray) {
            try {
                mmOutStream.write(buffer)
                // Share the sent message back to the UI Activity
                handler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
                connectionLost()
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Close() of connect socket failed", e)
            }
        }
    }

    private fun startBluetoothSco() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "MODIFY_AUDIO_SETTINGS permission not granted")
            return
        }
        if (!audioManager.isBluetoothScoAvailableOffCall) {
            Log.e(TAG, "SCO audio not available off call")
            return
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isBluetoothScoOn = true
        audioManager.startBluetoothSco()
        Log.d(TAG, "startBluetoothSco called")
    }

    private fun stopBluetoothSco() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "MODIFY_AUDIO_SETTINGS permission not granted")
            return
        }
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        Log.d(TAG, "stopBluetoothSco called")
    }
}
