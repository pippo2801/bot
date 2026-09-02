package com.amazego.autosolver.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Logger thread-safe in-memory con timestamp per telemetria, debug e visualizzazione UI.
 */
object DebugLogger {

    private val logBuffer = CopyOnWriteArrayList<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private const val MAX_LOGS = 300

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val formatted = "[$timestamp] $message"
        
        logBuffer.add(formatted)
        if (logBuffer.size > MAX_LOGS) {
            logBuffer.removeAt(0)
        }

        // Notifica ascoltatori (es. MainActivity o Floating Overlay)
        for (listener in listeners) {
            listener.invoke(formatted)
        }
    }

    fun getAllLogs(): List<String> = logBuffer.toList()

    fun clear() {
        logBuffer.clear()
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}
