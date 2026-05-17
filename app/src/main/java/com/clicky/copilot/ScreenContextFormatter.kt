package com.clicky.copilot

import com.clicky.accessibility.AccessibilityNodeSnapshot

object ScreenContextFormatter {

    fun formatNodeTree(nodes: List<AccessibilityNodeSnapshot>): String {
        val visibleNodes = nodes.filter { it.isVisibleToUser && it.hasMeaningfulContent() }
            .take(200)

        val builder = StringBuilder()
        builder.appendLine("Screen UI Elements:")

        for ((index, node) in visibleNodes.withIndex()) {
            val label = node.text ?: node.contentDescription ?: ""
            if (label.isBlank()) continue

            val bounds = "[${node.boundsInScreen.left},${node.boundsInScreen.top}][${node.boundsInScreen.right},${node.boundsInScreen.bottom}]"
            val clickable = if (node.isClickable) " [CLICKABLE]" else ""
            val checkable = if (node.isCheckable) " [CHECKABLE]" else ""

            builder.appendLine("${index + 1}. \"$label\"$clickable$checkable $bounds")
        }

        return builder.toString()
    }

    fun formatForGemini(nodes: List<AccessibilityNodeSnapshot>): String {
        return formatNodeTree(nodes)
    }
}
