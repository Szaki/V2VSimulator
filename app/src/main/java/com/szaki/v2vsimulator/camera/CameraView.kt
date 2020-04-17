package com.szaki.v2vsimulator.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.View
import com.szaki.v2vsimulator.R
import com.szaki.v2vsimulator.misc.Logger
import kotlin.concurrent.thread

class CameraView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var camera: Bitmap? = null
    var addresscode: Bitmap? = null
    var stopcode: Bitmap? = null
    var direction: Int = 0
    private var stop: Bitmap? = null
    private val left = BitmapFactory.decodeResource(resources, R.drawable.left)
    private val right = BitmapFactory.decodeResource(resources, R.drawable.right)
    private val leftsound = MediaPlayer.create(context, R.raw.left)
    private val rightsound = MediaPlayer.create(context, R.raw.right)
    private val logger = Logger()
    private var stopped = false

    fun stop() {
        thread {
            stop = stopcode
            Thread.sleep(1000)
            stop = null
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        when {
            stop != null -> {
                if (!stopped)
                    logger.log("QR_STOP,${System.currentTimeMillis()}\n")
                stopped = true
                scaleX = 1.25f
                scaleY = 1.25f
                canvas?.drawBitmap(stop!!, null, Rect(0, 60, width, width), null)
            }
            addresscode != null -> {
                stopped = false
                scaleX = 1.25f
                scaleY = 1.25f
                canvas?.drawBitmap(addresscode!!, null, Rect(0, 60, width, width), null)
            }
            camera != null -> canvas?.drawBitmap(camera!!, null, Rect(0, 0, width, height), null)
        }

        when (direction) {
            -2 -> {
                canvas?.drawBitmap(left, null, Rect(0, 800, 300, 1100), null)
                canvas?.drawBitmap(left, null, Rect(300, 875, 450, 1025), null)
            }
            -1 -> canvas?.drawBitmap(left, null, Rect(300, 875, 450, 1025), null)
            1 -> canvas?.drawBitmap(right, null, Rect(630, 875, 780, 1025), null)
            2 -> {
                canvas?.drawBitmap(right, null, Rect(630, 875, 780, 1025), null)
                canvas?.drawBitmap(right, null, Rect(780, 800, 1080, 1100), null)
            }
        }

        when (direction) {
            -2, -1 -> if (!leftsound.isPlaying)
                leftsound.start()
            2, 1 -> if (!rightsound.isPlaying)
                rightsound.start()
        }
    }
}