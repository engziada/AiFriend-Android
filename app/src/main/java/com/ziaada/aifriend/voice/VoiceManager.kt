package com.ziaada.aifriend.voice

import android.app.Application
import android.util.Log
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

    enum class VoiceState { IDLE, DOWNLOADING_MODELS, INITIALIZING, READY, LISTENING, PROCESSING, SPEAKING, ERROR }
    data class DownloadProgress(val current: Int, val total: Int, val modelName: String)

    private var initialized = false

    suspend fun initialize(): Boolean {
        if (initialized) return true
        _errorMessage.value = null
        _state.value = VoiceState.DOWNLOADING_MODELS
        val modelsReady = modelManager.ensureModelsDownloaded { name, current, total ->
            _downloadProgress.value = DownloadProgress(current, total, name)
        }
        if (!modelsReady) return fail("تعذر تجهيز نماذج الصوت. تأكد من الاتصال بالإنترنت وأعد المحاولة")

        _state.value = VoiceState.INITIALIZING
        val asrReady = asrEngine.initialize(modelManager.getAsrModelPath())
        val ttsReady = ttsEngine.initialize(modelManager.getTtsModelPath())
        if (!asrReady || !ttsReady) return fail("تعذر تشغيل محرك الصوت على هذا الجهاز")

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
                Log.e(TAG, message)
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
                Log.e(TAG, message)
                _errorMessage.value = "تعذر تشغيل الرد الصوتي"
                _state.value = VoiceState.READY
            }
        })
        initialized = true
        _state.value = VoiceState.READY
        return true
    }

    private var partialResult: ((String) -> Unit)? = null
    private var finalResult: ((String) -> Unit)? = null

    fun startListening(onPartial: (String) -> Unit, onFinal: (String) -> Unit): Boolean {
        if (!initialized || _state.value == VoiceState.LISTENING) return false
        partialResult = onPartial
        finalResult = onFinal
        _errorMessage.value = null
        return asrEngine.startListening().also { started ->
            if (started) _state.value = VoiceState.LISTENING
            else {
                _errorMessage.value = "تعذر فتح الميكروفون، تأكد من إذن التسجيل"
                _state.value = VoiceState.READY
            }
        }
    }

    fun stopListening() {
        if (_state.value == VoiceState.LISTENING) {
            _state.value = VoiceState.PROCESSING
            asrEngine.stopListening()
        }
    }

    suspend fun speak(text: String): Boolean {
        if (!initialized) return false
        return ttsEngine.speak(text)
    }

    fun stopSpeaking() = ttsEngine.stop()

    private fun fail(message: String): Boolean {
        _errorMessage.value = message
        _state.value = VoiceState.ERROR
        return false
    }

    fun shutdown() {
        asrEngine.shutdown()
        ttsEngine.shutdown()
    }

    companion object { private const val TAG = "VoiceManager" }
}
