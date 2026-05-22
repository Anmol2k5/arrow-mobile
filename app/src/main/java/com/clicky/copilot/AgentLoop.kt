package com.clicky.copilot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.clicky.accessibility.NodeTreeRepository
import com.clicky.gesture.GestureSupport
import com.clicky.memory.GuidanceAuditEntry
import com.clicky.memory.GuidanceAuditRepository
import com.clicky.memory.ScreenMemoryEntry
import com.clicky.memory.ScreenMemoryRepository
import com.clicky.overlay.HighlightOverlayView
import com.clicky.overlay.PillOverlayView
import com.clicky.screenshot.ScreenshotProvider
import com.clicky.voice.TextToSpeechManager
import com.clicky.voice.VoiceInputManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionCallPart
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.defineFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AgentLoop(
    private val context: Context,
    private val apiKey: String,
    private val nodeTreeRepository: NodeTreeRepository,
    private val screenshotProvider: ScreenshotProvider,
    private val highlightOverlay: HighlightOverlayView,
    private val pillOverlay: PillOverlayView,
    private val screenMemoryRepository: ScreenMemoryRepository,
    private val auditRepository: GuidanceAuditRepository,
    private val gestureSupport: GestureSupport? = null,
    private val ttsManager: TextToSpeechManager? = null,
    private val voiceInputManager: VoiceInputManager? = null
) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private var currentLanguage = "English"

    private lateinit var generativeModel: GenerativeModel

    private val getScreenContextDeclaration = defineFunction(
        name = "get_screen_context",
        description = "Gets the current screen context as a text list of UI elements with bounds. Call this when you need to see what is on the screen.",
        parameters = emptyList()
    )

    private val highlightTargetDeclaration = defineFunction(
        name = "highlight_target",
        description = "Highlights a specific target on the screen with a pulsing rectangle.",
        parameters = listOf(
            com.google.ai.client.generativeai.type.Schema.str("label", "The label of the element to highlight"),
            com.google.ai.client.generativeai.type.Schema.str("bounds", "The bounding box of the element in '[left,top][right,bottom]' or normalized '[ymin,xmin,ymax,xmax]' format.")
        )
    )

    private val speakInstructionDeclaration = defineFunction(
        name = "speak_instruction",
        description = "Displays a text instruction to the user AND speaks it aloud using TTS. Use this for important guidance.",
        parameters = listOf(
            com.google.ai.client.generativeai.type.Schema.str("text", "The instruction text to show and speak to the user."),
            com.google.ai.client.generativeai.type.Schema.str("language", "Language code for TTS: 'English', 'Hindi', 'Tamil', 'Telugu', 'Marathi', 'Kannada', 'Bengali', 'Gujarati', 'Malayalam', 'Punjabi'. Defaults to 'English'.")
        )
    )

    private val clickElementDeclaration = defineFunction(
        name = "click_element",
        description = "Click on a UI element by its text or content description. Use this to tap buttons, list items, etc.",
        parameters = listOf(
            com.google.ai.client.generativeai.type.Schema.str("text", "The text or partial text of the element to click")
        )
    )

    private val swipeDeclaration = defineFunction(
        name = "swipe",
        description = "Swipe the screen in a direction. Use for scrolling or navigating between screens.",
        parameters = listOf(
            com.google.ai.client.generativeai.type.Schema.str("direction", "Direction: 'up', 'down', 'left', 'right'"),
            com.google.ai.client.generativeai.type.Schema.int("distance", "Distance in pixels (default 300)")
        )
    )

    private val scrollDeclaration = defineFunction(
        name = "scroll",
        description = "Scroll the screen content. Use when you need to reveal hidden elements.",
        parameters = listOf(
            com.google.ai.client.generativeai.type.Schema.str("direction", "Direction: 'up' or 'down'")
        )
    )

    private val typeTextDeclaration = defineFunction(
        name = "type_text",
        description = "Type text into the currently focused text field.",
        parameters = listOf(
            com.google.ai.client.generativeai.type.Schema.str("text", "The text to type"),
            com.google.ai.client.generativeai.type.Schema.str("language", "Language of the text for keyboard selection")
        )
    )

    fun initialize() {
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            tools = listOf(
                com.google.ai.client.generativeai.type.Tool(
                    functionDeclarations = listOf(
                        getScreenContextDeclaration,
                        highlightTargetDeclaration,
                        speakInstructionDeclaration,
                        clickElementDeclaration,
                        swipeDeclaration,
                        scrollDeclaration,
                        typeTextDeclaration
                    )
                )
            ),
            systemInstruction = content {
                text(getSystemPrompt())
            }
        )
    }

    private fun getSystemPrompt(): String {
        return """
You are Clicky, a patient visual AI assistant for Android designed to help elderly users and beginners navigate their phone.

Your personality:
- Be warm, patient, and encouraging
- Use simple language, avoid technical terms
- Always explain what you're going to do before doing it
- Break complex tasks into small, easy steps
- NEVER assume the user knows anything about smartphones

For UPI/Payment guidance:
- Always confirm amounts before tapping "Pay"
- Explain each step clearly: "Now I'm going to tap on [button name]"
- Ask user to tap highlighted element
- Wait for user confirmation between steps
- If user seems confused, offer simpler alternatives

For navigation:
- Use simple directions: "top", "bottom", "left", "right"
- Always highlight the exact element to tap with a pulsing rectangle
- Speak instructions aloud in the user's preferred language
- Confirm each step was completed before moving to next

Coordinate format: [left,top][right,bottom] in pixels.
For gestures, describe clearly what you want the user to do.

User interface languages: ${supportedLanguagesMessage()}

IMPORTANT: Always speak instructions aloud using speak_instruction so elderly users can hear what to do.
        """.trimIndent()
    }

    private fun supportedLanguagesMessage(): String {
        return "English, Hindi (हिंदी), Tamil (தமிழ்), Telugu (తెలుగు), Marathi (मराठी), Kannada (ಕನ್ನಡ), Bengali (বাংলা), Gujarati (ગુજરાતી), Malayalam (മലയാളം), Punjabi (ਪੰਜਾਬੀ)"
    }

    suspend fun executeIntent(userIntent: String) {
        _isRunning.value = true
        _status.value = "Starting..."
        pillOverlay.showThinking()

        try {
            val chat = generativeModel.startChat()

            screenMemoryRepository.addEntry(
                ScreenMemoryEntry(
                    packageName = "unknown",
                    nodeCount = nodeTreeRepository.nodeTree.value.size,
                    userIntent = userIntent
                )
            )

            val initialPrompt = "User wants to: $userIntent. First, call get_screen_context to see what is currently on screen."
            var response = chat.sendMessage(initialPrompt)

            var maxIterations = 15
            while (maxIterations-- > 0 && _isRunning.value) {
                val toolCalls = response.functionCalls.toList()
                if (toolCalls.isEmpty()) {
                    val finalText = response.text ?: "No response from AI"
                    _status.value = finalText
                    pillOverlay.showInstruction(finalText)
                    ttsManager?.speak(finalText)
                    break
                }

                val toolResults = mutableListOf<Pair<FunctionCallPart, Map<String, Any>>>()
                for (call in toolCalls) {
                    val result = executeToolCall(call)
                    toolResults.add(call to result)
                }

                val combinedContent = content("function") {
                    for ((call, result) in toolResults) {
                        part(
                            FunctionResponsePart(
                                name = call.name,
                                response = org.json.JSONObject(result)
                            )
                        )
                        auditRepository.addEntry(
                            GuidanceAuditEntry(
                                toolName = call.name,
                                parameters = call.args.mapValues { it.value.toString() },
                                result = result.toString()
                            )
                        )
                    }
                }

                response = chat.sendMessage(combinedContent)
            }
        } catch (e: Exception) {
            _status.value = "Error: ${e.message}"
            pillOverlay.showInstruction("Error: ${e.message}")
        } finally {
            _isRunning.value = false
        }
    }

    private suspend fun executeToolCall(call: FunctionCallPart): Map<String, Any> {
        return when (call.name) {
            "get_screen_context" -> {
                _status.value = "Reading screen..."
                val nodes = nodeTreeRepository.nodeTree.value
                val context = ScreenContextFormatter.formatForGemini(nodes)
                mapOf("screen_context" to context)
            }
            "highlight_target" -> {
                _status.value = "Highlighting target..."
                val label = call.args["label"]?.toString() ?: ""
                val bounds = call.args["bounds"]?.toString() ?: ""
                val rect = parseBounds(bounds)
                highlightOverlay.setHighlightRects(listOf(rect))
                mapOf("highlighted" to true, "label" to label)
            }
            "speak_instruction" -> {
                val text = call.args["text"]?.toString() ?: ""
                val language = call.args["language"]?.toString() ?: "English"
                _status.value = text
                pillOverlay.showInstruction(text)
                if (language != currentLanguage) {
                    ttsManager?.setLanguage(language)
                    currentLanguage = language
                }
                ttsManager?.speak(text)
                mapOf("displayed" to true, "spoken" to true, "text" to text, "language" to language)
            }
            "click_element" -> {
                val text = call.args["text"]?.toString() ?: ""
                _status.value = "Clicking $text..."
                val success = gestureSupport?.findAndClickElement(text) ?: false
                mapOf("clicked" to success, "element" to text)
            }
            "swipe" -> {
                val direction = call.args["direction"]?.toString() ?: "up"
                val distance = call.args["distance"]?.toString()?.toIntOrNull() ?: 300
                _status.value = "Swiping $direction..."
                val success = when (direction.lowercase()) {
                    "up" -> gestureSupport?.swipeUp(distance) ?: false
                    "down" -> gestureSupport?.swipeDown(distance) ?: false
                    "left" -> gestureSupport?.swipeLeft(distance) ?: false
                    "right" -> gestureSupport?.swipeRight(distance) ?: false
                    else -> false
                }
                mapOf("swiped" to success, "direction" to direction)
            }
            "scroll" -> {
                val direction = call.args["direction"]?.toString() ?: "down"
                _status.value = "Scrolling $direction..."
                val success = when (direction.lowercase()) {
                    "up" -> gestureSupport?.scrollUp() ?: false
                    "down" -> gestureSupport?.scrollDown() ?: false
                    else -> false
                }
                mapOf("scrolled" to success, "direction" to direction)
            }
            "type_text" -> {
                val text = call.args["text"]?.toString() ?: ""
                _status.value = "Typing: $text"
                mapOf("typed" to true, "text" to text)
            }
            else -> mapOf("error" to "Unknown tool: ${call.name}")
        }
    }

    private fun parseBounds(bounds: String): Rect {
        val normalizedRegex = Regex("""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""")
        val match = normalizedRegex.find(bounds)
        if (match != null) {
            val (left, top, right, bottom) = match.destructured
            return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        }

        val yoloRegex = Regex("""\[(\d+),(\d+),(\d+),(\d+)\]""")
        val yoloMatch = yoloRegex.find(bounds)
        if (yoloMatch != null) {
            val (ymin, xmin, ymax, xmax) = yoloMatch.destructured
            val displayMetrics = context.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            return Rect(
                (xmin.toInt() / 1000.0 * width).toInt(),
                (ymin.toInt() / 1000.0 * height).toInt(),
                (xmax.toInt() / 1000.0 * width).toInt(),
                (ymax.toInt() / 1000.0 * height).toInt()
            )
        }

        return Rect(0, 0, 100, 100)
    }

    fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
        ttsManager?.setLanguage(languageCode)
        voiceInputManager?.setLanguage(languageCode)
    }

    fun startVoiceInput() {
        voiceInputManager?.startListening()
    }

    fun stopVoiceInput() {
        voiceInputManager?.stopListening()
    }

    fun speak(text: String) {
        ttsManager?.speak(text)
    }

    fun stopSpeaking() {
        ttsManager?.stop()
    }

    fun stop() {
        _isRunning.value = false
        _status.value = "Stopped"
        highlightOverlay.clearHighlights()
        pillOverlay.hide()
        ttsManager?.stop()
        voiceInputManager?.stopListening()
    }
}
