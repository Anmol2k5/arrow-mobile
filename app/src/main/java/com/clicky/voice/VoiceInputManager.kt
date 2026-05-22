package com.clicky.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

enum class VoiceInputState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

class VoiceInputManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow(VoiceInputState.IDLE)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private var currentLocale: Locale = Locale.getDefault()

    private val supportedLanguages = mapOf(
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

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun setLanguage(languageCode: String) {
        currentLocale = when (languageCode.lowercase()) {
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
    }

    fun getSupportedLanguages(): Map<String, Locale> = supportedLanguages

    fun startListening() {
        if (!isAvailable()) {
            _state.value = VoiceInputState.ERROR
            return
        }

        _state.value = VoiceInputState.LISTENING
        _transcript.value = ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.value = VoiceInputState.PROCESSING
                }

                override fun onError(error: Int) {
                    _state.value = VoiceInputState.ERROR
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        else -> "Recognition error"
                    }
                    _transcript.value = ""
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    _transcript.value = text
                    _state.value = VoiceInputState.IDLE
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    _transcript.value = partial?.firstOrNull() ?: ""
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = VoiceInputState.IDLE
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}