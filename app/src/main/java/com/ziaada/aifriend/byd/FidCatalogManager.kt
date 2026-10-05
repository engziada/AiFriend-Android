package com.ziaada.aifriend.byd

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

class FidCatalogManager(
    private val context: Context,
    private val autoserviceClient: AutoserviceClient,
) {
    private val TAG = "FidCatalogManager"
    private val catalogFile: File
    private val gson = Gson()

    init {
        catalogFile = File(context.filesDir, "fid-catalog.json")
    }

    data class CatalogEntry(
        val fingerprint: String,
        val appVersion: Int,
        val symbolsTotal: Int,
        val symbols: Map<String, Int>,
        val devices: Map<String, Int>,
    )

    suspend fun syncCatalog(): Boolean {
        return withContext(Dispatchers.IO) {
            val currentFingerprint = android.os.Build.FINGERPRINT
            val cached = loadCachedCatalog()

            if (cached != null && cached.fingerprint == currentFingerprint) {
                applyCatalog(cached)
                Log.d(TAG, "Using cached FID catalog (${cached.symbolsTotal} symbols)")
                return@withContext true
            }

            Log.i(TAG, "Dumping FID catalog from firmware...")
            val dumpResult = dumpFidsFromHelper()
            if (dumpResult.isEmpty()) {
                Log.e(TAG, "FID dump failed")
                return@withContext false
            }

            val (symbols, devices) = parseDump(dumpResult)
            val entry = CatalogEntry(
                fingerprint = currentFingerprint,
                appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionCode,
                symbolsTotal = symbols.size,
                symbols = symbols,
                devices = devices,
            )

            saveCatalog(entry)
            applyCatalog(entry)
            Log.i(TAG, "FID catalog synced: ${symbols.size} symbols, ${devices.size} devices")
            true
        }
    }

    private fun dumpFidsFromHelper(): String {
        val helper = HelperDaemonClient(context)
        return helper.dumpFids() ?: ""
    }

    private fun parseDump(dump: String): Pair<Map<String, Int>, Map<String, Int>> {
        val symbols = mutableMapOf<String, Int>()
        val devices = mutableMapOf<String, Int>()

        dump.lines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().toIntOrNull() ?: return@forEach
                symbols[key] = value

                if (key.startsWith("BYDAutoConstants.BYDAUTO_DEVICE_")) {
                    val deviceName = key.substring("BYDAutoConstants.BYDAUTO_DEVICE_".length)
                    devices[deviceName] = value
                }
            }
        }

        return symbols to devices
    }

    private fun applyCatalog(entry: CatalogEntry) {
        autoserviceClient.updateFidTable(entry.symbols)
    }

    private fun loadCachedCatalog(): CatalogEntry? {
        if (!catalogFile.exists()) return null
        try {
            val json = catalogFile.readText()
            return gson.fromJson(json, CatalogEntry::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached catalog", e)
            return null
        }
    }

    private fun saveCatalog(entry: CatalogEntry) {
        try {
            val json = gson.toJson(entry)
            FileWriter(catalogFile).use { it.write(json) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save catalog", e)
        }
    }
}