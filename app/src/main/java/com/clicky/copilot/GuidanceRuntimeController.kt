package com.clicky.copilot

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.clicky.accessibility.ClickyAccessibilityService
import com.clicky.accessibility.NodeTreeRepository
import com.clicky.memory.GuidanceAuditRepository
import com.clicky.memory.ScreenMemoryRepository
import com.clicky.overlay.AndroidOverlayHost
import com.clicky.overlay.HighlightOverlayView
import com.clicky.overlay.PillOverlayView
import com.clicky.screenshot.ScreenshotProvider

class GuidanceRuntimeController(private val context: Context) {

    private val nodeTreeRepository = NodeTreeRepository()
    private val screenMemoryRepository = ScreenMemoryRepository()
    private val auditRepository = GuidanceAuditRepository()
    private val screenshotProvider = ScreenshotProvider(context)
    private val overlayHost = AndroidOverlayHost(context)

    private lateinit var highlightOverlay: HighlightOverlayView
    private lateinit var pillOverlay: PillOverlayView
    private lateinit var agentLoop: AgentLoop

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
            auditRepository = auditRepository
        )
        agentLoop.initialize()
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
    }
}
