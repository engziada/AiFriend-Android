package com.ziaada.aifriend.voice

import android.content.Context
import android.util.Log
import com.ziaada.aifriend.pilot.PilotLogger
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
        private const val READ_TIMEOUT_MS = 60_000
        private const val MIN_ARCHIVE_BYTES = 1_000_000L
        private const val MIN_MODEL_FILE_BYTES = 1_024L
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
        val ok = downloadAndExtractIfNeeded(model, onProgress)
        if (ok) scanExistingModels()
        ok
    }

    private fun prepareModel(model: ModelInfo, onProgress: (String, Int, Int) -> Unit): Boolean {
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
        return downloadAndExtractIfNeeded(model, onProgress)
    }

    private fun findExistingModelDir(model: ModelInfo): File? {
        if (!modelsDir.isDirectory) return null
        return modelsDir.walkTopDown()
            .filter { it.isFile && it.name in model.requiredFiles }
            .mapNotNull { it.parentFile }
            .firstOrNull { parent -> modelFilesReady(parent, model) }
    }

    private fun downloadAndExtractIfNeeded(model: ModelInfo, onProgress: (String, Int, Int) -> Unit): Boolean {
        val targetDir = File(modelsDir, model.destination)
        val marker = File(targetDir, ".completed")
        if (modelFilesReady(targetDir, model)) {
            if (!marker.exists()) marker.writeText("ok")
            onProgress(model.name, 1, 1)
            PilotLogger.i(TAG, "Using existing ${model.name} model at $targetDir")
            return true
        }

        val archiveFile = File(modelsDir, "${model.destination.replace('/', '_')}.tar.bz2")
        return try {
            archiveFile.parentFile?.mkdirs()
            archiveFile.delete()
            PilotLogger.i(TAG, "Downloading ${model.name} from ${model.url}")
            downloadFile(model, archiveFile, onProgress)
            targetDir.deleteRecursively()
            check(targetDir.mkdirs() || targetDir.isDirectory) { "Cannot create $targetDir" }
            extractTarBz2(archiveFile, targetDir)
            check(modelFilesReady(targetDir, model)) { "Archive does not contain all required model files" }
            marker.writeText("ok")
            PilotLogger.i(TAG, "${model.name} model ready at $targetDir")
            true
        } catch (e: Exception) {
            PilotLogger.e(TAG, "Failed to prepare ${model.name}", e)
            targetDir.deleteRecursively()
            false
        } finally {
            archiveFile.delete()
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
            PilotLogger.i(TAG, "${model.name} download started, total=${if (total > 0) "${total / 1024 / 1024} MB" else "unknown"}")
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

    fun getAsrModelPath(): String = (discoveredAsrDir ?: File(modelsDir, ASR_MODEL.destination)).absolutePath
    fun getTtsModelPath(): String = (discoveredTtsDir ?: File(modelsDir, TTS_MODEL.destination)).absolutePath
}
