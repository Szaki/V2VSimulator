package com.szaki.v2vsimulator.connectivity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import com.szaki.v2vsimulator.R
import com.szaki.v2vsimulator.ScannerActivity
import com.szaki.v2vsimulator.camera.CameraView
import com.szaki.v2vsimulator.misc.AccelerationSender
import com.szaki.v2vsimulator.misc.Logger
import java.io.IOException
import java.util.*

class BluetoothHandler(val context: Context, val cameraView: CameraView) {
    val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val btconnected = MediaPlayer.create(context, R.raw.btconnected)
    private val btdisconnected = MediaPlayer.create(context, R.raw.btdisconnected)
    private val logger = Logger()
    var server: Server? = null
    var client: Client? = null

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private val APP_UUID = UUID.fromString("18a64e6f-ca89-4b5d-bfe2-5862d0294933")
        private const val APP_NAME = "com.szaki.v2vsimulator"
    }

    init {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            (context as ScannerActivity).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    inner class Server : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                APP_NAME,
                APP_UUID
            )
        }

        override fun run() {
            while (true) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    print(e.localizedMessage)
                    null
                }
                socket?.also {
                    logger.log("BT_CONNECTED,${System.currentTimeMillis()}\n")
                    (context as ScannerActivity).runOnUiThread {
                        Toast.makeText(context, "Received a connection!", Toast.LENGTH_SHORT).show()
                    }
                    val connection = BluetoothConnection(it)
                    connection.start()
                    val acc = AccelerationSender(context, connection, cameraView)
                    connection.join()
                    connection.cancel()
                    logger.log("BT_DISCONNECTED,${System.currentTimeMillis()}\n")
                    context.runOnUiThread {
                        Toast.makeText(context, "Lost the connection!", Toast.LENGTH_SHORT).show()
                    }
                    acc.cancel()
                }
            }
        }

        fun cancel() {
            logger.log("BT_DISCONNECTING,${System.currentTimeMillis()}\n")
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                print(e.localizedMessage)
            }
        }
    }

    inner class Client(private val address: String) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter.getRemoteDevice(address).createInsecureRfcommSocketToServiceRecord(APP_UUID)
        }

        override fun run() {
            mmSocket?.use { socket ->
                logger.log("BT_CONNECTING,${System.currentTimeMillis()}\n")
                try {
                    socket.connect()
                } catch (ex: Exception) {
                    (context as ScannerActivity).runOnUiThread {
                        Toast.makeText(context, "Failed to connect to $address!", Toast.LENGTH_SHORT).show()
                    }
                    return@use
                }
                logger.log("BT_CONNECTED,${System.currentTimeMillis()}\n")
                (context as ScannerActivity).runOnUiThread {
                    Toast.makeText(context, "Connected to $address!", Toast.LENGTH_SHORT).show()
                }
                btconnected.start()
                val connection = BluetoothConnection(socket)
                connection.start()
                connection.join()
                connection.cancel()
                logger.log("BT_DISCONNECTED,${System.currentTimeMillis()}\n")
                context.runOnUiThread {
                    Toast.makeText(context, "Disconnected!", Toast.LENGTH_SHORT).show()
                }
                btdisconnected.start()
            }
        }

        fun cancel() {
            logger.log("BT_DISCONNECTING,${System.currentTimeMillis()}\n")
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                print(e.localizedMessage)
            }
        }
    }
}