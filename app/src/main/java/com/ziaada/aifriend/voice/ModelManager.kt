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

    fun getModelPaths(): ModelPaths = ModelPaths(asrDir, ttsDir, vadFile)

    fun checkModels(): ModelStatus {
        val asrMissing = mutableListOf<String>()
        val ttsMissing = mutableListOf<String>()

        if (!asrDir.exists()) {
            asrMissing.add("directory")
        } else {
            if (!File(asrDir, "encoder_model.ort").exists()) asrMissing.add("encoder_model.ort")
            if (!File(asrDir, "decoder_model_merged.ort").exists()) asrMissing.add("decoder_model_merged.ort")
            if (!File(asrDir, "tokens.txt").exists()) asrMissing.add("tokens.txt")
        }

        if (!ttsDir.exists()) {
            ttsMissing.add("directory")
        } else {
            if (!File(ttsDir, "ar_JO-kareem-medium.onnx").exists()) ttsMissing.add("ar_JO-kareem-medium.onnx")
            if (!File(ttsDir, "tokens.txt").exists()) ttsMissing.add("tokens.txt")
            if (!File(ttsDir, "espeak-ng-data").exists()) ttsMissing.add("espeak-ng-data/")
        }

        val vadMissing = !vadFile.exists()

        val ready = asrMissing.isEmpty() && ttsMissing.isEmpty() && !vadMissing
        Log.i(TAG, "Model check: ready=$ready, asrMissing=$asrMissing, ttsMissing=$ttsMissing, vadMissing=$vadMissing")
        return ModelStatus(ready, asrMissing, ttsMissing, vadMissing)
    }

    fun getModelPathsOrNull(): ModelPaths? {
        val status = checkModels()
        return if (status.ready) ModelPaths(asrDir, ttsDir, vadFile) else null
    }
}