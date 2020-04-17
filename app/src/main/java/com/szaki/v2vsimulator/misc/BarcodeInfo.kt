package com.szaki.v2vsimulator.misc

import android.graphics.Point
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode

class BarcodeInfo() {
    var content: String? = ""
    var size: Int = -1
    var center = Point(-1, -1)
    var distance: Float = 0f
    var lastSeen = System.currentTimeMillis()

    constructor(f: FirebaseVisionBarcode, disconst: Float) : this() {
        content = f.rawValue
        f.cornerPoints?.let {
            size = Math.max(
                Math.max(it[1].x - it[0].x, it[2].x - it[3].x),
                Math.max(it[3].y - it[0].y, it[2].y - it[1].y)
            )
            center.x = it[0].x + size / 2
            center.y = it[0].y + size / 2
            if (disconst > 0f)
                distance = 2 * disconst / size
        }
    }

    override fun toString(): String {
        if (distance < 0f)
            return "Content: $content\nSize: $size\nCenter: ${center.x}, ${center.y}\n"
        return "Content: $content\nSize: $size\nCenter: ${center.x}, ${center.y}\nDistance: $distance m\n"
    }
}