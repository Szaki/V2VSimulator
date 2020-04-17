package com.szaki.v2vsimulator

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import com.google.firebase.ml.common.FirebaseMLException
import com.szaki.v2vsimulator.camera.FirebaseCamera
import com.szaki.v2vsimulator.connectivity.BluetoothHandler
import com.szaki.v2vsimulator.misc.Logger
import com.szaki.v2vsimulator.mlkit.BarcodeScanningProcessor
import kotlinx.android.synthetic.main.activity_scanner.*
import net.glxn.qrgen.android.QRCode
import java.io.IOException

class ScannerActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var bluetoothHandler: BluetoothHandler
    private var firebaseCamera: FirebaseCamera? = null
    private val logger = Logger()
    private var width: Int = 0
    private var height: Int = 0
    private var fps: Float = 0f
    private var mac: String? = ""

    private val requiredPermissions: Array<String?>
        get() {
            return try {
                val info = this.packageManager
                    .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
                val ps = info.requestedPermissions
                if (ps != null && ps.isNotEmpty()) {
                    ps
                } else {
                    arrayOfNulls(0)
                }
            } catch (e: Exception) {
                arrayOfNulls(0)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_scanner)

        bluetoothHandler = BluetoothHandler(this, cameraView)
    }

    override fun onStart() {
        width = intent.extras!!.getInt("width")
        height = intent.extras!!.getInt("height")
        fps = intent.extras!!.getFloat("fps")
        mac = intent.extras!!.getString("mac")

        mac?.let {
            if (it.split(':').size == 6 && it.length == 17) {
                cameraView.addresscode = QRCode.from(it).withSize(1200, 1200).bitmap()
                cameraView.stopcode = QRCode.from("$it:STOP").withSize(1200, 1200).bitmap()
            }
        }

        logger.startSession()

        if (allPermissionsGranted()) {
            createCameraSource()
        } else getRuntimePermissions()
        super.onStart()
        bluetoothHandler.server = bluetoothHandler.Server()
        bluetoothHandler.server?.start()
    }

    private fun createCameraSource() {
        firebaseCamera = FirebaseCamera(this, cameraView, width, height, fps)
        try {
            firebaseCamera?.setScanningProcessor(BarcodeScanningProcessor(this, log, bluetoothHandler, height))
        } catch (e: FirebaseMLException) {
            print(e.stackTrace)
        }
    }

    private fun startCameraSource() {
        firebaseCamera?.let {
            try {
                preview?.start(firebaseCamera!!, cameraView)
            } catch (e: IOException) {
                print(e.stackTrace)
                firebaseCamera?.release()
                firebaseCamera = null
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        startCameraSource()
    }

    override fun onStop() {
        super.onStop()
        bluetoothHandler.server?.cancel()
        bluetoothHandler.client?.cancel()
    }

    override fun onPause() {
        super.onPause()
        preview?.stop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        firebaseCamera?.release()
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(this, permission!!))
                return false
        }
        return true
    }

    private fun getRuntimePermissions() {
        val allNeededPermissions = arrayListOf<String>()
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(this, permission!!)) {
                allNeededPermissions.add(permission)
            }
        }

        if (!allNeededPermissions.isEmpty())
            ActivityCompat.requestPermissions(
                this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS
            )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (allPermissionsGranted())
            createCameraSource()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val PERMISSION_REQUESTS = 1

        private fun isPermissionGranted(context: Context, permission: String): Boolean {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
                return true
            return false
        }
    }
}
