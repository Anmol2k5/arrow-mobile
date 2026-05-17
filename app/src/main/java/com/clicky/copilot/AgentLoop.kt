package com.clicky.copilot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.clicky.accessibility.NodeTreeRepository
import com.clicky.memory.GuidanceAuditEntry
import com.clicky.memory.GuidanceAuditRepository
import com.clicky.memory.ScreenMemoryEntry
import com.clicky.memory.ScreenMemoryRepository
import com.clicky.overlay.HighlightOverlayView
import com.clicky.overlay.PillOverlayView
import com.clicky.screenshot.ScreenshotProvider
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
    private val auditRepository: GuidanceAuditRepository
) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

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
        description = "Displays a concise text instruction to the user in the floating assistant pill.",
        parameters = listOf(
            com.google.ai.client.generativeai.type.Schema.str("text", "The short text instruction to show to the user.")
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
                        speakInstructionDeclaration
                    )
                )
            ),
            systemInstruction = content {
                text(
                    """
                    You are Clicky, a visual AI copilot for Android. Your job is to help users navigate their device by understanding screen content and providing clear guidance.

                    Rules:
                    - Always call get_screen_context first to understand what is on screen
                    - Provide concise, actionable instructions
                    - Use highlight_target to draw attention to specific UI elements
                    - Use speak_instruction to communicate with the user
                    - Coordinate format: [left,top][right,bottom] in pixels
                    - Be helpful but not verbose
                    """.trimIndent()
                )
            }
        )
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

            var maxIterations = 10
            while (maxIterations-- > 0 && _isRunning.value) {
                val toolCalls = response.functionCalls.toList()
                if (toolCalls.isEmpty()) {
                    val finalText = response.text ?: "No response from AI"
                    _status.value = finalText
                    pillOverlay.showInstruction(finalText)
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
                _status.value = text
                pillOverlay.showInstruction(text)
                mapOf("displayed" to true, "text" to text)
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

    fun stop() {
        _isRunning.value = false
        _status.value = "Stopped"
        highlightOverlay.clearHighlights()
        pillOverlay.hide()
    }
}
