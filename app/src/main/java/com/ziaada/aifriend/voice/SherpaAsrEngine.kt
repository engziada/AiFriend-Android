package com.ziaada.aifriend.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SherpaAsrEngine(
    private val context: Context,
    private val asrDir: java.io.File,
    private val vadFile: java.io.File,
) {
    private val TAG = "SherpaAsrEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var isInitialized = false

    // Speech segments channel for VAD-segmented audio
    private val segmentChannel = Channel<Pair<OfflineStream, SpeechSegment>>(Channel.UNLIMITED)

    // Public state
    private val _state = kotlinx.coroutines.flow.MutableSharedFlow<AsrState>(replay = 1, extraBufferCapacity = 10)
    val state = _state.asSharedFlow()

    // Final results (after VAD segment ends)
    private val _finalResults = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 10)
    val finalResults = _finalResults.asSharedFlow()

    // Audio RMS for face animation
    private val _rms = kotlinx.coroutines.flow.MutableSharedFlow<Float>(replay = 1, extraBufferCapacity = 10)
    val rms = _rms.asSharedFlow()

    enum class AsrState {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        LISTENING,
        PROCESSING_SEGMENT,
        ERROR,
    }

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
        fun onStateChange(state: AsrState)
    }

    private var listener: Listener? = null
    fun setListener(listener: Listener) { this.listener = listener }

    suspend fun initialize(): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            _state.tryEmit(AsrState.INITIALIZING)
            try {
                // Feature config: 16kHz, 80-dim fbank
                val featConfig = FeatureConfig(16000, 80, 0f)

                // Moonshine Arabic model config (quantized merged decoder)
                val moonshineConfig = com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig(
                    "", // preprocessor - not present in quantized model
                    asrDir.absolutePath + "/encoder_model.ort",
                    "", // uncached decoder - not used
                    "", // cached decoder - not used
                    asrDir.absolutePath + "/decoder_model_merged.ort",
                )

                val modelConfig = OfflineModelConfig(
                    com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig("", "", "", "", 0),
                    com.k2fsa.sherpa.onnx.OfflineFireRedAsrModelConfig(""),
                    moonshineConfig,
                    com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig("", "", false),
                    com.k2fsa.sherpa.onnx.OfflineDolphinModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineWenetCtcModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineMedAsrCtcModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineFunAsrNanoModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineFireRedAsrCtcModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineCanaryModelConfig(""),
                    com.k2fsa.sherpa.onnx.OfflineCohereTranscribeModelConfig(""),
                    asrDir.absolutePath + "/tokens.txt",
                    1, // numThreads
                    false, // debug
                    "cpu", // provider
                    "", // modelType
                    "", // modelingUnit
                    "", // bpeVocab
                )

                val recognizerConfig = OfflineRecognizerConfig(
                    featConfig,
                    modelConfig,
                    com.k2fsa.sherpa.onnx.HomophoneReplacerConfig(""),
                    "greedy_search", // decodingMethod
                    4, // maxActivePaths
                    "", // hotwordsFile
                    1.5f, // hotwordsScore
                    "", // ruleFsts
                    "", // ruleFars
                    0f, // blankPenalty
                )

                // Initialize recognizer
                recognizer = OfflineRecognizer(context.assets, recognizerConfig)

                // Initialize VAD (Silero VAD)
                val sileroConfig = SileroVadModelConfig(
                    vadFile.absolutePath,
                    0.5f, // threshold
                    0.15f, // minSilenceDuration
                    0.1f, // minSpeechDuration
                    512, // windowSize (v5 = 512)
                    10.0f, // maxSpeechDuration
                )

                val vadConfig = VadModelConfig(
                    sileroConfig,
                    com.k2fsa.sherpa.onnx.TenVadModelConfig(""),
                    16000,
                    1,
                    "cpu",
                    false,
                )

                vad = Vad(context.assets, vadConfig)

                isInitialized = true
                _state.tryEmit(AsrState.READY)
                true
            } catch (e: Exception) {
                Log.e(TAG, "ASR initialization failed", e)
                _state.tryEmit(AsrState.ERROR)
                listener?.onError("ASR init failed: ${e.message}")
                false
            }
        }
    }

    fun startListening() {
        if (isRunning || !isInitialized) return
        isRunning = true

        // Start VAD segment consumer
        scope.launch { consumeSegments() }

        // Start audio recording
        scope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 4,
            )

            this@SherpaAsrEngine.audioRecord = audioRecord
            audioRecord.startRecording()

            val buffer = FloatArray(512) // Silero VAD window = 512 samples
            val shortBuffer = ShortArray(512)

            _state.tryEmit(AsrState.LISTENING)

            while (isRunning && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                if (read > 0) {
                    // Convert to float for VAD/recognizer (-1.0 to 1.0)
                    for (i in 0 until read) {
                        buffer[i] = shortBuffer[i] / 32768.0f
                    }

                    // Compute RMS for face animation
                    var sumSq = 0f
                    for (i in 0 until read) {
                        sumSq += buffer[i] * buffer[i]
                    }
                    val rms = kotlin.math.sqrt(sumSq / read)
                    kotlinx.coroutines.runBlocking { _rms.tryEmit(rms) }

                    // Feed VAD
                    vad?.acceptWaveform(buffer.copyOf(read))

                    // Process VAD segments
                    while (vad?.empty() == false) {
                        if (vad?.isSpeechDetected() == true) {
                            val segment = vad?.front()
                            if (segment != null) {
                                val samples = segment.samples
                                val stream = recognizer?.createStream() ?: continue
                                stream.acceptWaveform(samples, 16000)
                                segmentChannel.trySend(stream to segment)
                            }
                            vad?.pop()
                        } else {
                            // Silence segment - pop and continue
                            vad?.pop()
                        }
                    }
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord read error: $read")
                }
            }

            audioRecord.stop()
            audioRecord.release()
            this@SherpaAsrEngine.audioRecord = null
        }
    }

    private suspend fun consumeSegments() {
        for ((stream, segment) in segmentChannel) {
            if (!isRunning) break
            _state.tryEmit(AsrState.PROCESSING_SEGMENT)
            try {
                recognizer?.decode(stream)
                val result = recognizer?.getResult(stream)
                val text = result?.text ?: ""
                if (text.isNotBlank()) {
                    _finalResults.tryEmit(text)
                    listener?.onFinal(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Segment decode failed", e)
            } finally {
                stream.release()
                _state.tryEmit(AsrState.LISTENING)
            }
        }
    }

    fun stopListening() {
        isRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _state.tryEmit(AsrState.READY)
    }

    fun shutdown() {
        stopListening()
        recognizer?.release()
        vad?.release()
        recognizer = null
        vad = null
        scope.coroutineContext[Job]?.cancel()
        isInitialized = false
    }

    fun isReady(): Boolean = isInitialized
}