package com.szaki.v2vsimulator.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup

import java.io.IOException


class FirebaseCameraPreview(context: Context, attrs: AttributeSet) : ViewGroup(context, attrs) {

    private val surfaceView: SurfaceView
    private var startRequested: Boolean
    private var surfaceAvailable: Boolean
    private var firebaseCamera: FirebaseCamera? = null
    private var cameraView: CameraView? = null

    init {
        startRequested = false
        surfaceAvailable = false

        surfaceView = SurfaceView(context)
        surfaceView.holder.addCallback(SurfaceCallback())
        addView(surfaceView)
    }

    @Throws(IOException::class)
    fun start(firebaseCamera: FirebaseCamera?) {
        if (firebaseCamera == null) {
            this.firebaseCamera?.stop()
            this.firebaseCamera = null
        } else {
            this.firebaseCamera = firebaseCamera
            startRequested = true
            startIfReady()
        }
    }

    @Throws(IOException::class)
    fun start(firebaseCamera: FirebaseCamera, cameraView: CameraView) {
        cameraView.setOnLongClickListener {
            firebaseCamera.processor?.setConst()
            true
        }
        this.cameraView = cameraView
        start(firebaseCamera)
    }

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    private fun startIfReady() {
        if (startRequested && surfaceAvailable) {
            firebaseCamera?.start()
            cameraView?.postInvalidate()
            startRequested = false
        }
    }

    fun stop() {
        this.firebaseCamera?.stop()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var width = 480
        var height = 360

        val size = firebaseCamera?.previewSize
        if (size != null) {
            width = size.width
            height = size.height
        }

        if (isPortraitMode()) {
            val tmp = width
            width = height
            height = tmp
        }

        val layoutWidth = right - left
        val layoutHeight = bottom - top

        var childWidth = layoutWidth
        var childHeight = ((layoutWidth.toFloat() / width.toFloat()) * height).toInt()

        if (childHeight > layoutHeight) {
            childHeight = layoutHeight
            childWidth = ((layoutHeight.toFloat() / height.toFloat()) * width).toInt()
        }

        for (i in 0 until childCount) {
            getChildAt(i).layout(0, 0, childWidth, childHeight)
        }

        try {
            startIfReady()
        } catch (e: IOException) {
            print("Couldn't start camera")
        }

    }

    private fun isPortraitMode(): Boolean {
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true
        }
        return false
    }

    private inner class SurfaceCallback : SurfaceHolder.Callback {
        override fun surfaceCreated(surface: SurfaceHolder) {
            surfaceAvailable = true
            try {
                startIfReady()
            } catch (e: IOException) {
                print("Couldn't start camera")
            }

        }

        override fun surfaceDestroyed(surface: SurfaceHolder) {
            surfaceAvailable = false
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    }
}
