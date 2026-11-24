package com.ptchankue.speechrecognition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SpeechUiState(
    val isListening: Boolean = false,
    val transcript: String = "",
    val partial: String = "",
    val status: String = "Tap the mic to start",
    val engine: String = "",
    val error: String? = null,
)

class SpeechViewModel(application: Application) : AndroidViewModel(application),
    ContinuousSpeechRecognizer.Callback {

    private val recognizer = ContinuousSpeechRecognizer(application, this)

    private val _state = MutableStateFlow(
        SpeechUiState(engine = if (recognizer.isOnDevice) "On-device (offline)" else "Prefer offline")
    )
    val state: StateFlow<SpeechUiState> = _state.asStateFlow()

    fun start() {
        _state.update { it.copy(isListening = true, error = null) }
        recognizer.start()
    }

    fun stop() {
        recognizer.stop()
        _state.update { it.copy(isListening = false, partial = "") }
    }

    fun toggle() = if (_state.value.isListening) stop() else start()

    fun clear() = _state.update { it.copy(transcript = "", partial = "") }

    fun onPermissionDenied() = onFatalError("Microphone permission is required")

    override fun onPartialResult(text: String) = _state.update { it.copy(partial = text) }

    override fun onFinalResult(text: String) = _state.update {
        val joined = if (it.transcript.isEmpty()) text else "${it.transcript} $text"
        it.copy(transcript = joined, partial = "")
    }

    override fun onStatus(status: String) = _state.update { it.copy(status = status) }

    override fun onFatalError(message: String) = _state.update {
        it.copy(isListening = false, partial = "", status = "Stopped", error = message)
    }

    override fun onCleared() {
        recognizer.destroy()
    }
}
