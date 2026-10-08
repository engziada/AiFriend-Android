package com.ziaada.aifriend.voice

import android.content.Context
import android.util.Log
import java.io.File

class ModelManager(private val context: Context) {
    private val TAG = "ModelManager"

    data class ModelPaths(
        val asrDir: File,
        val ttsDir: File,
        val vadFile: File,
    ) {
        fun allExist(): Boolean = asrDir.exists() && ttsDir.exists() && vadFile.exists()
    }

    data class ModelStatus(
        val ready: Boolean,
        val asrMissing: List<String>,
        val ttsMissing: List<String>,
        val vadMissing: Boolean,
    ) {
        fun missingDescription(): String {
            val parts = mutableListOf<String>()
            if (asrMissing.isNotEmpty()) parts.add("ASR: ${asrMissing.joinToString(", ")}")
            if (ttsMissing.isNotEmpty()) parts.add("TTS: ${ttsMissing.joinToString(", ")}")
            if (vadMissing) parts.add("VAD: silero_vad.onnx")
            return if (parts.isEmpty()) "All models present" else parts.joinToString("; ")
        }
    }

    private val externalModelsDir: File by lazy {
        File(context.getExternalFilesDir(null)!!, "models")
    }

    private val asrDir: File by lazy { File(externalModelsDir, "asr") }
    private val ttsDir: File by lazy { File(externalModelsDir, "tts") }
    private val vadFile: File by lazy { File(externalModelsDir, "vad/silero_vad.onnx") }

    // Fallback search paths for models (user may have placed them elsewhere)
    private val fallbackAsrDirs = listOf(
        File(context.getExternalFilesDir(null)!!, "models/asr"),
        File("/sdcard/AiFriend/models/asr"),
        File("/sdcard/Models/asr"),
        File("/sdcard/Download/asr"),
        File("/sdcard/AiFriend/models/asr"),
    )

    private val fallbackTtsDirs = listOf(
        File(context.getExternalFilesDir(null)!!, "models/tts"),
        File("/sdcard/AiFriend/models/tts"),
        File("/sdcard/Models/tts"),
        File("/sdcard/Download/tts"),
        File("/sdcard/AiFriend/models/tts"),
    )

    private val fallbackVadFiles = listOf(
        File(context.getExternalFilesDir(null)!!, "models/vad/silero_vad.onnx"),
        File("/sdcard/AiFriend/models/vad/silero_vad.onnx"),
        File("/sdcard/Models/vad/silero_vad.onnx"),
        File("/sdcard/Download/vad/silero_vad.onnx"),
        File("/sdcard/AiFriend/models/vad/silero_vad.onnx"),
    )

    fun getModelPaths(): ModelPaths = ModelPaths(findAsrDir()!!, findTtsDir()!!, findVadFile()!!)

    private fun findAsrDir(): File? {
        val required = listOf("encoder_model.ort", "decoder_model_merged.ort", "tokens.txt")
        return (listOf(asrDir) + fallbackAsrDirs).firstOrNull { dir ->
            dir.exists() && required.all { File(dir, it).exists() }
        }
    }

    private fun findTtsDir(): File? {
        val required = listOf("ar_JO-kareem-medium.onnx", "tokens.txt")
        val requiredDir = "espeak-ng-data"
        return (listOf(ttsDir) + fallbackTtsDirs).firstOrNull { dir ->
            dir.exists() && required.all { File(dir, it).exists() } && File(dir, requiredDir).exists()
        }
    }

    private fun findVadFile(): File? {
        return (listOf(vadFile) + fallbackVadFiles).firstOrNull { it.exists() }
    }

    fun getModelPathsOrNull(): ModelPaths? {
        val asr = findAsrDir()
        val tts = findTtsDir()
        val vad = findVadFile()
        return if (asr != null && tts != null && vad != null) ModelPaths(asr, tts, vad) else null
    }

    fun checkModels(): ModelStatus {
        val asrDirFound = findAsrDir()
        val ttsDirFound = findTtsDir()
        val vadFileFound = findVadFile()

        val asrMissing = mutableListOf<String>()
        val ttsMissing = mutableListOf<String>()

        asrDirFound?.let { dir ->
            if (!File(dir, "encoder_model.ort").exists()) asrMissing.add("encoder_model.ort")
            if (!File(dir, "decoder_model_merged.ort").exists()) asrMissing.add("decoder_model_merged.ort")
            if (!File(dir, "tokens.txt").exists()) asrMissing.add("tokens.txt")
        } ?: asrMissing.add("directory (tried: ${(listOf(asrDir) + fallbackAsrDirs).joinToString(", "){it.absolutePath}} )")

        ttsDirFound?.let { dir ->
            if (!File(dir, "ar_JO-kareem-medium.onnx").exists()) ttsMissing.add("ar_JO-kareem-medium.onnx")
            if (!File(dir, "tokens.txt").exists()) ttsMissing.add("tokens.txt")
            if (!File(dir, "espeak-ng-data").exists()) ttsMissing.add("espeak-ng-data/")
        } ?: ttsMissing.add("directory (tried: ${(listOf(ttsDir) + fallbackTtsDirs).joinToString(", "){it.absolutePath}} )")

        val vadMissing = vadFileFound?.exists() != true

        val ready = asrMissing.isEmpty() && ttsMissing.isEmpty() && !vadMissing
        Log.i(TAG, "Model check: ready=$ready, asrMissing=$asrMissing, ttsMissing=$ttsMissing, vadMissing=$vadMissing")
        return ModelStatus(ready, asrMissing, ttsMissing, vadMissing)
    }
}