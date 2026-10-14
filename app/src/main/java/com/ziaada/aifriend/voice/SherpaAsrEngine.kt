package com.ziaada.aifriend.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.ziaada.aifriend.pilot.PilotLogger
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

/** Offline Moonshine v2 transcription. The whole utterance is decoded on-device. */
class SherpaAsrEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var isRunning = false
    @Volatile private var recognizer: OfflineRecognizer? = null
    private var listener: Listener? = null

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
        /** Live microphone RMS level (0f..1f+) reported while listening, for UI metering. */
        fun onLevel(level: Float) {}
    }

    fun setListener(listener: Listener) { this.listener = listener }

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = File(modelPath)
            val encoder = root.findFile("encoder_model.ort") ?: error("Moonshine encoder is missing")
            val decoder = root.findFile("decoder_model_merged.ort") ?: error("Moonshine decoder is missing")
            val tokens = root.findFile("tokens.txt") ?: error("Moonshine tokens are missing")
            val modelConfig = OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    encoder = encoder.absolutePath,
                    mergedDecoder = decoder.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
            )
            recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = modelConfig))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize offline Arabic ASR", e)
            listener?.onError("ASR initialization failed: ${e.message}")
            false
        }
    }

    fun startListening(): Boolean {
        if (isRunning || recognizer == null) return false
        val bufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferBytes <= 0) {
            listener?.onError("AudioRecord returned invalid buffer size: $bufferBytes")
            return false
        }
        val recorder = createRecorder(bufferBytes)
        if (recorder == null) {
            listener?.onError("AudioRecord did not initialize")
            return false
        }

        audioRecord = recorder
        isRunning = true
        scope.launch { captureUtterance(recorder) }
        return true
    }

    private fun createRecorder(bufferBytes: Int): AudioRecord? {
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val recorder = try {
                AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, maxOf(bufferBytes * 2, SAMPLE_RATE * 2))
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord source $source failed", e)
                continue
            }
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            recorder.release()
        }
        return null
    }

    private suspend fun captureUtterance(recorder: AudioRecord) {
        val samples = ArrayList<Float>(SAMPLE_RATE * MAX_UTTERANCE_SECONDS)
        val buffer = ShortArray(2048)
        var speechStarted = false
        var quietMs = 0
        var noSpeechMs = 0
        var elapsedMs = 0

        // Adaptive noise floor: track the quietest RMS observed so a low-gain car mic
        // still crosses the speech threshold. Calibrated during the first CALIBRATION_MS.
        var noiseFloor = Double.MAX_VALUE
        var peakRms = 0.0
        var lastLogMs = 0

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) error("Microphone did not start")
            PilotLogger.i(TAG, "Microphone recording started (source adaptive, rate=$SAMPLE_RATE)")

            while (isRunning && samples.size < SAMPLE_RATE * MAX_UTTERANCE_SECONDS) {
                val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count < 0) {
                    if (!isRunning) break
                    error("AudioRecord read failed: $count")
                }
                if (count == 0) continue

                var energy = 0.0
                for (i in 0 until count) {
                    val sample = buffer[i] / 32768.0f
                    samples.add(sample)
                    energy += sample * sample
                }
                val chunkMs = count * 1000 / SAMPLE_RATE
                elapsedMs += chunkMs
                val rms = sqrt(energy / count)
                if (rms > peakRms) peakRms = rms

                // Report a smoothed level to the UI (scaled so typical speech ~0.3-1.0).
                listener?.onLevel((rms * LEVEL_UI_SCALE).toFloat().coerceIn(0f, 1.5f))

                if (elapsedMs <= CALIBRATION_MS) {
                    if (rms < noiseFloor) noiseFloor = rms
                }
                val threshold = speechThreshold(noiseFloor, elapsedMs)

                if (elapsedMs - lastLogMs >= 500) {
                    lastLogMs = elapsedMs
                    PilotLogger.d(
                        TAG,
                        "mic rms=${"%.4f".format(rms)} peak=${"%.4f".format(peakRms)} " +
                            "floor=${if (noiseFloor == Double.MAX_VALUE) "n/a" else "%.4f".format(noiseFloor)} " +
                            "thr=${"%.4f".format(threshold)} speech=$speechStarted elapsed=${elapsedMs}ms",
                    )
                }

                if (rms >= threshold) {
                    if (!speechStarted) PilotLogger.i(TAG, "Speech detected at ${elapsedMs}ms (rms=${"%.4f".format(rms)} >= thr=${"%.4f".format(threshold)})")
                    speechStarted = true
                    quietMs = 0
                    noSpeechMs = 0
                } else if (speechStarted) {
                    quietMs += chunkMs
                } else {
                    noSpeechMs += chunkMs
                }

                if (speechStarted && quietMs >= END_SILENCE_MS) break
                if (!speechStarted && noSpeechMs >= NO_SPEECH_TIMEOUT_MS) break
            }
        } catch (e: Exception) {
            if (isRunning) {
                PilotLogger.e(TAG, "Microphone capture failed: ${e.message}")
                Log.e(TAG, "Microphone capture failed", e)
                listener?.onError("Microphone capture failed: ${e.message}")
                isRunning = false
                listener?.onLevel(0f)
                return
            }
        } finally {
            releaseRecorder(recorder)
            isRunning = false
            listener?.onLevel(0f)
        }

        PilotLogger.i(
            TAG,
            "Capture ended: speechStarted=$speechStarted samples=${samples.size} peakRms=${"%.4f".format(peakRms)} elapsed=${elapsedMs}ms",
        )
        if (speechStarted && samples.size >= MIN_UTTERANCE_SAMPLES) {
            decode(samples.toFloatArray())
        } else {
            if (!speechStarted) {
                PilotLogger.w(
                    TAG,
                    "No speech detected (peak rms=${"%.4f".format(peakRms)}). Mic gain too low or mic held by another app?",
                )
            }
            listener?.onFinal("")
        }
    }

    /** Speech RMS threshold: adaptive once the noise floor is calibrated, else a safe default. */
    private fun speechThreshold(noiseFloor: Double, elapsedMs: Int): Double {
        if (elapsedMs <= CALIBRATION_MS || noiseFloor == Double.MAX_VALUE) return SPEECH_RMS_THRESHOLD
        // Require clear separation above ambient noise, but never below an absolute floor.
        return maxOf(SPEECH_RMS_MIN, noiseFloor * NOISE_FLOOR_MULTIPLIER)
    }

    private suspend fun decode(samples: FloatArray) = withContext(Dispatchers.IO) {
        val currentRecognizer = recognizer ?: run {
            PilotLogger.e(TAG, "decode() called but recognizer is null")
            listener?.onFinal("")
            return@withContext
        }
        val stream = currentRecognizer.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
            currentRecognizer.decode(stream)
            val text = currentRecognizer.getResult(stream).text.trim()
            PilotLogger.i(TAG, "ASR decoded ${samples.size} samples (${samples.size / SAMPLE_RATE}s) -> '$text'")
            listener?.onFinal(text)
        } catch (e: Exception) {
            PilotLogger.e(TAG, "Offline transcription failed: ${e.message}")
            Log.e(TAG, "Offline transcription failed", e)
            listener?.onError("Offline transcription failed: ${e.message}")
        } finally {
            stream.release()
        }
    }

    fun stopListening() {
        isRunning = false
        try {
            val recorder = audioRecord
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Recorder had already stopped", e)
        }
    }

    private fun releaseRecorder(recorder: AudioRecord) {
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } catch (_: IllegalStateException) { }
        recorder.release()
        if (audioRecord === recorder) audioRecord = null
    }

    fun shutdown() {
        stopListening()
        scope.coroutineContext[Job]?.cancel()
        recognizer?.release()
        recognizer = null
    }

    private fun File.findFile(name: String): File? =
        walkTopDown().firstOrNull { it.isFile && it.name == name }

    companion object {
        private const val TAG = "SherpaAsrEngine"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_UTTERANCE_SECONDS = 15
        private const val END_SILENCE_MS = 1_100
        private const val NO_SPEECH_TIMEOUT_MS = 5_000
        private const val MIN_UTTERANCE_SAMPLES = 1_600
        private const val SPEECH_RMS_THRESHOLD = 0.012
        private const val CALIBRATION_MS = 500
        private const val SPEECH_RMS_MIN = 0.004
        private const val NOISE_FLOOR_MULTIPLIER = 2.8
        private const val LEVEL_UI_SCALE = 6.0
    }
}
