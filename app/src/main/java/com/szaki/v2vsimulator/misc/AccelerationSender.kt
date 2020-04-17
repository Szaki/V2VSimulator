package com.szaki.v2vsimulator.misc

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.szaki.v2vsimulator.camera.CameraView
import com.szaki.v2vsimulator.connectivity.BluetoothConnection
import kotlin.math.round

class AccelerationSender(context: Context, val bluetoothConnection: BluetoothConnection, val cameraView: CameraView) :
    SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    init {
        sensorManager.registerListener(this, sensor, 2)
    }

    private fun approx(f: Float) = round(f * 1000) / 1000

    override fun onSensorChanged(event: SensorEvent) {
        val data = "${approx(event.values[0])},${approx(event.values[1])},${approx(event.values[2])}"
        if (event.values[0] < -3f || event.values[1] < -3f || event.values[2] < -3f) {
            cameraView.stop()
            bluetoothConnection.send("BT_STOP")
        } else bluetoothConnection.send(data)
    }

    fun cancel() {
        sensorManager.unregisterListener(this, sensor)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}