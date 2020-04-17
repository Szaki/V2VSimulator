package com.szaki.v2vsimulator.mlkit

import android.graphics.Bitmap
import android.support.annotation.GuardedBy
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.szaki.v2vsimulator.camera.CameraView
import com.szaki.v2vsimulator.misc.BitmapUtils
import com.szaki.v2vsimulator.misc.FrameMetadata
import java.nio.ByteBuffer

abstract class VisionProcessorBase<T> {

    @GuardedBy("this")
    private var latestImage: ByteBuffer? = null

    @GuardedBy("this")
    private var latestImageMetaData: FrameMetadata? = null

    @GuardedBy("this")
    private var processingImage: ByteBuffer? = null

    @GuardedBy("this")
    private var processingMetaData: FrameMetadata? = null

    @Synchronized
    fun process(
        data: ByteBuffer,
        frameMetadata: FrameMetadata,
        cameraView: CameraView
    ) {
        latestImage = data
        latestImageMetaData = frameMetadata
        if (processingImage == null && processingMetaData == null) {
            processLatestImage(cameraView)
        }
    }

    fun process(bitmap: Bitmap, cameraView: CameraView) {
        detectInVisionImage(
            null,
            FirebaseVisionImage.fromBitmap(bitmap),
            null,
            cameraView
        )
    }

    @Synchronized
    private fun processLatestImage(cameraView: CameraView) {
        processingImage = latestImage
        processingMetaData = latestImageMetaData
        latestImage = null
        latestImageMetaData = null
        if (processingImage != null && processingMetaData != null) {
            processImage(processingImage!!, processingMetaData!!, cameraView)
        }
    }

    private fun processImage(
        data: ByteBuffer,
        frameMetadata: FrameMetadata,
        cameraView: CameraView
    ) {
        val metadata = FirebaseVisionImageMetadata.Builder()
            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
            .setWidth(frameMetadata.width)
            .setHeight(frameMetadata.height)
            .setRotation(frameMetadata.rotation)
            .build()

        val bitmap = BitmapUtils.getBitmap(data, frameMetadata)
        detectInVisionImage(
            bitmap, FirebaseVisionImage.fromByteBuffer(data, metadata), frameMetadata,
            cameraView
        )
    }

    private fun detectInVisionImage(
        originalCameraImage: Bitmap?,
        image: FirebaseVisionImage,
        metadata: FrameMetadata?,
        cameraView: CameraView
    ) {
        detectInImage(image)
            .addOnSuccessListener { results ->
                onSuccess(
                    originalCameraImage, results,
                    metadata!!,
                    cameraView
                )
                processLatestImage(cameraView)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    protected abstract fun detectInImage(image: FirebaseVisionImage): Task<T>

    protected abstract fun onSuccess(
        originalCameraImage: Bitmap?,
        results: T,
        frameMetadata: FrameMetadata,
        cameraView: CameraView
    )

    protected abstract fun onFailure(e: Exception)
}
