package com.ziaada.aifriend.voice

import android.app.Application
import com.ziaada.aifriend.pilot.PilotLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Coordinates fully on-device Sherpa-ONNX Arabic speech recognition and speech output. */
class VoiceManager(application: Application) {
    private val modelManager = ModelManager(application)
    private val asrEngine = SherpaAsrEngine()
    private val ttsEngine = SherpaTtsEngine()

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state = _state.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _downloadProgress = MutableStateFlow(DownloadProgress(0, 0, ""))
    val downloadProgress = _downloadProgress.asStateFlow()
    private val _modelStatus = MutableStateFlow(modelManager.status())
    val modelStatus = _modelStatus.asStateFlow()

    enum class VoiceState { IDLE, DOWNLOADING_MODELS, INITIALIZING, READY, LISTENING, PROCESSING, SPEAKING, ERROR }
    data class DownloadProgress(val current: Int, val total: Int, val modelName: String)

    private var initialized = false
    private var asrOnlyMode = false

    suspend fun initialize(): Boolean {
        PilotLogger.i(TAG, "initialize() called, alreadyInitialized=$initialized")
        if (initialized) return true
        _errorMessage.value = null
        _state.value = VoiceState.DOWNLOADING_MODELS
        val modelsReady = modelManager.ensureModelsDownloaded { name, current, total ->
            _downloadProgress.value = DownloadProgress(current, total, name)
        }
        _modelStatus.value = modelManager.status()
        if (!modelsReady) {
            // Fallback: if ASR model is present but TTS is not, still enable listening with text replies.
            return initAsrOnlyFallback()
        }

        return initEngines()
    }

    private suspend fun initEngines(): Boolean {
        _state.value = VoiceState.INITIALIZING
        val asrPath = modelManager.getAsrModelPath()
        val ttsPath = modelManager.getTtsModelPath()
        PilotLogger.i(TAG, "Initializing ASR with path: $asrPath")
        val asrReady = asrEngine.initialize(asrPath)
        PilotLogger.i(TAG, "Initializing TTS with path: $ttsPath")
        val ttsReady = ttsEngine.initialize(ttsPath)
        PilotLogger.i(TAG, "Engine init result: ASR=$asrReady TTS=$ttsReady")
        if (!asrReady) return fail("تعذر تشغيل محرك التعرف على الكلام")
        if (!ttsReady) {
            asrOnlyMode = true
            PilotLogger.w(TAG, "TTS failed; continuing in ASR-only mode")
        } else {
            asrOnlyMode = false
        }
        setupListeners()
        initialized = true
        _state.value = VoiceState.READY
        PilotLogger.i(TAG, "VoiceManager initialized and READY (asrOnly=$asrOnlyMode)")
        return true
    }

    private suspend fun initAsrOnlyFallback(): Boolean {
        _modelStatus.value = modelManager.status()
        val status = _modelStatus.value
        PilotLogger.i(TAG, "Checking ASR-only fallback: asrReady=${status.asrReady} ttsReady=${status.ttsReady}")
        if (!status.asrReady) {
            return fail("تعذر تجهيز نماذج الصوت. تأكد من الاتصال بالإنترنت وأعد المحاولة")
        }
        _state.value = VoiceState.INITIALIZING
        val asrPath = modelManager.getAsrModelPath()
        val asrReady = asrEngine.initialize(asrPath)
        if (!asrReady) return fail("تعذر تشغيل محرك التعرف على الكلام")
        asrOnlyMode = true
        setupListeners()
        initialized = true
        _state.value = VoiceState.READY
        _errorMessage.value = "نموذج الكلام غير متاح؛ الأوامر الصوتية ستظهر كنص فقط"
        PilotLogger.i(TAG, "VoiceManager initialized in ASR-only mode")
        return true
    }

    private fun setupListeners() {
        asrEngine.setListener(object : SherpaAsrEngine.Listener {
            override fun onPartial(text: String) { partialResult?.invoke(text) }
            override fun onFinal(text: String) {
                _state.value = VoiceState.PROCESSING
                if (text.isBlank()) {
                    _errorMessage.value = "ملحقتش أسمع كلام واضح، جرّب تاني"
                    _state.value = VoiceState.READY
                    finalResult?.invoke("")
                } else {
                    finalResult?.invoke(text)
                }
            }
            override fun onError(message: String) {
                PilotLogger.e(TAG, "ASR error: $message")
                _errorMessage.value = "حصلت مشكلة في تسجيل الصوت، جرّب تاني"
                _state.value = VoiceState.READY
                finalResult?.invoke("")
            }
        })
        ttsEngine.setListener(object : SherpaTtsEngine.Listener {
            override fun onStart() { _state.value = VoiceState.SPEAKING }
            override fun onProgress(progress: Float) { }
            override fun onComplete() { _state.value = VoiceState.READY }
            override fun onError(message: String) {
                PilotLogger.e(TAG, "TTS error: $message")
                _errorMessage.value = "تعذر تشغيل الرد الصوتي"
                _state.value = VoiceState.READY
            }
        })
    }

    /** Explicitly (re)download models; used by the pilot download button. */
    suspend fun downloadModels(): Boolean {
        PilotLogger.i(TAG, "downloadModels() started")
        _errorMessage.value = null
        _state.value = VoiceState.DOWNLOADING_MODELS
        initialized = false
        val ok = modelManager.ensureModelsDownloaded { name, current, total ->
            _downloadProgress.value = DownloadProgress(current, total, name)
        }
        _modelStatus.value = modelManager.status()
        PilotLogger.i(TAG, "downloadModels() finished: ok=$ok")
        if (!ok) {
            if (initAsrOnlyFallback()) return true
            _state.value = VoiceState.ERROR
            _errorMessage.value = "فشل تحميل النماذج. تأكد من الإنترنت."
            return false
        }
        return initEngines()
    }

    /** Re-scan for models and reinitialize engines. Used when models were manually copied. */
    suspend fun reinitialize(): Boolean {
        initialized = false
        _modelStatus.value = modelManager.status()
        return initialize()
    }

    private var partialResult: ((String) -> Unit)? = null
    private var finalResult: ((String) -> Unit)? = null

    fun startListening(onPartial: (String) -> Unit, onFinal: (String) -> Unit): Boolean {
        if (!initialized || _state.value == VoiceState.LISTENING) {
            PilotLogger.w(TAG, "startListening refused: initialized=$initialized state=${_state.value}")
            return false
        }
        partialResult = onPartial
        finalResult = onFinal
        _errorMessage.value = null
        PilotLogger.i(TAG, "Starting microphone listening")
        return asrEngine.startListening().also { started ->
            if (started) _state.value = VoiceState.LISTENING
            else {
                PilotLogger.e(TAG, "startListening() failed")
                _errorMessage.value = "تعذر فتح الميكروفون، تأكد من إذن التسجيل"
                _state.value = VoiceState.READY
            }
        }
    }

    fun stopListening() {
        PilotLogger.i(TAG, "stopListening() called")
        if (_state.value == VoiceState.LISTENING) {
            _state.value = VoiceState.PROCESSING
            asrEngine.stopListening()
        }
    }

    suspend fun speak(text: String): Boolean {
        PilotLogger.i(TAG, "TTS speak: ${text.take(80)}")
        if (!initialized) return false
        if (asrOnlyMode) {
            PilotLogger.i(TAG, "ASR-only mode: skipping audio output")
            return false
        }
        return ttsEngine.speak(text)
    }

    fun isAsrOnly(): Boolean = initialized && asrOnlyMode

    fun stopSpeaking() = ttsEngine.stop()

    private fun fail(message: String): Boolean {
        PilotLogger.e(TAG, "VoiceManager failure: $message")
        _errorMessage.value = message
        _state.value = VoiceState.ERROR
        return false
    }

    fun shutdown() {
        PilotLogger.i(TAG, "shutdown()")
        asrEngine.shutdown()
        ttsEngine.shutdown()
        initialized = false
        asrOnlyMode = false
    }

    companion object { private const val TAG = "VoiceManager" }
}
