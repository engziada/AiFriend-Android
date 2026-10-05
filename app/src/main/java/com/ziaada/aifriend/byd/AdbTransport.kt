package com.ziaada.aifriend.byd

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class AdbTransport(
    private val context: Context,
    private val host: String = BydConstants.ADB_HOST,
    private val port: Int = BydConstants.ADB_PORT,
) {
    private var socket: Socket? = null
    private var dataOut: DataOutputStream? = null
    private var dataIn: DataInputStream? = null
    private var keyPair: KeyPair? = null
    private var localId = 1

    companion object {
        private const val TAG = "AdbTransport"
        const val CMD_CNXN = 0x4E584E43.toInt()
        const val CMD_AUTH = 0x48545541.toInt()
        const val CMD_OPEN = 0x4E45504F.toInt()
        const val CMD_OKAY = 0x59414B4F.toInt()
        const val CMD_CLSE = 0x45534C43.toInt()
        const val CMD_WRTE = 0x45545257.toInt()
        const val CMD_STLS = 0x534C5453.toInt()

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        const val MAX_PAYLOAD = 256 * 1024
        const val HEADER_SIZE = 24

        val ASN1_PREFIX: ByteArray = "3021300906052b0e03021a05000414".hexToByteArray()
    }

    fun connect(): Boolean {
        try {
            loadOrGenerateKeys()
            socket = Socket()
            socket?.soTimeout = BydConstants.ADB_SOCKET_TIMEOUT_MS
            socket?.connect(InetSocketAddress(host, port), BydConstants.ADB_SOCKET_TIMEOUT_MS)
            dataOut = DataOutputStream(socket?.getOutputStream()!!)
            dataIn = DataInputStream(socket?.getInputStream()!!)

            if (!handshake()) return false
            if (!authenticate()) return false
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            disconnect()
            return false
        }
    }

    private fun loadOrGenerateKeys() {
        val keyDir = context.getDir("adb_keys", Context.MODE_PRIVATE)
        val privFile = java.io.File(keyDir, "adb_private")
        val pubFile = java.io.File(keyDir, "adb_public")

        if (privFile.exists() && pubFile.exists()) {
            try {
                val privBytes = java.nio.file.Files.readAllBytes(privFile.toPath())
                val pubBytes = java.nio.file.Files.readAllBytes(pubFile.toPath())
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                keyPair = KeyPair(publicKey, privateKey)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load keys, regenerating", e)
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        keyPair = kpg.generateKeyPair()

        try {
            java.nio.file.Files.write(privFile.toPath(), keyPair!!.private.encoded)
            java.nio.file.Files.write(pubFile.toPath(), keyPair!!.public.encoded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save keys", e)
        }
    }

    private fun handshake(): Boolean {
        val banner = "host::\u0000".toByteArray()
        sendPacket(CMD_CNXN, 0x01000001, 0, banner)

        val result = readPacket()
        return when (result.cmd) {
            CMD_CNXN -> true
            CMD_STLS -> {
                sendPacket(CMD_STLS, 0x01000000, 0, ByteArray(0))
                val result2 = readPacket()
                result2.cmd == CMD_CNXN
            }
            else -> false
        }
    }

    private fun authenticate(): Boolean {
        var attempts = 0
        while (attempts < 3) {
            val result = readPacket()
            when (result.cmd) {
                CMD_AUTH -> {
                    when (result.arg0) {
                        AUTH_TOKEN -> {
                            val token = result.payload.copyOf()
                            val signature = signToken(token)
                            sendPacket(CMD_AUTH, AUTH_SIGNATURE, 0, signature)
                        }
                        AUTH_SIGNATURE -> return true
                        AUTH_TOKEN -> {
                            val pubKeyBlob = buildPublicKeyBlob()
                            sendPacket(CMD_AUTH, AUTH_RSAPUBLICKEY, 0, pubKeyBlob)
                            Thread.sleep(BydConstants.ADB_AUTH_TIMEOUT_MS.toLong())
                        }
                    }
                }
                CMD_CNXN -> return true
                else -> return false
            }
            attempts++
        }
        return false
    }

    private fun signToken(token: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1").digest(token)
        val signedData = ByteArray(ASN1_PREFIX.size + digest.size)
        System.arraycopy(ASN1_PREFIX, 0, signedData, 0, ASN1_PREFIX.size)
        System.arraycopy(digest, 0, signedData, ASN1_PREFIX.size, digest.size)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keyPair!!.private)
        return cipher.doFinal(signedData)
    }

    private fun buildPublicKeyBlob(): ByteArray {
        val pubKey = keyPair!!.public as java.security.interfaces.RSAPublicKey
        val modulus = pubKey.modulus
        val exponent = pubKey.publicExponent

        val n = modulus.toByteArray()
        val nLen = if (n[0].toInt() == 0) n.size - 1 else n.size

        val buffer = ByteBuffer.allocate(4 + 4 + nLen + nLen + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(nLen / 4)
        buffer.putInt(0) // n0inv placeholder

        if (n[0].toInt() == 0) {
            buffer.put(n, 1, nLen)
        } else {
            buffer.put(n, 0, nLen)
        }

        val rr = computeRR(modulus)
        buffer.put(rr)
        buffer.putInt(exponent.toInt())
        return buffer.array()
    }

    private fun computeRR(modulus: java.math.BigInteger): ByteArray {
        val R = java.math.BigInteger.ONE.shiftLeft(2048)
        val RR = R.modPow(java.math.BigInteger.valueOf(2), modulus)
        val bytes = RR.toByteArray()
        val padded = ByteArray(256)
        val offset = 256 - bytes.size
        if (offset > 0) System.arraycopy(bytes, 0, padded, offset, bytes.size)
        return padded
    }

    fun executeCommand(command: String): String {
        val localId = nextLocalId()
        openShell(localId)

        val okay = waitForOkay(localId)
        if (!okay) return ""

        writeShellCommand(localId, command)

        val output = StringBuilder()
        while (true) {
            val result = readPacket()
            when (result.cmd) {
                CMD_WRTE -> {
                    if (result.arg0 == localId) {
                        output.append(String(result.payload, kotlin.text.Charsets.UTF_8))
                        sendPacket(CMD_OKAY, localId, 0, ByteArray(0))
                    }
                }
                CMD_CLSE -> {
                    if (result.arg0 == localId) {
                        sendPacket(CMD_CLSE, localId, 0, ByteArray(0))
                        break
                    }
                }
                CMD_OKAY -> { /* ack for our WRTE */ }
            }
        }
        return output.toString()
    }

    private fun openShell(localId: Int) {
        val cmd = "shell:exec sh\u0000".toByteArray()
        sendPacket(CMD_OPEN, localId, 0, cmd)
    }

    private fun waitForOkay(localId: Int): Boolean {
        val result = readPacket()
        return result.cmd == CMD_OKAY && result.arg0 == localId
    }

    private fun writeShellCommand(localId: Int, command: String) {
        val payload = "$command\n".toByteArray(kotlin.text.Charsets.UTF_8)
        sendPacket(CMD_WRTE, localId, 0, payload)
    }

    private fun sendPacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val checksum = payload.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
        val magic = command.inv()

        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(command)
            putInt(arg0)
            putInt(arg1)
            putInt(payload.size)
            putInt(checksum)
            putInt(magic)
        }.array()

        synchronized(this) {
            dataOut!!.write(header)
            if (payload.isNotEmpty()) dataOut!!.write(payload)
            dataOut!!.flush()
        }
    }

    private fun readPacket(): AdbPacket {
        val header = ByteArray(HEADER_SIZE)
        dataIn!!.readFully(header)

        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = bb.getInt()
        val arg0 = bb.getInt()
        val arg1 = bb.getInt()
        val length = bb.getInt()
        val checksum = bb.getInt()
        val magic = bb.getInt()

        val payload = if (length > 0) {
            val buf = ByteArray(length)
            dataIn!!.readFully(buf)
            val calcChecksum = buf.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
            if (calcChecksum != checksum) {
                Log.w(TAG, "Checksum mismatch: expected $checksum, got $calcChecksum")
            }
            buf
        } else ByteArray(0)

        return AdbPacket(command, arg0, arg1, payload)
    }

    private fun nextLocalId(): Int = localId++

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Disconnect error", e)
        }
        socket = null
        dataOut = null
        dataIn = null
    }

    fun isConnected(): Boolean = socket?.isConnected == true && socket?.isClosed == false

    data class AdbPacket(
        val cmd: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray,
    )
}

private fun String.hexToByteArray(): ByteArray {
    val len = length / 2
    val result = ByteArray(len)
    for (i in 0 until len) {
        result[i] = substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return result
}