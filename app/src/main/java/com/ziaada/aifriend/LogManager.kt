package com.ziaada.aifriend

import android.util.Log

object LogManager {
    private const val MAX_LOGS = 500
    private val logs = mutableListOf<String>()
    private val lock = Any()

    fun log(tag: String, message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "[$timestamp] [$tag] $message"
        Log.d("AiFriend", logEntry)
        synchronized(lock) {
            logs.add(logEntry)
            if (logs.size > MAX_LOGS) {
                logs.removeAt(0)
            }
        }
    }

    fun getLogs(): List<String> {
        synchronized(lock) {
            return logs.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            logs.clear()
        }
    }
}