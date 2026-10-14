package com.ziaada.aifriend.byd

import android.content.Context

/** Persisted ADB endpoint settings (host/port) so the user can change them from Settings. */
object AdbConfig {
    private const val PREFS = "adb_config"
    private const val KEY_HOST = "adb_host"
    private const val KEY_PORT = "adb_port"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getHost(context: Context): String =
        prefs(context).getString(KEY_HOST, BydConstants.ADB_HOST) ?: BydConstants.ADB_HOST

    fun getPort(context: Context): Int =
        prefs(context).getInt(KEY_PORT, BydConstants.ADB_PORT)

    fun setHost(context: Context, host: String) {
        prefs(context).edit().putString(KEY_HOST, host.trim()).apply()
    }

    fun setPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PORT, port).apply()
    }

    fun setEndpoint(context: Context, host: String, port: Int) {
        prefs(context).edit().putString(KEY_HOST, host.trim()).putInt(KEY_PORT, port).apply()
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535
}
