package com.ptchankue.speechrecognition

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Wraps Android's [SpeechRecognizer] and keeps it listening until [stop] is called.
 *
 * The platform recognizer stops after each utterance (or on silence), so we
 * restart it automatically after every result or recoverable error.
 *
 * Offline: on Android 12+ we use the dedicated on-device recognizer when the
 * device supports it. Otherwise we fall back to the default recognizer with
 * EXTRA_PREFER_OFFLINE, which works offline if the language pack is installed.
 */
class ContinuousSpeechRecognizer(
    private val context: Context,
    private val callback: Callback,
) {
    interface Callback {
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onStatus(status: String)
        fun onFatalError(message: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    val isOnDevice: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    val isAvailable: Boolean
        get() = isOnDevice || SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (active) return
        if (!isAvailable) {
            callback.onFatalError("Speech recognition is not available on this device")
            return
        }
        active = true
        if (recognizer == null) recognizer = createRecognizer()
        listen()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        callback.onStatus("Stopped")
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer {
        val r = if (isOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(listener)
        return r
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun listen() {
        if (!active) return
        recognizer?.startListening(recognizerIntent())
    }

    private fun restart(delayMs: Long = 0) {
        if (!active) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = callback.onStatus("Listening…")
        override fun onBeginningOfSpeech() = callback.onStatus("Hearing speech…")
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = callback.onStatus("Processing…")
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(callback::onPartialResult)
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            if (!text.isNullOrBlank()) callback.onFinalResult(text)
            callback.onPartialResult("")
            restart()
        }

        override fun onError(error: Int) {
            if (!active) return
            when (error) {
                // Silence or nothing understood: just keep listening.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart()

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    recognizer?.cancel()
                    restart(RETRY_DELAY_MS)
                }

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    active = false
                    callback.onFatalError("Microphone permission is required")
                }

                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                    active = false
                    callback.onFatalError(
                        "Offline language pack for ${Locale.getDefault().displayLanguage} " +
                            "is not installed. Download it in your device's speech settings."
                    )
                }

                else -> {
                    callback.onStatus("Recovering (error $error)…")
                    // Recreate the recognizer for client/server errors; it can get stuck.
                    recognizer?.destroy()
                    recognizer = createRecognizer()
                    restart(RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object {
        const val RETRY_DELAY_MS = 500L
        // SpeechRecognizer.ERROR_LANGUAGE_* constants (API 31) inlined to support minSdk 24.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
