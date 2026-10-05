package com.ziaada.aifriend.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class FaceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    HAPPY,
    SURPRISED,
}

data class AiFriendUiState(
    val faceState: FaceState = FaceState.IDLE,
    val transcript: String = "",
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isSpeaking: Boolean = false,
    val climateSetpointC: Int = 22,
    val vehicleMessage: String = "جاهز أسمعك",
)

class AiFriendStateStore {
    private val mutable = MutableStateFlow(AiFriendUiState())
    val state: StateFlow<AiFriendUiState> = mutable

    fun update(transform: (AiFriendUiState) -> AiFriendUiState) {
        mutable.value = transform(mutable.value)
    }
}
