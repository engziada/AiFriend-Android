package com.ziaada.aifriend.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import kotlin.io.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelManager(private val context: Context) {
    private val TAG = "ModelManager"
    private val modelsDir: File

    init {
        modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
    }

    data class ModelInfo(
        val name: String,
        val url: String,
        val sha256: String,
        val destination: String,
    )

    companion object {
        val ASR_MODEL = ModelInfo(
            name = "Moonshine Arabic ASR",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-ar-quantized-2026-02-27.tar.bz2",
            sha256 = "CHECKSUM_TO_BE_ADDED",
            destination = "asr/moonshine-ar",
        )

        val TTS_MODEL = ModelInfo(
            name = "Piper Arabic TTS (kareem-medium)",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ar_JO-kareem-medium.tar.bz2",
            sha256 = "CHECKSUM_TO_BE_ADDED",
            destination = "tts/piper-ar-kareem-medium",
        )
    }

    suspend fun ensureModelsDownloaded(onProgress: (String, Int, Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val asrOk = downloadAndExtractIfNeeded(ASR_MODEL, onProgress)
            val ttsOk = downloadAndExtractIfNeeded(TTS_MODEL, onProgress)
            asrOk && ttsOk
        }
    }

    private fun downloadAndExtractIfNeeded(model: ModelInfo, onProgress: (String, Int, Int) -> Unit): Boolean {
        val targetDir = File(modelsDir, model.destination)
        val markerFile = File(targetDir, ".completed")

        if (markerFile.exists()) {
            Log.d(TAG, "Model ${model.name} already downloaded")
            return true
        }

        if (targetDir.exists()) {
            deleteRecursively(targetDir)
        }
        targetDir.mkdirs()

        try {
            Log.i(TAG, "Downloading ${model.name} from ${model.url}")
            val archiveFile = File(modelsDir, "${model.name.replace(" ", "_")}.tar.bz2")
            downloadFile(model.url, archiveFile, onProgress)

            Log.i(TAG, "Verifying checksum...")
            if (!verifyChecksum(archiveFile, model.sha256)) {
                Log.e(TAG, "Checksum mismatch for ${model.name}")
                return false
            }

            Log.i(TAG, "Extracting ${model.name}...")
            extractTarBz2(archiveFile, targetDir)

            markerFile.writeText("ok")
            archiveFile.delete()
            Log.i(TAG, "Model ${model.name} ready at $targetDir")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download/extract ${model.name}", e)
            return false
        }
    }

    private fun downloadFile(urlString: String, destination: File, onProgress: (String, Int, Int) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection()
        connection.connect()
        val fileLength = connection.contentLength
        val input = connection.getInputStream()
        val output = FileOutputStream(destination)

        val buffer = ByteArray(8192)
        var downloaded = 0
        var len: Int
        while (input.read(buffer).also { len = it } != -1) {
            output.write(buffer, 0, len)
            downloaded += len
            onProgress(destination.name, downloaded, fileLength)
        }
        output.flush()
        output.close()
        input.close()
    }

    private fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (expectedSha256 == "CHECKSUM_TO_BE_ADDED") return true // Skip for now
        val digest = MessageDigest.getInstance("SHA-256")
        val fis = java.io.FileInputStream(file)
        val buffer = ByteArray(8192)
        var len: Int
        while (fis.read(buffer).also { len = it } != -1) {
            digest.update(buffer, 0, len)
        }
        fis.close()
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    private fun extractTarBz2(archive: File, dest: File) {
        // Use Apache Commons Compress or similar in production
        // For now, we'll use a simple approach assuming tar.bz2
        val process = Runtime.getRuntime().exec(arrayOf("tar", "-xjf", archive.absolutePath, "-C", dest.absolutePath))
        process.waitFor()
        if (process.exitValue() != 0) {
            val error = process.errorStream.bufferedReader().readText()
            throw RuntimeException("Extraction failed: $error")
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    fun getAsrModelPath(): String = File(modelsDir, ASR_MODEL.destination).absolutePath
    fun getTtsModelPath(): String = File(modelsDir, TTS_MODEL.destination).absolutePath
}