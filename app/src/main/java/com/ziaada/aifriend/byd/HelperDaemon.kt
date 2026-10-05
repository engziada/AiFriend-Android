package com.ziaada.aifriend.byd

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.util.Log

class HelperDaemon {
    companion object {
        private const val TAG = "HelperDaemon"
        private const val LOCK_FILE = "/data/local/tmp/aifriend_helper.lock"

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 2) {
                println("ERR: usage <expectedUid> <spawnToken>")
                System.exit(2)
            }

            val expectedUid = args[0].toIntOrNull() ?: return
            val spawnToken = args[1]

            if (Process.myUid() != expectedUid) {
                println("ERR: uid mismatch")
                System.exit(2)
            }

            val lockFile = java.io.File(LOCK_FILE)
            val lockChannel = java.nio.channels.FileChannel.open(
                lockFile.toPath(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE
            )
            val lock = lockChannel.tryLock()
            if (lock == null) {
                println("ALREADY_RUNNING")
                System.exit(0)
            }

            println("BOOT uid=$expectedUid pid=${Process.myPid()} selinux=${getSelinuxContext()} token=set")

            Looper.prepareMainLooper()

            val autoservice = getAutoservice()
            if (autoservice == null) {
                println("ERR: autoservice not found")
                System.exit(3)
            }

            val helperBinder = HelperBinder(autoservice!!, expectedUid, spawnToken)
            val success = try {
                val sm = Class.forName("android.os.ServiceManager")
                val addService = sm.getDeclaredMethod("addService", String::class.java, IBinder::class.java)
                addService.isAccessible = true
                addService.invoke(null, BydConstants.HELPER_SERVICE_NAME, helperBinder)
                true
            } catch (e: Exception) {
                Log.w(TAG, "ServiceManager.addService failed, using broadcast fallback", e)
                broadcastFallback(helperBinder, spawnToken)
                false
            }

            if (success) {
                println("READY via=servicemanager pid=${Process.myPid()}")
            } else {
                println("READY via=broadcast pid=${Process.myPid()}")
            }

            Looper.loop()
        }

        private fun getAutoservice(): IBinder? {
            try {
                val sm = Class.forName("android.os.ServiceManager")
                val getService = sm.getDeclaredMethod("getService", String::class.java)
                getService.isAccessible = true
                return getService.invoke(null, BydConstants.AUTOSERVICE_NAME) as IBinder?
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get autoservice", e)
                return null
            }
        }

        private fun getSelinuxContext(): String {
            return try {
                java.io.File("/proc/self/attr/current").readText().trim()
            } catch (e: Exception) {
                "unknown"
            }
        }

        private fun broadcastFallback(binder: IBinder, token: String) {
            val intent = Intent("com.ziaada.aifriend.helper.BINDER")
            intent.setPackage("com.ziaada.aifriend")
            val bundle = Bundle()
            bundle.putBinder("binder", binder)
            bundle.putString("token", token)
            bundle.putLong("version", 1L)
            bundle.putInt("pid", Process.myPid())
            intent.putExtra("helper", bundle)
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

            // Note: In app_process context, we can't easily get Application context
            // The app will receive this broadcast when it registers a receiver
        }
    }
}

class HelperBinder(
    private val autoservice: IBinder,
    private val expectedUid: Int,
    private val spawnToken: String,
) : Binder() {
    private val TAG = "HelperBinder"
    private val interfaceDescriptor: String = autoservice.interfaceDescriptor!!

    init {
        attachInterface(null, BydConstants.HELPER_INTERFACE)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val r = reply!!
        if (code == IBinder.INTERFACE_TRANSACTION) {
            r.writeString(BydConstants.HELPER_INTERFACE)
            return true
        }

        if (Binder.getCallingUid() != expectedUid) {
            Log.w(TAG, "UID mismatch: expected $expectedUid, got ${Binder.getCallingUid()}")
            return false
        }

        data.enforceInterface(BydConstants.HELPER_INTERFACE)

        return when (code) {
            BydConstants.HelperTransaction.TX_PING -> {
                r.writeInt(0)
                true
            }
            BydConstants.HelperTransaction.TX_READ -> {
                val tx = data.readInt()
                val dev = data.readInt()
                val fid = data.readInt()
                val result = transactAutoservice(tx, dev, fid, 0, false)
                r.writeInt(result.first)
                r.writeInt(result.second)
                true
            }
            BydConstants.HelperTransaction.TX_WRITE -> {
                val dev = data.readInt()
                val fid = data.readInt()
                val value = data.readInt()
                val result = transactAutoservice(BydConstants.TransactionCode.SET_INT, dev, fid, value, true)
                r.writeInt(result.first)
                r.writeInt(result.second)
                true
            }
            BydConstants.HelperTransaction.TX_READ_BATCH -> {
                val count = data.readInt()
                if (count < 0 || count > BydConstants.MAX_BATCH_ITEMS) {
                    r.writeInt(0)
                    return true
                }
                val results = mutableListOf<Pair<Int, Int>>()
                repeat(count) {
                    val tx = data.readInt()
                    val dev = data.readInt()
                    val fid = data.readInt()
                    results.add(transactAutoservice(tx, dev, fid, 0, false))
                }
                r.writeInt(results.size)
                results.forEach { (status, value) ->
                    r.writeInt(status)
                    r.writeInt(value)
                }
                true
            }
            BydConstants.HelperTransaction.TX_DUMP_FIDS -> {
                val offset = data.readInt()
                val dump = dumpFids()
                val bytes = dump.toByteArray(kotlin.text.Charsets.UTF_8)
                if (offset == 0) {
                    r.writeInt(0)
                    r.writeInt(bytes.size)
                    r.writeByteArray(bytes.copyOfRange(0, minOf(bytes.size, BydConstants.FID_DUMP_CHUNK_MAX)))
                } else {
                    val start = offset
                    val end = minOf(offset + BydConstants.FID_DUMP_CHUNK_MAX, bytes.size)
                    r.writeInt(0)
                    r.writeInt(bytes.size)
                    r.writeByteArray(bytes.copyOfRange(start, end))
                }
                true
            }
            BydConstants.HelperTransaction.TX_GET_VERSION -> {
                r.writeInt(0)
                r.writeInt(1)
                true
            }
            BydConstants.HelperTransaction.TX_REGISTER_CLIENT -> {
                r.writeInt(0)
                true
            }
            BydConstants.HelperTransaction.TX_WRITE_BUFFER -> {
                val dev = data.readInt()
                val fid = data.readInt()
                val buffer = data.createByteArray()
                val result = transactAutoservice(BydConstants.TransactionCode.SET_BUFFER, dev, fid, 0, true, buffer)
                r.writeInt(result.first)
                r.writeInt(0)
                true
            }
            else -> false
        }
    }

    private fun transactAutoservice(
        tx: Int,
        dev: Int,
        fid: Int,
        value: Int,
        writeValue: Boolean,
        buffer: ByteArray? = null,
    ): Pair<Int, Int> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(interfaceDescriptor)
            data.writeInt(dev)
            data.writeInt(fid)
            if (writeValue) {
                when (tx) {
                    BydConstants.TransactionCode.SET_INT -> data.writeInt(value)
                    BydConstants.TransactionCode.SET_BUFFER -> data.writeByteArray(buffer!!)
                }
            }
            val success = autoservice.transact(tx, data, reply, 0)
            if (!success) return Pair(-999, 0)
            val status = if (reply.dataAvail() >= 4) reply.readInt() else -999
            val ret = if (reply.dataAvail() >= 8) reply.readInt() else 0
            return Pair(status, ret)
        } catch (e: Exception) {
            Log.e(TAG, "Autoservice transact failed", e)
            return Pair(-999, 0)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun dumpFids(): String {
        try {
            val classLoader = this.javaClass.classLoader
            val featureIdsClass = classLoader.loadClass("android.hardware.bydauto.BYDAutoFeatureIds")
            val constantsClass = classLoader.loadClass("android.hardware.bydauto.BYDAutoConstants")

            val sb = StringBuilder()
            listOf(featureIdsClass, constantsClass).forEach { clazz ->
                clazz.declaredFields.forEach { field ->
                    if (!field.name.startsWith("$") && (field.type == Int::class.javaPrimitiveType || field.type == Long::class.javaPrimitiveType)) {
                        field.isAccessible = true
                        val value = field.get(null)
                        sb.append("${clazz.simpleName}.${field.name}=$value\n")
                    }
                }
                clazz.declaredClasses.forEach { nested ->
                    nested.declaredFields.forEach { field ->
                        if (!field.name.startsWith("$") && (field.type == Int::class.javaPrimitiveType || field.type == Long::class.javaPrimitiveType)) {
                            field.isAccessible = true
                            val value = field.get(null)
                            sb.append("${nested.simpleName}.${field.name}=$value\n")
                        }
                    }
                }
            }
            return sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "FID dump failed", e)
            return ""
        }
    }
}