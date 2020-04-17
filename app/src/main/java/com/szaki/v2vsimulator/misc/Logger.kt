package com.szaki.v2vsimulator.misc

import android.os.Environment
import java.io.File

class Logger {
    private val dir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOCUMENTS
    )
    private val receivedlog = File(
        dir, "received_data.txt"
    )
    private val sentlog = File(
        dir, "sent_data.txt"
    )
    private val log = File(
        dir, "log.txt"
    )

    init {
        dir.mkdir()
        if (!receivedlog.exists())
            receivedlog.createNewFile()
        if (!sentlog.exists())
            sentlog.createNewFile()
        if (!log.exists())
            log.createNewFile()
    }

    fun log(s: String) {
        log.appendText(s)
    }

    fun sent(s: String) {
        sentlog.appendText(s)
    }

    fun received(s: String) {
        receivedlog.appendText(s)
    }

    fun startSession() {
        log.appendText("---NEW SESSION---\n")
        sentlog.appendText("---NEW SESSION---\n")
        receivedlog.appendText("---NEW SESSION---\n")
    }
}