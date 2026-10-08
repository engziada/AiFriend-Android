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
    private var asrEngine: SherpaAsrEngine? = null
    private var ttsEngine: SherpaTtsEngine? = null

    private val _state = MutableStateFlow<VoiceState>(VoiceState.UNINITIALIZED)
    val state = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress(0, 0, ""))
    val downloadProgress = _downloadProgress.asStateFlow()

    enum class VoiceState {
        UNINITIALIZED,
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
        Log.d(TAG, "VoiceManager created")
    }

    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            val paths = modelManager.getModelPathsOrNull()
            if (paths == null) {
                val status = modelManager.checkModels()
                val missing = status.missingDescription()
                Log.w(TAG, "Models not ready: $missing")
                _state.value = VoiceState.ERROR
                return@withContext false
            }

            // Smart cast doesn't work across lambda boundary, use local variables
            val asrDir: java.io.File = paths.asrDir
            val ttsDir: java.io.File = paths.ttsDir
            val vadFile: java.io.File = paths.vadFile

            _state.value = VoiceState.INITIALIZING

            // Initialize ASR
            asrEngine = SherpaAsrEngine(getApplication(), asrDir, vadFile)
            val asrOk = asrEngine!!.initialize()

            // Initialize TTS
            ttsEngine = SherpaTtsEngine(getApplication(), ttsDir)
            val ttsOk = ttsEngine!!.initialize()

            if (asrOk && ttsOk) {
                _state.value = VoiceState.READY
                return@withContext true
            } else {
                _state.value = VoiceState.ERROR
                return@withContext false
            }
        }
    }

    fun startListening(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        val currentState = state.value
        if (currentState != VoiceState.READY) {
            Log.w(TAG, "Cannot start listening: voice not ready (state=$currentState)")
            return
        }
        _state.value = VoiceState.LISTENING

        asrEngine?.setListener(object : SherpaAsrEngine.Listener {
            override fun onPartial(text: String) = onPartial(text)
            override fun onFinal(text: String) = onFinal(text)
            override fun onError(message: String) {
                _state.value = VoiceState.ERROR
            }
            override fun onStateChange(state: SherpaAsrEngine.AsrState) {
                // State changes handled by ASR engine's flow
            }
        })

        asrEngine?.startListening()
    }

    fun stopListening() {
        asrEngine?.stopListening()
        if (state.value == VoiceState.LISTENING) {
            _state.value = VoiceState.READY
        }
    }

    suspend fun speak(text: String): Boolean {
        if (state.value == VoiceState.SPEAKING) return false
        _state.value = VoiceState.SPEAKING
        val result = ttsEngine?.speak(text) ?: false
        if (state.value == VoiceState.SPEAKING) {
            _state.value = VoiceState.READY
        }
        return result
    }

    fun stopSpeaking() {
        ttsEngine?.stop()
        if (state.value == VoiceState.SPEAKING) {
            _state.value = VoiceState.READY
        }
    }

    override fun onCleared() {
        asrEngine?.shutdown()
        ttsEngine?.shutdown()
        super.onCleared()
    }
}