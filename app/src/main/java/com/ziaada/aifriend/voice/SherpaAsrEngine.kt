package com.ziaada.aifriend.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SherpaAsrEngine(private val context: Context) {
    private val TAG = "SherpaAsrEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var recognizer: Any? = null // sherpa-onnx OnlineRecognizer
    private var isRunning = false

    private val _partialResults = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 10)
    val partialResults = _partialResults.asSharedFlow()

    private val _finalResults = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 10)
    val finalResults = _finalResults.asSharedFlow()

    private val _state = MutableSharedFlow<AsrState>(replay = 1)
    val state = _state.asSharedFlow()

    enum class AsrState {
        IDLE,
        INITIALIZING,
        READY,
        LISTENING,
        ERROR,
    }

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    private var listener: Listener? = null

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    suspend fun initialize(modelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            _state.tryEmit(AsrState.INITIALIZING)
            try {
                // This is where we'd initialize the sherpa-onnx recognizer
                // Since we can't include the AAR in this build, this is a template
                /*
                val config = OnlineRecognizerConfig().apply {
                    featConfig = OnlineFeatureConfig().apply {
                        sampleRate = 16000
                        featureDim = 80
                    }
                    modelConfig = OnlineModelConfig().apply {
                        moonshine = MoonshineModelConfig().apply {
                            preprocessor = "${modelPath}/preprocess.onnx"
                            encoder = "${modelPath}/encoder.onnx"
                            decoder = "${modelPath}/decoder.onnx"
                            tokens = "${modelPath}/tokens.txt"
                        }
                    }
                }
                recognizer = OnlineRecognizer(config)
                */
                _state.tryEmit(AsrState.READY)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ASR", e)
                _state.tryEmit(AsrState.ERROR)
                listener?.onError("ASR initialization failed: ${e.message}")
                false
            }
        }
    }

    fun startListening() {
        if (isRunning) return
        isRunning = true
        _state.tryEmit(AsrState.LISTENING)

        scope.launch {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 4
            )

            audioRecord?.startRecording()

            val buffer = ShortArray(bufferSize)
            while (isRunning && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Feed audio to sherpa-onnx recognizer
                    /*
                    val floatBuffer = buffer.take(read).map { it.toFloat() / 32768.0f }.toFloatArray()
                    recognizer.acceptWaveform(floatBuffer)
                    while (recognizer.isReady) {
                        recognizer.decode()
                    }
                    val partial = recognizer.getPartialResult()
                    if (partial.isNotBlank()) {
                        _partialResults.tryEmit(partial)
                        listener?.onPartial(partial)
                    }
                    */
                }
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
        scope.coroutineContext[Job]?.cancel()
        recognizer = null
    }
}