package com.ziaada.aifriend.voice

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceManager(application: Application) : AndroidViewModel(application) {
    private val TAG = "VoiceManager"
    private val modelManager = ModelManager(getApplication())
    private val asrEngine = SherpaAsrEngine(getApplication())
    private val ttsEngine = SherpaTtsEngine(getApplication())

    private val _state = MutableStateFlow<VoiceState>(VoiceState.IDLE)
    val state = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress(0, 0, ""))
    val downloadProgress = _downloadProgress.asStateFlow()

    enum class VoiceState {
        IDLE,
        DOWNLOADING_MODELS,
        INITIALIZING,
        READY,
        LISTENING,
        PROCESSING,
        SPEAKING,
        ERROR,
    }

    data class DownloadProgress(
        val current: Int,
        val total: Int,
        val modelName: String,
    )

    init {
        asrEngine.setListener(object : SherpaAsrEngine.Listener {
            override fun onPartial(text: String) {
                // Handled by MainViewModel
            }
            override fun onFinal(text: String) {
                // Handled by MainViewModel
            }
            override fun onError(message: String) {
                _state.value = VoiceState.ERROR
            }
        })

        ttsEngine.setListener(object : SherpaTtsEngine.Listener {
            override fun onStart() {
                _state.value = VoiceState.SPEAKING
            }
            override fun onProgress(progress: Float) {}
            override fun onComplete() {
                _state.value = VoiceState.READY
            }
            override fun onError(message: String) {
                _state.value = VoiceState.ERROR
            }
        })
    }

    suspend fun initialize(): Boolean {
        _state.value = VoiceState.DOWNLOADING_MODELS
        val downloadOk = modelManager.ensureModelsDownloaded { name, current, total ->
            _downloadProgress.value = DownloadProgress(current, total, name)
        }
        if (!downloadOk) {
            _state.value = VoiceState.ERROR
            return false
        }

        _state.value = VoiceState.INITIALIZING
        val asrOk = asrEngine.initialize(modelManager.getAsrModelPath())
        val ttsOk = ttsEngine.initialize(modelManager.getTtsModelPath())

        if (asrOk && ttsOk) {
            _state.value = VoiceState.READY
            return true
        } else {
            _state.value = VoiceState.ERROR
            return false
        }
    }

    fun startListening(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        if (_state.value != VoiceState.READY) return
        _state.value = VoiceState.LISTENING

        asrEngine.setListener(object : SherpaAsrEngine.Listener {
            override fun onPartial(text: String) = onPartial(text)
            override fun onFinal(text: String) = onFinal(text)
            override fun onError(message: String) {
                _state.value = VoiceState.ERROR
            }
        })

        asrEngine.startListening()
    }

    fun stopListening() {
        asrEngine.stopListening()
        if (_state.value == VoiceState.LISTENING) {
            _state.value = VoiceState.READY
        }
    }

    suspend fun speak(text: String): Boolean {
        if (_state.value == VoiceState.SPEAKING) return false
        _state.value = VoiceState.SPEAKING
        return ttsEngine.speak(text)
    }

    fun stopSpeaking() {
        ttsEngine.stop()
        if (_state.value == VoiceState.SPEAKING) {
            _state.value = VoiceState.READY
        }
    }

    override fun onCleared() {
        asrEngine.shutdown()
        ttsEngine.shutdown()
        super.onCleared()
    }
}