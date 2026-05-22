package com.clicky.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class TtsState {
    IDLE,
    SPEAKING,
    ERROR
}

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _state = MutableStateFlow(TtsState.IDLE)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var currentLocale: Locale = Locale.US
    private var isInitialized = false

    private val supportedVoices = mapOf(
        "English" to Locale.US,
        "हिंदी" to Locale("hi", "IN"),
        "தமிழ்" to Locale("ta", "IN"),
        "తెలుగు" to Locale("te", "IN"),
        "मराठी" to Locale("mr", "IN"),
        "ಕನ್ನಡ" to Locale("kn", "IN"),
        "বাংলা" to Locale("bn", "IN"),
        "ગુજરાતી" to Locale("gu", "IN"),
        "മലയാളം" to Locale("ml", "IN"),
        "ਪੰਜਾਬੀ" to Locale("pa", "IN")
    )

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = currentLocale
            isInitialized = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _state.value = TtsState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    _state.value = TtsState.IDLE
                }

                override fun onError(utteranceId: String?) {
                    _state.value = TtsState.ERROR
                }
            })
        } else {
            _state.value = TtsState.ERROR
            isInitialized = false
        }
    }

    fun setLanguage(languageCode: String): Boolean {
        val locale = when (languageCode.lowercase()) {
            "hi", "hindi" -> Locale("hi", "IN")
            "ta", "tamil" -> Locale("ta", "IN")
            "te", "telugu" -> Locale("te", "IN")
            "mr", "marathi" -> Locale("mr", "IN")
            "kn", "kannada" -> Locale("kn", "IN")
            "bn", "bengali" -> Locale("bn", "IN")
            "gu", "gujarati" -> Locale("gu", "IN")
            "ml", "malayalam" -> Locale("ml", "IN")
            "pa", "punjabi" -> Locale("pa", "IN")
            else -> Locale.US
        }

        currentLocale = locale
        val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return false
        }

        tts?.setSpeechRate(0.85f)
        return true
    }

    fun getSupportedLanguages(): Map<String, Locale> = supportedVoices

    fun speak(text: String, flush: Boolean = true) {
        if (!isInitialized) return

        val utteranceId = UUID.randomUUID().toString()
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

        tts?.speak(text, queueMode, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _state.value = TtsState.IDLE
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}