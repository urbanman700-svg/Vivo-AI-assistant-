package com.codexkd.vivoassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.codexkd.vivoassistant.utils.Constants
import java.util.Locale

/**
 * VoiceEngine — Handles all Speech-to-Text functionality.
 *
 * Features:
 * - Google Speech Recognition (cloud ASR)
 * - Hindi + English + Hinglish support
 * - Voice Activity Detection (auto-stop on silence)
 * - Session-based listening (not always-on)
 * - Error recovery and graceful degradation
 * - Wake word detection ("Vivo")
 */
class VoiceEngine(
    private val context: Context,
    private val listener: VoiceListener
) {

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var currentLanguage = Constants.LANG_ENGLISH
    private var silenceTimeoutMs = Constants.VAD_SILENCE_TIMEOUT
    private var restartOnError = false

    interface VoiceListener {
        fun onListeningStarted()
        fun onPartialResult(text: String)
        fun onResult(text: String)
        fun onError(error: String)
        fun onListeningStopped()
        fun onVolumeChanged(rmsdB: Float)
    }

    // ═══════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════

    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("Speech recognition not available on this device.")
            return
        }
        createRecognizer()
        Log.d(TAG, "VoiceEngine initialized")
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }
    }

    fun destroy() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
        mainHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "VoiceEngine destroyed")
    }

    // ═══════════════════════════════════════════════
    // LISTENING CONTROL
    // ═══════════════════════════════════════════════

    fun startListening(language: String = currentLanguage, restart: Boolean = false) {
        if (isListening) {
            Log.d(TAG, "Already listening — stop first")
            stopListening()
            mainHandler.postDelayed({ startListening(language, restart) }, 300)
            return
        }

        currentLanguage = language
        restartOnError = restart

        val intent = buildRecognizerIntent(language)

        mainHandler.post {
            try {
                if (recognizer == null) createRecognizer()
                recognizer?.startListening(intent)
                Log.d(TAG, "Started listening [$language]")
            } catch (e: Exception) {
                Log.e(TAG, "startListening error: ${e.message}")
                listener.onError("Could not start voice recognition. Try again.")
            }
        }
    }

    fun stopListening() {
        if (!isListening) return
        mainHandler.post {
            try {
                recognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "stopListening error: ${e.message}")
            }
        }
    }

    fun cancelListening() {
        mainHandler.post {
            try {
                recognizer?.cancel()
                isListening = false
            } catch (e: Exception) {
                Log.e(TAG, "cancelListening error: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════
    // RECOGNIZER INTENT BUILDER
    // ═══════════════════════════════════════════════

    private fun buildRecognizerIntent(language: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            // Primary language
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)

            // Also recognize English and Hindi alternately
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)

            // Additional languages (Hinglish support)
            val extraLangs = ArrayList<String>().apply {
                add(Constants.LANG_ENGLISH)
                add(Constants.LANG_HINDI)
                add(Locale.ENGLISH.toString())
            }
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, extraLangs)

            // Partial results for real-time feedback
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            // Max silence before stopping
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                silenceTimeoutMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                silenceTimeoutMs - 500)

            // Prompt (shown on Google recognition dialog if visible)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")

            // Number of results
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    // ═══════════════════════════════════════════════
    // RECOGNITION LISTENER
    // ═══════════════════════════════════════════════

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            Log.d(TAG, "Ready for speech")
            listener.onListeningStarted()
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech began")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Volume level — used to animate the waveform
            listener.onVolumeChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Raw audio buffer — not used
        }

        override fun onEndOfSpeech() {
            isListening = false
            Log.d(TAG, "Speech ended")
            listener.onListeningStopped()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?: return
            Log.d(TAG, "Partial: $partial")
            listener.onPartialResult(partial)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (matches.isNullOrEmpty()) {
                listener.onError("Could not understand. Please try again.")
                return
            }

            // Take best result
            val bestResult = matches[0].trim()
            Log.d(TAG, "Final result: $bestResult")

            if (bestResult.isBlank()) {
                listener.onError("Nothing heard. Please speak clearly.")
                return
            }

            listener.onResult(bestResult)
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMsg = getSpeechErrorMessage(error)
            Log.e(TAG, "Recognition error $error: $errorMsg")

            // Auto-restart for recoverable errors if in continuous mode
            if (restartOnError && isRecoverableError(error)) {
                mainHandler.postDelayed({
                    startListening(currentLanguage, true)
                }, 1000)
            } else {
                listener.onError(errorMsg)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Additional events — not commonly needed
        }
    }

    // ═══════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════

    private fun getSpeechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO                -> "Microphone error. Check permissions."
        SpeechRecognizer.ERROR_CLIENT               -> "Client error. Restart app."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
        SpeechRecognizer.ERROR_NETWORK              -> "Network error. Check internet."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT      -> "Network timeout. Check connection."
        SpeechRecognizer.ERROR_NO_MATCH             -> "Could not understand. Speak clearly."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY      -> "Speech engine busy. Wait a moment."
        SpeechRecognizer.ERROR_SERVER               -> "Google speech server error. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT       -> "No speech detected. Try again."
        else                                        -> "Unknown error ($error)."
    }

    private fun isRecoverableError(error: Int): Boolean = error in listOf(
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT
    )

    fun isCurrentlyListening() = isListening

    fun setLanguage(language: String) {
        currentLanguage = language
    }

    fun setSilenceTimeout(ms: Long) {
        silenceTimeoutMs = ms
    }

    companion object {
        private const val TAG = "VoiceEngine"
    }
}
