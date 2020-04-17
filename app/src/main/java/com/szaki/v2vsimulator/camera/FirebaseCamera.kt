package com.szaki.v2vsimulator.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.support.annotation.RequiresPermission
import android.view.Surface
import android.view.WindowManager
import com.google.android.gms.common.images.Size
import com.szaki.v2vsimulator.misc.FrameMetadata
import com.szaki.v2vsimulator.mlkit.BarcodeScanningProcessor
import java.io.IOException
import java.lang.Thread.State
import java.nio.ByteBuffer
import java.util.*

@SuppressLint("MissingPermission")
class FirebaseCamera(
    private val activity: Activity, private val cameraView: CameraView,
    private val requestedPreviewWidth: Int,
    private val requestedPreviewHeight: Int, private val requestedFps: Float
) {
    private val processingRunnable: FrameProcessingRunnable
    private val processorLock = Object()

    private val bytesToByteBuffer = IdentityHashMap<ByteArray, ByteBuffer>()
    private var camera: Camera? = null
    private var rotation: Int = 0
    var previewSize: Size? = null
    private var dummySurfaceTexture: SurfaceTexture? = null
    private var usingSurfaceTexture: Boolean = false
    private var processingThread: Thread? = null
    var processor: BarcodeScanningProcessor? = null

    init {
        cameraView.postInvalidate()
        processingRunnable = FrameProcessingRunnable()

        if (Camera.getNumberOfCameras() == 1) {
            val cameraInfo = CameraInfo()
            Camera.getCameraInfo(0, cameraInfo)
        }
    }

    fun release() {
        synchronized(processorLock) {
            stop()
            processingRunnable.release()
            cleanScreen()
            processor?.stop()
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.CAMERA)
    @Synchronized
    @Throws(IOException::class)
    fun start(): FirebaseCamera {
        if (camera == null) {
            camera = createCamera()
            dummySurfaceTexture = SurfaceTexture(DUMMY_TEXTURE_NAME)
            camera!!.setPreviewTexture(dummySurfaceTexture)
            usingSurfaceTexture = true
            camera!!.startPreview()
            processingThread = Thread(processingRunnable)
            processingRunnable.setActive(true)
            processingThread!!.start()
        }
        return this
    }

    @Synchronized
    fun stop() {
        processingRunnable.setActive(false)
        try {
            processingThread?.join()
        } catch (e: InterruptedException) {
            print(e.stackTrace)
        }
        processingThread = null

        camera?.stopPreview()
        camera?.setPreviewCallbackWithBuffer(null)
        try {
            if (usingSurfaceTexture)
                camera?.setPreviewTexture(null)
            else camera?.setPreviewDisplay(null)
        } catch (e: Exception) {
            print(e.stackTrace)
        }
        camera?.release()
        camera = null

        bytesToByteBuffer.clear()
    }

    @SuppressLint("InlinedApi")
    @Throws(IOException::class)
    private fun createCamera(): Camera {
        val requestedCameraId =
            getIdForRequestedCamera(CameraInfo.CAMERA_FACING_BACK)
        if (requestedCameraId == -1) {
            throw IOException("Could not find requested camera.")
        }
        val camera = Camera.open(requestedCameraId)

        val sizePair = selectSizePair(
            camera,
            requestedPreviewWidth,
            requestedPreviewHeight
        )
            ?: throw IOException("Couldn't find suitable preview size.")
        val pictureSize = sizePair.picture
        previewSize = sizePair.preview

        val previewFpsRange = selectPreviewFpsRange(
            camera,
            requestedFps
        )
            ?: throw IOException("Couldn't find suitable preview frames per second range.")

        val parameters = camera.parameters

        parameters?.setPictureSize(pictureSize!!.width, pictureSize.height)
        parameters.setPreviewSize(previewSize!!.width, previewSize!!.height)
        parameters.setPreviewFpsRange(
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
        )
        parameters.previewFormat = ImageFormat.NV21

        setRotation(camera, parameters, requestedCameraId)

        if (parameters
                .supportedFocusModes
                .contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
        ) {
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        } else {
            print("AF isn't supported")
        }

        camera.parameters = parameters
        camera.setPreviewCallbackWithBuffer(CameraPreviewCallback())
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))

        return camera
    }

    private fun setRotation(camera: Camera, parameters: Camera.Parameters, cameraId: Int) {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var degrees = 0
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        val cameraInfo = CameraInfo()
        Camera.getCameraInfo(cameraId, cameraInfo)
        val angle = (cameraInfo.orientation - degrees + 360) % 360
        this.rotation = angle / 90
        camera.setDisplayOrientation(angle)
        parameters.setRotation(angle)
    }

    @SuppressLint("InlinedApi")
    private fun createPreviewBuffer(previewSize: Size): ByteArray {
        val bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21)
        val sizeInBits = (previewSize.height * previewSize.width * bitsPerPixel).toLong()
        val bufferSize = Math.ceil(sizeInBits / 8.0).toInt() + 1
        val byteArray = ByteArray(bufferSize)
        val buffer = ByteBuffer.wrap(byteArray)
        if (!buffer.hasArray() || !buffer.array().equals(byteArray))
            throw IllegalStateException("Couldn't create valid buffer for camera")

        bytesToByteBuffer[byteArray] = buffer
        return byteArray
    }

    fun setScanningProcessor(processor: BarcodeScanningProcessor?) {
        synchronized(processorLock) {
            cleanScreen()
            processor?.stop()
            this.processor = processor
        }
    }

    private fun cleanScreen() {
        cameraView.postInvalidate()
    }

    internal class SizePair constructor(
        previewSize: android.hardware.Camera.Size,
        pictureSize: android.hardware.Camera.Size?
    ) {
        val preview: Size = Size(previewSize.width, previewSize.height)
        var picture: Size? = null

        init {
            if (pictureSize != null)
                picture = Size(pictureSize.width, pictureSize.height)
        }
    }

    private inner class CameraPreviewCallback : Camera.PreviewCallback {
        override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            processingRunnable.setNextFrame(data, camera)
        }
    }

    private inner class FrameProcessingRunnable : Runnable {

        private val lock = Object()
        private var active = true

        private var pendingFrameData: ByteBuffer? = null

        @SuppressLint("Assert")
        internal fun release() {
            assert(processingThread?.state == State.TERMINATED)
        }

        internal fun setActive(active: Boolean) {
            synchronized(lock) {
                this.active = active
                lock.notifyAll()
            }
        }

        internal fun setNextFrame(data: ByteArray, camera: Camera) {
            synchronized(lock) {
                if (pendingFrameData != null) {
                    camera.addCallbackBuffer(pendingFrameData!!.array())
                    pendingFrameData = null
                }
                if (!bytesToByteBuffer.containsKey(data))
                    return

                pendingFrameData = bytesToByteBuffer[data]
                lock.notifyAll()
            }
        }

        @SuppressLint("InlinedApi")
        override fun run() {
            var data: ByteBuffer
            while (true) {
                synchronized(lock) {
                    while (active && pendingFrameData == null) {
                        try {
                            lock.wait()
                        } catch (e: InterruptedException) {
                            print("Frame processing interrupted")
                            return
                        }
                    }
                    if (!active)
                        return
                    data = pendingFrameData!!
                    pendingFrameData = null
                }

                try {
                    synchronized(processorLock) {
                        processor!!.process(
                            data,
                            FrameMetadata.Builder()
                                .setWidth(previewSize!!.width)
                                .setHeight(previewSize!!.height)
                                .setRotation(rotation)
                                .setCameraFacing(CameraInfo.CAMERA_FACING_BACK)
                                .build(),
                            cameraView
                        )
                    }
                } catch (t: Throwable) {
                    print(t.message)
                } finally {
                    camera!!.addCallbackBuffer(data.array())
                }
            }
        }
    }

    companion object {
        private val DUMMY_TEXTURE_NAME = 100
        private val ASPECT_RATIO_TOLERANCE = 0.01f

        private fun getIdForRequestedCamera(facing: Int): Int {
            val cameraInfo = CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == facing) {
                    return i
                }
            }
            return -1
        }

        private fun selectSizePair(camera: Camera, desiredWidth: Int, desiredHeight: Int): SizePair? {
            val validPreviewSizes =
                generateValidPreviewSizeList(camera)

            var selectedPair: SizePair? = null
            var minDiff = Integer.MAX_VALUE
            for (sizePair in validPreviewSizes) {
                val size = sizePair.preview
                val diff = Math.abs(size.width - desiredWidth) + Math.abs(size.height - desiredHeight)
                if (diff < minDiff) {
                    selectedPair = sizePair
                    minDiff = diff
                }
            }
            return selectedPair
        }

        private fun generateValidPreviewSizeList(camera: Camera): List<SizePair> {
            val parameters = camera.parameters
            val supportedPreviewSizes = parameters.supportedPreviewSizes
            val supportedPictureSizes = parameters.supportedPictureSizes
            val validPreviewSizes = ArrayList<SizePair>()
            for (previewSize in supportedPreviewSizes) {
                val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()

                for (pictureSize in supportedPictureSizes) {
                    val pictureAspectRatio = pictureSize.width.toFloat() / pictureSize.height.toFloat()
                    if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                        validPreviewSizes.add(
                            SizePair(
                                previewSize,
                                pictureSize
                            )
                        )
                        break
                    }
                }
            }
            if (validPreviewSizes.isEmpty()) {
                for (previewSize in supportedPreviewSizes) {
                    validPreviewSizes.add(
                        SizePair(
                            previewSize,
                            null
                        )
                    )
                }
            }

            return validPreviewSizes
        }

        @SuppressLint("InlinedApi")
        private fun selectPreviewFpsRange(camera: Camera, desiredPreviewFps: Float): IntArray? {
            val desiredPreviewFpsScaled = (desiredPreviewFps * 1000.0f).toInt()

            var selectedFpsRange: IntArray? = null
            var minDiff = Integer.MAX_VALUE
            val previewFpsRangeList = camera.parameters.supportedPreviewFpsRange
            for (range in previewFpsRangeList) {
                val deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                val deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
                val diff = Math.abs(deltaMin) + Math.abs(deltaMax)
                if (diff < minDiff) {
                    selectedFpsRange = range
                    minDiff = diff
                }
            }
            return selectedFpsRange
        }
    }
}
