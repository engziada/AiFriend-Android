package com.ziaada.aifriend.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaada.aifriend.byd.BydConnectionManager
import com.ziaada.aifriend.byd.BydConnectionState
import com.ziaada.aifriend.byd.BydVehicleController
import com.ziaada.aifriend.domain.AiFriendStateStore
import com.ziaada.aifriend.domain.AssistantEngine
import com.ziaada.aifriend.domain.FaceState
import com.ziaada.aifriend.domain.VehicleController
import com.ziaada.aifriend.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
        voiceManager.startListening(
            onPartial = { text -> updatePartial(text) },
            onFinal = { text -> finishListening(text) }
        )
        store.update {
            it.copy(faceState = FaceState.LISTENING, isListening = true, vehicleMessage = "بسمعك...")
        }
    }

    fun updatePartial(text: String) {
        store.update { it.copy(transcript = text, faceState = FaceState.LISTENING) }
    }

    fun finishListening(text: String) {
        store.update {
            it.copy(
                transcript = text,
                isListening = false,
                isProcessing = true,
                faceState = FaceState.THINKING,
                vehicleMessage = "بفكر...",
            )
        }
        viewModelScope.launch {
            val response = assistant.handleArabicCommand(text)
            store.update {
                it.copy(
                    isProcessing = false,
                    faceState = FaceState.SPEAKING,
                    isSpeaking = true,
                    vehicleMessage = response,
                )
            }
            // Speak the response
            voiceManager.speak(response)
        }
    }

    fun stopListening() {
        voiceManager.stopListening()
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
        store.update {
            it.copy(isSpeaking = false)
        }
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
        super.onCleared()
    }
}
