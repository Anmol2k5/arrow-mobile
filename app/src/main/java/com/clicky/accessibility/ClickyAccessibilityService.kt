package com.clicky.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ClickyAccessibilityService : AccessibilityService() {

    private val _nodeTreeFlow = MutableStateFlow<List<AccessibilityNodeSnapshot>>(emptyList())
    val nodeTreeFlow: StateFlow<List<AccessibilityNodeSnapshot>> = _nodeTreeFlow.asStateFlow()

    private val _windowChangeFlow = MutableStateFlow<String?>(null)
    val windowChangeFlow: StateFlow<String?> = _windowChangeFlow.asStateFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: "unknown"
            _windowChangeFlow.value = packageName
            parseCurrentWindow()
        }
    }

    override fun onInterrupt() {}

    fun parseCurrentWindow() {
        val root = rootInActiveWindow ?: return
        try {
            val snapshots = NodeSnapshotParser.parse(root)
            _nodeTreeFlow.value = snapshots
        } finally {
            root.recycle()
        }
    }

    fun getCurrentNodeTree(): List<AccessibilityNodeSnapshot> = _nodeTreeFlow.value
}
