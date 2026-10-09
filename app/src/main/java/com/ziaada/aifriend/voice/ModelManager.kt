package com.ziaada.aifriend.voice

import android.content.Context
import com.ziaada.aifriend.pilot.PilotLogger
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    data class ModelStatus(
        val asrReady: Boolean,
        val ttsReady: Boolean,
        val asrPath: String,
        val ttsPath: String,
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
        private const val READ_TIMEOUT_MS = 300_000
        private const val MIN_ARCHIVE_BYTES = 1_000_000L
        private const val MIN_MODEL_FILE_BYTES = 1_024L
        private const val DOWNLOAD_RETRIES = 3
        private const val RETRY_DELAY_MS = 1_500L
    }

    private var discoveredAsrDir: File? = null
    private var discoveredTtsDir: File? = null

    init {
        scanExistingModels()
    }

    /** Scans the models directory (and subdirectories) for already-present model files. */
    fun scanExistingModels() {
        discoveredAsrDir = findExistingModelDir(ASR_MODEL)
        discoveredTtsDir = findExistingModelDir(TTS_MODEL)
        PilotLogger.i(TAG, "Model scan: ASR=${discoveredAsrDir?.absolutePath ?: "not found"}, TTS=${discoveredTtsDir?.absolutePath ?: "not found"}")
    }

    /** Current model readiness, considering both canonical and discovered paths. */
    fun status(): ModelStatus = ModelStatus(
        asrReady = modelReady(ASR_MODEL),
        ttsReady = modelReady(TTS_MODEL),
        asrPath = getAsrModelPath(),
        ttsPath = getTtsModelPath(),
    )

    suspend fun ensureModelsDownloaded(onProgress: (String, Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        scanExistingModels()
        val asrOk = prepareModel(ASR_MODEL, onProgress)
        val ttsOk = prepareModel(TTS_MODEL, onProgress)
        PilotLogger.i(TAG, "ensureModelsDownloaded: asrOk=$asrOk ttsOk=$ttsOk")
        asrOk && ttsOk
    }

    /** Explicitly download/extract a single model; useful for the pilot download buttons. */
    suspend fun downloadModel(model: ModelInfo, onProgress: (String, Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val ok = downloadAndExtractWithRetry(model, onProgress)
        if (ok) scanExistingModels()
        ok
    }

    private suspend fun prepareModel(model: ModelInfo, onProgress: (String, Int, Int) -> Unit): Boolean {
        val targetDir = File(modelsDir, model.destination)
        val marker = File(targetDir, ".completed")
        if (modelFilesReady(targetDir, model)) {
            if (!marker.exists()) marker.writeText("ok")
            onProgress(model.name, 1, 1)
            return true
        }
        // If the model lives somewhere else (e.g. older version path), keep it and create a marker.
        val existingDir = findExistingModelDir(model)
        if (existingDir != null) {
            PilotLogger.i(TAG, "Reusing existing ${model.name} at $existingDir")
            onProgress(model.name, 1, 1)
            return true
        }
        return downloadAndExtractWithRetry(model, onProgress)
    }

    private fun findExistingModelDir(model: ModelInfo): File? {
        if (!modelsDir.isDirectory) return null
        return modelsDir.walkTopDown()
            .filter { it.isFile && it.name in model.requiredFiles }
            .mapNotNull { it.parentFile }
            .firstOrNull { parent -> modelFilesReady(parent, model) }
    }

    private suspend fun downloadAndExtractWithRetry(model: ModelInfo, onProgress: (String, Int, Int) -> Unit): Boolean {
        repeat(DOWNLOAD_RETRIES) { attempt ->
            val ok = downloadAndExtractIfNeeded(model, onProgress, attempt + 1)
            if (ok) return true
            if (attempt < DOWNLOAD_RETRIES - 1) {
                PilotLogger.w(TAG, "${model.name} download attempt ${attempt + 1} failed, retrying in ${RETRY_DELAY_MS}ms")
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return false
    }

    private fun downloadAndExtractIfNeeded(model: ModelInfo, onProgress: (String, Int, Int) -> Unit, attempt: Int): Boolean {
        val targetDir = File(modelsDir, model.destination)
        val marker = File(targetDir, ".completed")
        if (modelFilesReady(targetDir, model)) {
            if (!marker.exists()) marker.writeText("ok")
            onProgress(model.name, 1, 1)
            PilotLogger.i(TAG, "Using existing ${model.name} model at $targetDir")
            return true
        }

        val archiveFile = File(modelsDir, "${model.destination.replace('/', '_')}.tar.bz2")
        val partFile = File(archiveFile.parentFile, archiveFile.name + ".part")
        return try {
            archiveFile.parentFile?.mkdirs()
            if (!downloadFileWithResume(model, archiveFile, partFile, onProgress, attempt)) {
                return false
            }
            targetDir.deleteRecursively()
            check(targetDir.mkdirs() || targetDir.isDirectory) { "Cannot create $targetDir" }
            extractTarBz2(archiveFile, targetDir)
            val filesAfterExtract = targetDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(targetDir).path }.toList()
            PilotLogger.d(TAG, "${model.name} extracted files: $filesAfterExtract")
            check(modelFilesReady(targetDir, model)) {
                "Archive does not contain all required model files. Found: $filesAfterExtract"
            }
            marker.writeText("ok")
            PilotLogger.i(TAG, "${model.name} model ready at $targetDir")
            true
        } catch (e: Exception) {
            PilotLogger.e(TAG, "Failed to prepare ${model.name} (attempt $attempt)", e)
            targetDir.deleteRecursively()
            false
        } finally {
            archiveFile.delete()
            // Keep the .part file only if we succeeded; otherwise clean it up to force a fresh start.
            if (!modelFilesReady(targetDir, model)) {
                partFile.delete()
            }
        }
    }

    private fun modelReady(model: ModelInfo): Boolean {
        val targetDir = File(modelsDir, model.destination)
        return modelFilesReady(targetDir, model) || findExistingModelDir(model) != null
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

    /**
     * Resumable download. Keeps a `.part` file and uses HTTP Range to continue after
     * a network drop. Validates final size against Content-Length when available.
     */
    private fun downloadFileWithResume(
        model: ModelInfo,
        destination: File,
        partFile: File,
        onProgress: (String, Int, Int) -> Unit,
        attempt: Int,
    ): Boolean {
        var existing = 0L
        if (partFile.exists()) {
            existing = partFile.length()
            PilotLogger.i(TAG, "${model.name} resuming previous download from ${existing / 1024 / 1024} MB")
        }

        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0) {
                setRequestProperty("Range", "bytes=$existing-")
            }
        }

        try {
            connection.connect()
            val response = connection.responseCode
            val isResume = response == 206
            if (response !in 200..299 && !isResume) {
                throw IllegalStateException("Download returned HTTP $response")
            }

            val totalHeader = connection.getHeaderFieldLong("Content-Length", -1)
            val total = if (isResume && totalHeader >= 0) {
                (existing + totalHeader).toInt()
            } else if (totalHeader >= 0) {
                totalHeader.toInt()
            } else {
                -1
            }

            PilotLogger.i(
                TAG,
                "${model.name} attempt $attempt download started, total=${if (total > 0) "${total / 1024 / 1024} MB" else "unknown"}, resume=$isResume"
            )

            var downloaded = existing
            connection.inputStream.use { input ->
                val mode = if (isResume) "rw" else "rwd"
                RandomAccessFile(partFile, mode).use { raf ->
                    if (isResume) raf.seek(existing)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        raf.write(buffer, 0, count)
                        downloaded += count
                        onProgress(model.name, downloaded.toInt(), total)
                    }
                    raf.fd.sync()
                }
            }

            check(partFile.length() > MIN_ARCHIVE_BYTES) { "Downloaded archive is empty or truncated" }
            if (total > 0) {
                check(partFile.length() >= total * 0.99) {
                    "Downloaded size ${partFile.length()} is smaller than expected $total"
                }
            }
            check(partFile.renameTo(destination)) { "Could not finalize model download" }
            return true
        } catch (e: Exception) {
            PilotLogger.e(TAG, "${model.name} download attempt $attempt failed", e)
            return false
        } finally {
            connection.disconnect()
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

    fun getAsrModelPath(): String = (discoveredAsrDir ?: File(modelsDir, ASR_MODEL.destination)).absolutePath
    fun getTtsModelPath(): String = (discoveredTtsDir ?: File(modelsDir, TTS_MODEL.destination)).absolutePath
}
