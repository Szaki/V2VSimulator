package com.szaki.v2vsimulator

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    private val PERMISSION_REQUESTS = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        val address = BluetoothAdapter.getDefaultAdapter().address
        if (address == "02:00:00:00:00:00")
            mac.setText("")
        else mac.setText(address)

        start.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
                .putExtra("width", width.text.toString().toInt())
                .putExtra("height", height.text.toString().toInt())
                .putExtra("fps", fps.text.toString().toFloat())
                .putExtra("mac", mac.text.toString())
            startActivity(intent)
        }

        if (!allPermissionsGranted()) {
            getRuntimePermissions()
        }
    }

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

    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(this, permission!!)) {
                return false
            }
        }
        return true
    }

    private fun getRuntimePermissions() {
        val allNeededPermissions = arrayListOf<String?>()
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(this, permission!!)) {
                allNeededPermissions.add(permission)
            }
        }

        if (allNeededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS
            )
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
            return true
        return false
    }
}
