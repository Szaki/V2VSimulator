package com.szaki.v2vsimulator.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.szaki.v2vsimulator.ScannerActivity
import com.szaki.v2vsimulator.camera.CameraView
import com.szaki.v2vsimulator.connectivity.BluetoothHandler
import com.szaki.v2vsimulator.misc.BarcodeInfo
import com.szaki.v2vsimulator.misc.FrameMetadata
import com.szaki.v2vsimulator.misc.Logger
import java.io.IOException

class BarcodeScanningProcessor(
    val context: Context,
    val textView: TextView,
    val bluetoothHandler: BluetoothHandler,
    val height: Int
) :
    VisionProcessorBase<List<FirebaseVisionBarcode>>() {

    private var disconst = 0f
    private var currentBarcode = BarcodeInfo()
    private val logger = Logger()

    private val detector: FirebaseVisionBarcodeDetector by lazy {
        FirebaseVision.getInstance().visionBarcodeDetector
    }

    fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            print(e.stackTrace)
        }
    }

    fun setConst() {
        if (currentBarcode.size != 0) {
            disconst = currentBarcode.size / 2.0f
            (context as ScannerActivity).runOnUiThread {
                Toast.makeText(context, "Distance set", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearBarcodes() {
        currentBarcode = BarcodeInfo()
        bluetoothHandler.client?.cancel()
    }

    override fun detectInImage(image: FirebaseVisionImage): Task<List<FirebaseVisionBarcode>> {
        return detector.detectInImage(image)
    }

    override fun onSuccess(
        originalCameraImage: Bitmap?,
        results: List<FirebaseVisionBarcode>,
        frameMetadata: FrameMetadata,
        cameraView: CameraView
    ) {
        cameraView.camera = originalCameraImage
        val time = System.currentTimeMillis()
        results.forEach {
            val barcodeInfo = BarcodeInfo(it, disconst)
            if (barcodeInfo.content == "${currentBarcode.content}:STOP") {
                logger.log("QR_STOP,${barcodeInfo.lastSeen}\n")
                currentBarcode.content = barcodeInfo.content
            }
            if (currentBarcode.content == "${barcodeInfo.content}:STOP") {
                currentBarcode.content = barcodeInfo.content
            }
            if (barcodeInfo.content?.length == 17) {
                if (barcodeInfo.content != currentBarcode.content) {
                    if (barcodeInfo.distance in 0f..currentBarcode.distance && barcodeInfo.center.x in (height / 4).rangeTo(
                            3 * (height / 4)
                        )
                    ) {
                        currentBarcode = barcodeInfo
                        bluetoothHandler.client?.cancel()
                        bluetoothHandler.client = bluetoothHandler.Client(currentBarcode.content!!)
                        bluetoothHandler.client?.start()
                    }
                } else currentBarcode = barcodeInfo
            }
        }
        if (time - currentBarcode.lastSeen > 5000)
            clearBarcodes()
        textView.text = currentBarcode.toString()
        textView.append(time.toString())
        when {
            currentBarcode.center.x in 0.rangeTo((height / 4) - 1) -> cameraView.direction = -2
            currentBarcode.center.x in (height / 4).rangeTo((3 * height / 8)) -> cameraView.direction = -1
            currentBarcode.center.x in (5 * height / 8).rangeTo(3 * (height / 4)) -> cameraView.direction = 1
            currentBarcode.center.x in (3 * height / 4 + 1).rangeTo(height) -> cameraView.direction = 2
            else -> cameraView.direction = 0
        }
        cameraView.postInvalidate()
    }

    override fun onFailure(e: Exception) {
        print(e.stackTrace)
    }
}