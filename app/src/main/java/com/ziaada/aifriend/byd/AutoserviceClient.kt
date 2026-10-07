package com.ziaada.aifriend.byd

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AutoserviceClient(
    private val context: Context,
    private val adbTransport: AdbTransport,
) {
    private val TAG = "AutoserviceClient"
    private val fidTable = ConcurrentHashMap<String, FidAddress>()
    private val parcelRegex = """Parcel\(00000000\s+([0-9a-fA-F]{8})""".toRegex()
    private val interfaceToken: String? = null
    private val helperClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HelperDaemonClient(context.applicationContext)
    }

    init {
        FidRegistry.allReadNames().forEach { name ->
            FidRegistry.getRead(name)?.let { fidTable[name] = it }
        }
    }

    suspend fun readInt(deviceId: Int, fid: Int): Int {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result = adbTransport.executeCommand(
                "service call autoservice ${BydConstants.TransactionCode.GET_INT} i32 $deviceId i32 $fid"
            )
            parseParcelResult(result)
        }
    }

    suspend fun readFloat(deviceId: Int, fid: Int): Float {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result = adbTransport.executeCommand(
                "service call autoservice ${BydConstants.TransactionCode.GET_FLOAT} i32 $deviceId i32 $fid"
            )
            val raw = parseParcelResult(result)
            java.lang.Float.intBitsToFloat(raw)
        }
    }

    suspend fun writeInt(deviceId: Int, fid: Int, value: Int): Boolean {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            helperClient.writeInt(deviceId, fid, value)
        }
    }

    suspend fun readBatch(requests: List<Pair<Int, Pair<Int, Int>>>): List<Int> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            helperClient.readBatch(requests.map { (tx, devFid) -> Triple(tx, devFid.first, devFid.second) })
        }
    }

    fun updateFidTable(catalog: Map<String, Int>) {
        catalog.forEach { (symbol, fid) ->
            val parts = symbol.split(".")
            val fieldName = parts.last()
            val prefix = parts.dropLast(1).joinToString(".")

            FidRegistry.getRead(fieldName)?.let { existing ->
                if (existing.fid != fid) {
                    val updated = existing.copy(fid = fid)
                    fidTable[fieldName] = updated
                    FidRegistry.updateRead(fieldName, updated)
                    Log.d(TAG, "Updated FID $fieldName: ${existing.fid} -> $fid (symbol: $symbol)")
                }
            }
        }
    }

    private fun parseParcelResult(output: String): Int {
        val matcher = parcelRegex.find(output)
        return matcher?.groupValues?.get(1)?.let { it.toInt(16) } ?: BydConstants.Sentinel.WRONG_TRANSACT_CODE
    }

    fun decodeValue(name: String, raw: Int): Any {
        val addr = fidTable[name] ?: return raw
        return when (addr.decoder) {
            BydConstants.DecoderType.INT_TEMP_C -> raw.toFloat()
            BydConstants.DecoderType.INT_TEMP_C_OFS40 -> (raw - 40).toFloat()
            BydConstants.DecoderType.INT_PERCENT -> raw.toFloat()
            BydConstants.DecoderType.INT_KPA -> raw.toFloat()
            BydConstants.DecoderType.INT_SCALED -> raw * addr.scale + addr.offset
            "INT_RAW" -> raw.toFloat()
            "INT_ENUM" -> raw
            else -> raw.toFloat()
        }
    }

    fun isSentinel(name: String, raw: Int): Boolean {
        val addr = fidTable[name]
        if (addr == null) return raw == BydConstants.Sentinel.FEATURE_LINK_ERROR ||
                raw == BydConstants.Sentinel.NOT_INITIALIZED_20BIT ||
                raw == BydConstants.Sentinel.WRONG_TRANSACT_CODE ||
                raw == BydConstants.Sentinel.NOT_WRITABLE

        return when (addr.decoder) {
            BydConstants.DecoderType.INT_PERCENT,
            BydConstants.DecoderType.INT_KPA -> raw == BydConstants.Sentinel.FEATURE_LINK_ERROR
            BydConstants.DecoderType.INT_TEMP_C,
            BydConstants.DecoderType.INT_TEMP_C_OFS40 -> raw == BydConstants.Sentinel.FEATURE_LINK_ERROR
            else -> raw == BydConstants.Sentinel.FEATURE_LINK_ERROR ||
                    raw == BydConstants.Sentinel.NOT_INITIALIZED_20BIT ||
                    raw == BydConstants.Sentinel.WRONG_TRANSACT_CODE ||
                    raw == BydConstants.Sentinel.NOT_WRITABLE
        }
    }

    fun isFloatSentinel(raw: Float): Boolean {
        return raw.isNaN() || raw.isInfinite() || raw == BydConstants.Sentinel.FLOAT_NOT_INIT
    }
}

class HelperDaemonClient(private val context: Context) {
    private val TAG = "HelperDaemonClient"
    private var binder: android.os.IBinder? = null

    init {
        connect()
    }

    private fun connect() {
        val intent = android.content.Intent().apply {
            action = "com.ziaada.aifriend.helper.BINDER"
            setPackage("com.ziaada.aifriend")
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                binder = intent.getBundleExtra("helper")?.getBinder("binder")
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter("com.ziaada.aifriend.helper.BINDER"))
        Thread.sleep(2000)
        context.unregisterReceiver(receiver)
    }

    fun writeInt(deviceId: Int, fid: Int, value: Int): Boolean {
        binder?.let { b ->
            val data = android.os.Parcel.obtain()
            val reply = android.os.Parcel.obtain()
            try {
                data.writeInterfaceToken(BydConstants.HELPER_INTERFACE)
                data.writeInt(BydConstants.HelperTransaction.TX_WRITE)
                data.writeInt(deviceId)
                data.writeInt(fid)
                data.writeInt(value)
                b.transact(BydConstants.HelperTransaction.TX_WRITE, data, reply, 0)
                val status = reply.readInt()
                return status == 0
            } catch (e: Exception) {
                Log.e(TAG, "Write failed", e)
                return false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        return false
    }

    fun readBatch(requests: List<Triple<Int, Int, Int>>): List<Int> {
        return binder?.let { b ->
            val data = android.os.Parcel.obtain()
            val reply = android.os.Parcel.obtain()
            try {
                data.writeInterfaceToken(BydConstants.HELPER_INTERFACE)
                data.writeInt(BydConstants.HelperTransaction.TX_READ_BATCH)
                data.writeInt(requests.size)
                requests.forEach { (tx, dev, fid) ->
                    data.writeInt(tx)
                    data.writeInt(dev)
                    data.writeInt(fid)
                }
                b.transact(BydConstants.HelperTransaction.TX_READ_BATCH, data, reply, 0)
                val count = reply.readInt()
                return List(count) { reply.readInt() }
            } catch (e: Exception) {
                Log.e(TAG, "Batch read failed", e)
                emptyList()
            } finally {
                data.recycle()
                reply.recycle()
            }
        } ?: emptyList()
    }

    fun dumpFids(): String? {
        return binder?.let { b ->
            val data = android.os.Parcel.obtain()
            val reply = android.os.Parcel.obtain()
            try {
                data.writeInterfaceToken(BydConstants.HELPER_INTERFACE)
                data.writeInt(BydConstants.HelperTransaction.TX_DUMP_FIDS)
                data.writeInt(0)
                b.transact(BydConstants.HelperTransaction.TX_DUMP_FIDS, data, reply, 0)
                val status = reply.readInt()
                if (status != 0) return null
                val totalLength = reply.readInt()
                val chunk = reply.createByteArray() ?: ByteArray(0)
                return String(chunk, kotlin.text.Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "FID dump failed", e)
                null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
