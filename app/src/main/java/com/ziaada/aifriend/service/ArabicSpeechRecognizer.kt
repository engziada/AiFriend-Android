package com.ziaada.aifriend.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class ArabicSpeechRecognizer(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onListeningStarted()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onListeningStopped()
        fun onError(errorCode: Int)
    }

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListeningStarted()
            override fun onBeginningOfSpeech() = listener.onListeningStarted()
            override fun onEndOfSpeech() = listener.onListeningStopped()
            override fun onError(error: Int) = listener.onError(error)
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(listener::onFinal)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(listener::onPartial)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.startListening(intent)
    }

    fun stop() = recognizer.stopListening()

    fun release() = recognizer.destroy()
}
