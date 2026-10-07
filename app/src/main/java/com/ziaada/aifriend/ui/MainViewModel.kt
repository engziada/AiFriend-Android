package com.ziaada.aifriend.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaada.aifriend.byd.BydConnectionManager
import com.ziaada.aifriend.byd.BydConnectionState
import com.ziaada.aifriend.byd.BydVehicleController
import com.ziaada.aifriend.domain.AiFriendStateStore
import com.ziaada.aifriend.domain.AssistantEngine
import com.ziaada.aifriend.domain.CaptionSpeaker
import com.ziaada.aifriend.domain.FaceState
import com.ziaada.aifriend.domain.VehicleController
import com.ziaada.aifriend.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AiFriendStateStore()
    private var vehicleController: VehicleController = com.ziaada.aifriend.domain.StubVehicleController()
    private var assistant = AssistantEngine(getApplication(), vehicleController)

    private val connectionManager = BydConnectionManager(getApplication())
    private val _bydState = MutableStateFlow<BydConnectionState>(BydConnectionState.DISCONNECTED)
    val bydState = _bydState.asStateFlow()

    private val voiceManager = VoiceManager(getApplication())
    val voiceState = voiceManager.state
    val voiceDownloadProgress = voiceManager.downloadProgress
    val voiceError = voiceManager.errorMessage
    private val _hasOpenRouterKey = MutableStateFlow(assistant.hasLlmKey())
    val hasOpenRouterKey = _hasOpenRouterKey.asStateFlow()
    private var climateEnabled = false

    val state = store.state

    init {
        observeConnection()
        initializeVoice()
    }

    private fun initializeVoice() {
        viewModelScope.launch {
            val success = voiceManager.initialize()
            if (!success) {
                // Voice initialization failed, but continue with limited functionality
            }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            connectionManager.stateFlow.collect { state ->
                _bydState.value = state
                if (state == BydConnectionState.READY || state == BydConnectionState.DEGRADED) {
                    val vc = connectionManager.vehicleController
                    if (vc != null) {
                        vehicleController = vc
                        assistant = AssistantEngine(getApplication(), vehicleController)
                    }
                }
            }
        }
        connectionManager.start()
    }

    fun beginListening() {
        if (voiceManager.state.value == VoiceManager.VoiceState.ERROR || voiceManager.state.value == VoiceManager.VoiceState.IDLE) {
            viewModelScope.launch {
                if (voiceManager.initialize()) beginListening()
            }
            return
        }
        val started = voiceManager.startListening(
            onPartial = { text -> updatePartial(text) },
            onFinal = { text -> finishListening(text) }
        )
        if (started) {
            store.update {
                it.copy(
                    faceState = FaceState.LISTENING,
                    isListening = true,
                    isProcessing = false,
                    transcript = "",
                    caption = "بسمعك… قولّي عايز إيه",
                    captionSpeaker = CaptionSpeaker.YOU,
                    vehicleMessage = "بسمعك...",
                )
            }
        }
    }

    fun updatePartial(text: String) {
        store.update {
            it.copy(
                transcript = text,
                caption = text,
                captionSpeaker = CaptionSpeaker.YOU,
                faceState = FaceState.LISTENING,
            )
        }
    }

    fun finishListening(text: String) {
        if (text.isBlank()) {
            store.update {
                it.copy(
                    faceState = FaceState.IDLE,
                    isListening = false,
                    isProcessing = false,
                    caption = "أنا آدم، ما سمعتش كلام واضح. جرّب تاني.",
                    captionSpeaker = CaptionSpeaker.ASSISTANT,
                )
            }
            return
        }
        store.update {
            it.copy(
                transcript = text,
                caption = text,
                captionSpeaker = CaptionSpeaker.YOU,
                isListening = false,
                isProcessing = true,
                faceState = FaceState.THINKING,
                vehicleMessage = "بفكر...",
            )
        }
        viewModelScope.launch {
            val connected = _bydState.value == BydConnectionState.READY || _bydState.value == BydConnectionState.DEGRADED
            val response = runCatching { assistant.handleArabicCommand(text, connected) }
                .getOrElse { error -> "أنا آدم: ${error.message ?: "حصلت مشكلة وأنا بنفذ طلبك، جرّب تاني"}" }
            store.update {
                it.copy(
                    isProcessing = false,
                    faceState = FaceState.SPEAKING,
                    isSpeaking = true,
                    caption = response,
                    captionSpeaker = CaptionSpeaker.ASSISTANT,
                    vehicleMessage = response,
                )
            }
            // Speak the response
            voiceManager.speak(response)
            resetToIdle()
        }
    }

    fun adjustClimateTemperature(delta: Int) {
        if (_bydState.value != BydConnectionState.READY && _bydState.value != BydConnectionState.DEGRADED) {
            store.update { it.copy(vehicleMessage = "اتصال السيارة مش جاهز للتحكم في التكييف") }
            return
        }
        val temperature = (state.value.climateSetpointC + delta).coerceIn(16, 30)
        viewModelScope.launch {
            val response = runCatching {
                vehicleController.setClimateTemperature(temperature)
                "ضبطت الحرارة على $temperature درجة"
            }.getOrElse { "تعذر ضبط حرارة التكييف" }
            store.update { it.copy(climateSetpointC = temperature, vehicleMessage = response, faceState = FaceState.HAPPY) }
        }
    }

    fun toggleClimate() {
        if (_bydState.value != BydConnectionState.READY && _bydState.value != BydConnectionState.DEGRADED) {
            store.update { it.copy(vehicleMessage = "اتصال السيارة مش جاهز للتحكم في التكييف") }
            return
        }
        val enabled = !climateEnabled
        climateEnabled = enabled
        viewModelScope.launch {
            val response = runCatching {
                vehicleController.setClimatePower(enabled)
                if (enabled) "شغلت التكييف" else "طفيت التكييف"
            }.getOrElse { "تعذر التحكم في التكييف" }
            store.update { it.copy(vehicleMessage = response, faceState = FaceState.HAPPY) }
        }
    }

    fun stopListening() {
        store.update {
            it.copy(
                faceState = FaceState.THINKING,
                isListening = false,
                isProcessing = true,
                caption = "بحول كلامك لنص…",
                captionSpeaker = CaptionSpeaker.ASSISTANT,
                vehicleMessage = "بحول كلامك لنص…",
            )
        }
        voiceManager.stopListening()
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
        store.update {
            it.copy(isSpeaking = false)
        }
    }

    fun saveOpenRouterKey(key: String) {
        assistant.setLlmApiKey(key.trim())
        _hasOpenRouterKey.value = assistant.hasLlmKey()
    }

    fun clearOpenRouterKey() {
        assistant.setLlmApiKey("")
        _hasOpenRouterKey.value = false
    }

    fun resetToIdle() {
        store.update {
            it.copy(
                faceState = FaceState.IDLE,
                isListening = false,
                isProcessing = false,
                isSpeaking = false,
                vehicleMessage = "جاهز أسمعك",
            )
        }
    }

    override fun onCleared() {
        connectionManager.stop()
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        voiceManager.shutdown()
        super.onCleared()
    }
}
