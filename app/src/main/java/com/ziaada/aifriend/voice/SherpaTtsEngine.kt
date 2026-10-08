package com.ziaada.aifriend.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SherpaTtsEngine(
    private val context: Context,
    private val ttsDir: java.io.File,
) {
    private val TAG = "SherpaTtsEngine"
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var isSpeaking = false
    private var isInitialized = false

    enum class TtsState {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        SPEAKING,
        ERROR,
    }

    interface Listener {
        fun onStart()
        fun onProgress(progress: Float) // 0.0 to 1.0
        fun onComplete()
        fun onError(message: String)
        fun onStateChange(state: TtsState)
    }

    private var listener: Listener? = null
    fun setListener(listener: Listener) { this.listener = listener }

    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Piper TTS (VITS) model config
                val vitsConfig = OfflineTtsVitsModelConfig(
                    ttsDir.absolutePath + "/ar_JO-kareem-medium.onnx", // model
                    ttsDir.absolutePath + "/tokens.txt",               // tokens
                    ttsDir.absolutePath + "/espeak-ng-data",           // dataDir (espeak-ng-data directory)
                    "", // lexicon - not needed for piper
                    "", // dictDir - not needed
                    0.667f, // noiseScale
                    1.0f, // noiseScaleW
                    1.0f, // lengthScale
                )

                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    matcha = com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig(""),
                    kokoro = com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig(""),
                    zipvoice = com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig(""),
                    kitten = com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig(""),
                    pocket = com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig(""),
                    supertonic = com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig(""),
                    1, // numThreads
                    false, // debug
                    "cpu", // provider
                )

                val ttsConfig = OfflineTtsConfig(
                    model = modelConfig,
                    ruleFsts = "",
                    ruleFars = "",
                    maxNumSentences = 1,
                    silenceScale = 0.5f,
                )

                tts = OfflineTts(context.assets, ttsConfig)
                isInitialized = true
                true
            } catch (e: Exception) {
                Log.e(TAG, "TTS initialization failed", e)
                listener?.onError("TTS init failed: ${e.message}")
                false
            }
        }
    }

    suspend fun speak(text: String): Boolean {
        if (isSpeaking || !isInitialized || tts == null) return false
        isSpeaking = true
        listener?.onStart()

        return withContext(Dispatchers.IO) {
            try {
                val generatedAudio = tts!!.generate(text, 0, 1.0f) // speaker=0, speed=1.0
                val samples = generatedAudio.samples
                val sampleRate = generatedAudio.sampleRate

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    minBufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                audioTrack?.play()

                val buffer = ShortArray(1024)
                var written = 0
                while (written < samples.size && isSpeaking) {
                    val toWrite = minOf(buffer.size, samples.size - written)
                    for (i in 0 until toWrite) {
                        val sample = (samples[written + i] * 32767.0f).coerceIn(-32768f, 32767f).toInt().toShort()
                        buffer[i] = sample
                    }
                    audioTrack?.write(buffer, 0, toWrite)
                    written += toWrite
                    val progress = written.toFloat() / samples.size
                    listener?.onProgress(progress)
                }

                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                listener?.onComplete()
                true
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak failed", e)
                listener?.onError("TTS speak failed: ${e.message}")
                false
            } finally {
                isSpeaking = false
            }
        }
    }

    fun stop() {
        isSpeaking = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun shutdown() {
        stop()
        tts?.free()
        tts?.release()
        tts = null
        isInitialized = false
    }

    fun isReady(): Boolean = isInitialized
    fun isSpeaking(): Boolean = isSpeaking
}