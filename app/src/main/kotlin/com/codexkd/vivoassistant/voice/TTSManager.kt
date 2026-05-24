package com.codexkd.vivoassistant.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.codexkd.vivoassistant.utils.Constants
import java.util.Locale
import java.util.UUID

/**
 * TTSManager — Text-to-Speech output for Vivo Assistant.
 *
 * Features:
 * - Android native TTS (no API key needed)
 * - Hindi + English language support
 * - Adjustable speed and pitch
 * - Queue management (interrupt or queue)
 * - Callback support (done speaking)
 */
class TTSManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var currentSpeed = Constants.TTS_DEFAULT_SPEED
    private var currentPitch = Constants.TTS_DEFAULT_PITCH
    private var currentLanguage = Constants.LANG_ENGLISH

    // Callbacks
    var onSpeakStart: ((utteranceId: String) -> Unit)? = null
    var onSpeakDone: ((utteranceId: String) -> Unit)? = null
    var onSpeakError: ((utteranceId: String) -> Unit)? = null
    var onTTSReady: (() -> Unit)? = null

    // ═══════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                setupLanguage()
                setupProgressListener()
                Log.d(TAG, "TTS initialized successfully")
                onTTSReady?.invoke()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isReady = false
            }
        }
    }

    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
            Log.d(TAG, "TTS destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "TTS destroy error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════
    // SPEECH OUTPUT
    // ═══════════════════════════════════════════════

    /**
     * Speak text — interrupts any current speech.
     */
    fun speak(text: String, queueMode: Boolean = false): String {
        if (!isReady || tts == null) {
            Log.w(TAG, "TTS not ready — cannot speak")
            return ""
        }

        // Clean text for TTS (remove markdown, special chars)
        val cleanText = cleanForTTS(text)
        if (cleanText.isBlank()) return ""

        val utteranceId = "vivo_${UUID.randomUUID()}"
        val mode = if (queueMode) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.setSpeechRate(currentSpeed)
        tts?.setPitch(currentPitch)

        val result = tts?.speak(cleanText, mode, params, utteranceId)

        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS speak() returned ERROR for: $cleanText")
        } else {
            Log.d(TAG, "Speaking [$utteranceId]: $cleanText")
        }

        return utteranceId
    }

    /**
     * Speak a short notification/sound cue (non-blocking, queued)
     */
    fun speakCue(cue: String) {
        speak(cue, queueMode = true)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "TTS stop error: ${e.message}")
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    // ═══════════════════════════════════════════════
    // LANGUAGE & SETTINGS
    // ═══════════════════════════════════════════════

    fun setLanguage(languageTag: String) {
        currentLanguage = languageTag
        if (isReady) setupLanguage()
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(currentSpeed)
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(currentPitch)
    }

    private fun setupLanguage() {
        val locale = when (currentLanguage) {
            Constants.LANG_HINDI   -> Locale("hi", "IN")
            Constants.LANG_ENGLISH -> Locale("en", "IN")  // Indian English accent
            else                   -> Locale.ENGLISH
        }

        val result = tts?.setLanguage(locale)
        when (result) {
            TextToSpeech.LANG_MISSING_DATA -> {
                Log.w(TAG, "Language data missing for $locale, falling back to English")
                tts?.setLanguage(Locale.ENGLISH)
            }
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.w(TAG, "Language not supported: $locale, falling back to English")
                tts?.setLanguage(Locale.ENGLISH)
            }
            else -> Log.d(TAG, "Language set: $locale")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { onSpeakStart?.invoke(it) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { onSpeakDone?.invoke(it) }
            }

            override fun onError(utteranceId: String?) {
                utteranceId?.let { onSpeakError?.invoke(it) }
            }
        })
    }

    // ═══════════════════════════════════════════════
    // TEXT CLEANING
    // ═══════════════════════════════════════════════

    private fun cleanForTTS(text: String): String {
        return text
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")  // Remove **bold**
            .replace(Regex("\\*(.*?)\\*"), "$1")         // Remove *italic*
            .replace(Regex("`(.*?)`"), "$1")             // Remove `code`
            .replace(Regex("#{1,6} "), "")               // Remove ## headers
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1") // Links → text only
            .replace(Regex("[•●▸▹►]"), "")               // Remove bullets
            .replace(Regex("\\n{2,}"), ". ")             // Multiple newlines → pause
            .replace("\n", ", ")                         // Single newlines → comma pause
            .replace("  ", " ")                          // Double spaces
            .trim()
            .take(500)                                   // TTS max length per chunk
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
