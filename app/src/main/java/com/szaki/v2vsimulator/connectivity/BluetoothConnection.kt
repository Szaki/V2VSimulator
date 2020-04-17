package com.szaki.v2vsimulator.connectivity

import android.bluetooth.BluetoothSocket
import com.szaki.v2vsimulator.misc.Logger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BluetoothConnection(private val socket: BluetoothSocket) : Thread() {

    private val inputStream: InputStream = socket.inputStream
    private val outputStream: OutputStream = socket.outputStream
    private val logger = Logger()

    override fun run() {
        while (true) {
            val buffer = ByteArray(128)
            try {
                inputStream.read(buffer)
            } catch (e: IOException) {
                print(e.localizedMessage)
                break
            }
            val time = System.currentTimeMillis()
            val data = buffer.toString(Charsets.UTF_8).trim(0.toChar())
            logger.received("$data,$time\n")
        }
    }

    fun send(data: String) {
        logger.sent("$data,${System.currentTimeMillis()}\n")
        try {
            outputStream.write(data.toByteArray())
        } catch (e: IOException) {
            println(e.localizedMessage)
            return
        }
    }

    fun cancel() {
        try {
            socket.close()
        } catch (e: IOException) {
            print(e.localizedMessage)
        }
    }
}