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
import com.ziaada.aifriend.pilot.PilotLogger
import com.ziaada.aifriend.voice.ModelManager
import com.ziaada.aifriend.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val modelStatus = voiceManager.modelStatus
    val logs = PilotLogger.logs
    private val _hasOpenRouterKey = MutableStateFlow(assistant.hasLlmKey())
    val hasOpenRouterKey = _hasOpenRouterKey.asStateFlow()
    private var climateEnabled = false

    val state = store.state

    init {
        PilotLogger.i("MainViewModel", "Created")
        observeConnection()
        initializeVoice()
    }

    private fun initializeVoice() {
        viewModelScope.launch {
            PilotLogger.i("MainViewModel", "initializeVoice() launching")
            val success = voiceManager.initialize()
            PilotLogger.i("MainViewModel", "initializeVoice() success=$success asrOnly=${voiceManager.isAsrOnly()}")
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            connectionManager.stateFlow.collect { state ->
                _bydState.value = state
                PilotLogger.i("MainViewModel", "BYD state: $state")
                if (state == BydConnectionState.READY || state == BydConnectionState.DEGRADED) {
                    val vc = connectionManager.vehicleController
                    if (vc != null) {
                        vehicleController = vc
                        assistant = AssistantEngine(getApplication(), vehicleController)
                        PilotLogger.i("MainViewModel", "Vehicle controller attached")
                    }
                }
            }
        }
        connectionManager.start()
    }

    fun beginListening() {
        PilotLogger.i("MainViewModel", "beginListening() state=${voiceManager.state.value}")
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
        } else {
            PilotLogger.w("MainViewModel", "beginListening() returned false")
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
        PilotLogger.i("MainViewModel", "finishListening: '$text'")
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
                .getOrElse { error ->
                    PilotLogger.e("MainViewModel", "assistant.handleArabicCommand failed", error)
                    "أنا آدم: ${error.message ?: "حصلت مشكلة وأنا بنفذ طلبك، جرّب تاني"}"
                }
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

    // ─── Pilot test helpers ────────────────────────────────────────────────────

    fun downloadVoiceModels() {
        viewModelScope.launch {
            PilotLogger.i("MainViewModel", "Pilot: explicit model download requested")
            voiceManager.downloadModels()
        }
    }

    fun rescanVoiceModels() {
        viewModelScope.launch {
            PilotLogger.i("MainViewModel", "Pilot: rescan/reinitialize voice requested")
            val success = voiceManager.reinitialize()
            PilotLogger.i("MainViewModel", "Pilot: reinitialize success=$success")
        }
    }

    fun testTts() {
        viewModelScope.launch {
            PilotLogger.i("MainViewModel", "Pilot: testTts")
            val text = "أنا آدم، جاهز أساعدك في العربية."
            store.update { it.copy(caption = text, captionSpeaker = CaptionSpeaker.ASSISTANT) }
            voiceManager.speak(text)
        }
    }

    fun runManualCommand(command: String) {
        PilotLogger.i("MainViewModel", "Pilot manual command: $command")
        finishListening(command)
    }

    fun reconnectByd() {
        PilotLogger.i("MainViewModel", "Pilot: reconnect BYD requested")
        connectionManager.stop()
        connectionManager.start()
    }

    fun clearLogs() = PilotLogger.clear()

    // ─── Direct vehicle action chips (bypass assistant parser) ─────────────────

    private fun requireConnected(block: suspend () -> String) {
        if (_bydState.value != BydConnectionState.READY && _bydState.value != BydConnectionState.DEGRADED) {
            store.update { it.copy(vehicleMessage = "السيارة مش متصلة. جرّب Reconnect أولاً.") }
            return
        }
        viewModelScope.launch {
            val result = runCatching { block() }.getOrElse { "تعذر التنفيذ: ${it.message}" }
            store.update { it.copy(vehicleMessage = result, faceState = FaceState.HAPPY) }
        }
    }

    fun directClimate22() = requireConnected {
        vehicleController.setClimatePower(true)
        vehicleController.setClimateTemperature(22)
        "ضبطت التكييف على 22 درجة"
    }

    fun directToggleClimate() = requireConnected {
        climateEnabled = !climateEnabled
        vehicleController.setClimatePower(climateEnabled)
        if (climateEnabled) "شغلت التكييف" else "طفيت التكييف"
    }

    fun directWindowVent() = requireConnected {
        vehicleController.setWindowPosition("fl", 15)
        "فتحت شباك السائق للتهوية"
    }

    fun directLockDoors() = requireConnected {
        vehicleController.setDoorLocks(true)
        "قفلت الأبواب"
    }

    fun directUnlockDoors() = requireConnected {
        vehicleController.setDoorLocks(false)
        "فتحت قفل الأبواب"
    }

    fun directReadSoc() = requireConnected {
        val soc = vehicleController.getSocPercent()
        if (soc != null) "نسبة شحن البطارية $soc٪" else "مش قادر أقرأ نسبة الشحن"
    }

    fun directAdbShellTest() {
        viewModelScope.launch {
            val output = connectionManager.testShell("echo AiFriend_ADB_OK")
            val message = output?.trim() ?: "ADB shell test failed or not connected"
            PilotLogger.i("MainViewModel", "ADB shell test output: $message")
            store.update { it.copy(vehicleMessage = message) }
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
            it.copy(isSpeaking = false, faceState = FaceState.IDLE)
        }
    }

    fun stopAll() {
        when {
            state.value.isListening -> stopListening()
            state.value.isSpeaking -> stopSpeaking()
            else -> resetToIdle()
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
