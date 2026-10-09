package com.ziaada.aifriend.voice

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Offline Arabic Piper VITS speech synthesis using Sherpa-ONNX. */
class SherpaTtsEngine {
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var isSpeaking = false
    private var listener: Listener? = null

    enum class TtsState { IDLE, INITIALIZING, READY, SPEAKING, ERROR }

    interface Listener {
        fun onStart()
        fun onProgress(progress: Float)
        fun onComplete()
        fun onError(message: String)
    }

    fun setListener(listener: Listener) { this.listener = listener }

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = File(modelPath)
            val model = root.findFile("ar_JO-kareem-medium.onnx") ?: error("Arabic Piper model is missing")
            val tokens = root.findFile("tokens.txt") ?: error("Piper tokens are missing")
            val dataDir = root.findDirectory("espeak-ng-data") ?: error("eSpeak-ng data is missing")
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = model.absolutePath,
                        tokens = tokens.absolutePath,
                        dataDir = dataDir.absolutePath,
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            )
            tts = OfflineTts(config = config)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize offline Arabic TTS", e)
            listener?.onError("TTS initialization failed: ${e.message}")
            false
        }
    }

    suspend fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (isSpeaking || text.isBlank()) return false
        isSpeaking = true
        listener?.onStart()
        return withContext(Dispatchers.IO) {
            try {
                val audio = engine.generateWithConfig(
                    text = text,
                    config = GenerationConfig(sid = 0, speed = 1.0f, silenceScale = 0.2f),
                )
                if (audio.samples.isEmpty()) error("TTS produced no audio")
                playSamples(audio.samples, audio.sampleRate)
                listener?.onComplete()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Offline TTS playback failed", e)
                listener?.onError("TTS playback failed: ${e.message}")
                false
            } finally {
                releaseAudioTrack()
                isSpeaking = false
            }
        }
    }

    private fun playSamples(samples: FloatArray, sampleRate: Int) {
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferBytes > 0) { "AudioTrack returned invalid buffer size: $minBufferBytes" }
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(minBufferBytes, 4096),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack did not initialize" }
        audioTrack = track
        track.play()

        val shortBuffer = ShortArray(1024)
        var offset = 0
        while (offset < samples.size && isSpeaking) {
            val count = minOf(shortBuffer.size, samples.size - offset)
            for (index in 0 until count) {
                shortBuffer[index] = (samples[offset + index] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            }
            var written = 0
            while (written < count && isSpeaking) {
                val result = track.write(shortBuffer, written, count - written, AudioTrack.WRITE_BLOCKING)
                check(result > 0) { "AudioTrack write failed: $result" }
                written += result
            }
            offset += count
            listener?.onProgress(offset.toFloat() / samples.size)
        }
    }

    fun stop() {
        isSpeaking = false
        releaseAudioTrack()
    }

    private fun releaseAudioTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
        track.release()
    }

    fun shutdown() {
        stop()
        tts?.release()
        tts = null
    }

    fun isSpeaking(): Boolean = isSpeaking

    private fun File.findFile(name: String): File? = walkTopDown().firstOrNull { it.isFile && it.name == name }
    private fun File.findDirectory(name: String): File? = walkTopDown().firstOrNull { it.isDirectory && it.name == name }

    companion object { private const val TAG = "SherpaTtsEngine" }
}
