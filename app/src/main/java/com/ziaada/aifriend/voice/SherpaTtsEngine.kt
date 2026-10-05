package com.ziaada.aifriend.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SherpaTtsEngine(private val context: Context) {
    private val TAG = "SherpaTtsEngine"
    private var tts: Any? = null // sherpa-onnx OfflineTts
    private var audioTrack: AudioTrack? = null
    private var isSpeaking = false

    enum class TtsState {
        IDLE,
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
    }

    private var listener: Listener? = null

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    suspend fun initialize(modelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Initialize sherpa-onnx TTS
                /*
                val config = OfflineTtsConfig().apply {
                    model = OfflineTtsModelConfig().apply {
                        vits = VitsModelConfig().apply {
                            model = "${modelPath}/model.onnx"
                            tokens = "${modelPath}/tokens.txt"
                            dataDir = "${modelPath}/espeak-ng-data"
                            dictDir = ""
                        }
                    }
                    ruleFsts = ""
                    maxNumSentences = 1
                }
                tts = OfflineTts(config)
                */
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TTS", e)
                listener?.onError("TTS initialization failed: ${e.message}")
                false
            }
        }
    }

    suspend fun speak(text: String): Boolean {
        if (isSpeaking) return false
        isSpeaking = true
        listener?.onStart()

        return withContext(Dispatchers.IO) {
            try {
                /*
                val audio = tts.generate(text, 1.0f) // speed = 1.0
                val samples = audio.samples // FloatArray
                val sampleRate = audio.sampleRate

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
                        val sample = (samples[written + i] * 32767.0f).coerceIn(-32768f, 32767f).toShort()
                        buffer[i] = sample
                    }
                    audioTrack?.write(buffer, 0, toWrite)
                    written += toWrite
                    listener?.onProgress(written.toFloat() / samples.size)
                }

                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                */
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
        tts = null
    }

    fun isSpeaking(): Boolean = isSpeaking
}