package com.clicky.copilot

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.clicky.accessibility.ClickyAccessibilityService
import com.clicky.accessibility.NodeTreeRepository
import com.clicky.gesture.GestureSupport
import com.clicky.memory.GuidanceAuditRepository
import com.clicky.memory.ScreenMemoryRepository
import com.clicky.overlay.AndroidOverlayHost
import com.clicky.overlay.HighlightOverlayView
import com.clicky.overlay.PillOverlayView
import com.clicky.screenshot.ScreenshotProvider
import com.clicky.voice.TextToSpeechManager
import com.clicky.voice.VoiceInputManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GuidanceRuntimeController(private val context: Context) {

    private val nodeTreeRepository = NodeTreeRepository()
    private val screenMemoryRepository = ScreenMemoryRepository()
    private val auditRepository = GuidanceAuditRepository()
    private val screenshotProvider = ScreenshotProvider(context)
    private val overlayHost = AndroidOverlayHost(context)

    private val _status = MutableStateFlow("Uninitialized")
    val status: StateFlow<String> = _status.asStateFlow()

    private val controllerScope = CoroutineScope(Dispatchers.Main)

    private lateinit var highlightOverlay: HighlightOverlayView
    private lateinit var pillOverlay: PillOverlayView
    private lateinit var agentLoop: AgentLoop

    private lateinit var gestureSupport: GestureSupport
    private lateinit var ttsManager: TextToSpeechManager
    private lateinit var voiceInputManager: VoiceInputManager

    fun initializeOverlays() {
        highlightOverlay = HighlightOverlayView(context)
        pillOverlay = PillOverlayView(context)

        overlayHost.addOverlayView(highlightOverlay)
        overlayHost.addOverlayView(
            pillOverlay,
            width = android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            height = android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        )

        gestureSupport = GestureSupport(getAccessibilityService()!!)
        ttsManager = TextToSpeechManager(context)
        voiceInputManager = VoiceInputManager(context)
    }

    private fun getAccessibilityService(): AccessibilityService? {
        return ClickyAccessibilityService.instance
    }

    fun initializeAgent(apiKey: String) {
        agentLoop = AgentLoop(
            context = context,
            apiKey = apiKey,
            nodeTreeRepository = nodeTreeRepository,
            screenshotProvider = screenshotProvider,
            highlightOverlay = highlightOverlay,
            pillOverlay = pillOverlay,
            screenMemoryRepository = screenMemoryRepository,
            auditRepository = auditRepository,
            gestureSupport = if (::gestureSupport.isInitialized) gestureSupport else null,
            ttsManager = if (::ttsManager.isInitialized) ttsManager else null,
            voiceInputManager = if (::voiceInputManager.isInitialized) voiceInputManager else null
        )
        agentLoop.initialize()
        _status.value = "Idle"
        controllerScope.launch {
            agentLoop.status.collect { s -> _status.value = s }
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("${context.packageName}/com.clicky.accessibility.ClickyAccessibilityService")
    }

    fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun executeIntent(intent: String) {
        agentLoop.executeIntent(intent)
    }

    fun stop() {
        agentLoop.stop()
        overlayHost.removeAllOverlays()
    }

    fun cleanup() {
        stop()
        screenshotProvider.release()
        if (::ttsManager.isInitialized) ttsManager.destroy()
        if (::voiceInputManager.isInitialized) voiceInputManager.destroy()
    }

    fun setLanguage(languageCode: String) {
        if (::agentLoop.isInitialized) {
            agentLoop.setLanguage(languageCode)
        }
    }

    fun speak(text: String) {
        if (::ttsManager.isInitialized) {
            ttsManager.speak(text)
        }
    }

    fun stopSpeaking() {
        if (::ttsManager.isInitialized) {
            ttsManager.stop()
        }
    }

    fun startVoiceInput() {
        if (::voiceInputManager.isInitialized) {
            voiceInputManager.startListening()
        }
    }

    fun getVoiceInputState() = if (::voiceInputManager.isInitialized) voiceInputManager.state else null

    fun getTranscript() = if (::voiceInputManager.isInitialized) voiceInputManager.transcript else null

    fun stopVoiceInput() {
        if (::voiceInputManager.isInitialized) {
            voiceInputManager.stopListening()
        }
    }

    fun getSupportedLanguages(): Map<String, java.util.Locale> {
        return if (::ttsManager.isInitialized) {
            ttsManager.getSupportedLanguages()
        } else {
            emptyMap()
        }
    }

    fun isVoiceInputAvailable(): Boolean {
        return if (::voiceInputManager.isInitialized) {
            voiceInputManager.isAvailable()
        } else {
            false
        }
    }
}
