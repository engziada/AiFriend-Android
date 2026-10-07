package com.ziaada.aifriend.voice

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads and safely expands the two Sherpa model archives into app-private storage. */
class ModelManager(context: Context) {
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    data class ModelInfo(
        val name: String,
        val url: String,
        val destination: String,
        val requiredFiles: List<String>,
        val requiredDirectories: List<String> = emptyList(),
    )

    companion object {
        val ASR_MODEL = ModelInfo(
            name = "Moonshine Arabic",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-ar-quantized-2026-02-27.tar.bz2",
            destination = "asr/moonshine-ar",
            requiredFiles = listOf("encoder_model.ort", "decoder_model_merged.ort", "tokens.txt"),
        )
        val TTS_MODEL = ModelInfo(
            name = "Piper Arabic voice",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ar_JO-kareem-medium.tar.bz2",
            destination = "tts/piper-ar-kareem-medium",
            requiredFiles = listOf("ar_JO-kareem-medium.onnx", "tokens.txt"),
            requiredDirectories = listOf("espeak-ng-data"),
        )
        private const val TAG = "ModelManager"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MIN_ARCHIVE_BYTES = 1_000_000L
        private const val MIN_MODEL_FILE_BYTES = 1_024L
    }

    suspend fun ensureModelsDownloaded(onProgress: (String, Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        downloadAndExtractIfNeeded(ASR_MODEL, onProgress) && downloadAndExtractIfNeeded(TTS_MODEL, onProgress)
    }

    private fun downloadAndExtractIfNeeded(model: ModelInfo, onProgress: (String, Int, Int) -> Unit): Boolean {
        val targetDir = File(modelsDir, model.destination)
        val marker = File(targetDir, ".completed")
        if (modelFilesReady(targetDir, model)) {
            if (!marker.exists()) marker.writeText("ok")
            onProgress(model.name, 1, 1)
            Log.i(TAG, "Using existing ${model.name} model at $targetDir")
            return true
        }

        val archiveFile = File(modelsDir, "${model.destination.replace('/', '_')}.tar.bz2")
        return try {
            archiveFile.parentFile?.mkdirs()
            archiveFile.delete()
            downloadFile(model, archiveFile, onProgress)
            targetDir.deleteRecursively()
            check(targetDir.mkdirs() || targetDir.isDirectory) { "Cannot create $targetDir" }
            extractTarBz2(archiveFile, targetDir)
            check(modelFilesReady(targetDir, model)) { "Archive does not contain all required model files" }
            marker.writeText("ok")
            Log.i(TAG, "${model.name} model ready at $targetDir")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare ${model.name}", e)
            targetDir.deleteRecursively()
            false
        } finally {
            archiveFile.delete()
        }
    }

    private fun modelFilesReady(root: File, model: ModelInfo): Boolean {
        if (!root.isDirectory) return false
        val filesReady = model.requiredFiles.all { name ->
            root.walkTopDown().any { it.isFile && it.name == name && it.length() > MIN_MODEL_FILE_BYTES }
        }
        val directoriesReady = model.requiredDirectories.all { name ->
            root.walkTopDown().any { it.isDirectory && it.name == name && (it.list()?.isNotEmpty() == true) }
        }
        return filesReady && directoriesReady
    }

    private fun downloadFile(model: ModelInfo, destination: File, onProgress: (String, Int, Int) -> Unit) {
        val temporary = File(destination.parentFile, destination.name + ".part")
        temporary.delete()
        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Download returned HTTP ${connection.responseCode}" }
            val total = connection.contentLength
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(model.name, downloaded, total)
                    }
                    output.fd.sync()
                }
            }
            check(temporary.length() > MIN_ARCHIVE_BYTES) { "Downloaded archive is empty or truncated" }
            check(temporary.renameTo(destination)) { "Could not finalize model download" }
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private fun extractTarBz2(archive: File, destination: File) {
        val canonicalRoot = destination.canonicalFile.toPath()
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)), true),
        ).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val output = File(destination, entry.name).canonicalFile
                require(output.toPath().startsWith(canonicalRoot)) { "Archive entry escapes model directory" }
                require(!entry.isSymbolicLink && !entry.isLink) { "Symbolic links are not allowed in model archives" }
                when {
                    entry.isDirectory -> check(output.mkdirs() || output.isDirectory)
                    entry.isFile -> {
                        output.parentFile?.let { check(it.mkdirs() || it.isDirectory) }
                        FileOutputStream(output).use { tar.copyTo(it) }
                    }
                }
            }
        }
    }

    fun getAsrModelPath(): String = File(modelsDir, ASR_MODEL.destination).absolutePath
    fun getTtsModelPath(): String = File(modelsDir, TTS_MODEL.destination).absolutePath

}
