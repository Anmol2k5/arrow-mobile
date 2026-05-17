package com.clicky.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class AccessibilityNodeSnapshot(
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val className: String?,
    val boundsInScreen: Rect,
    val isVisibleToUser: Boolean,
    val isClickable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val depth: Int
) {
    fun hasMeaningfulContent(): Boolean {
        return !text.isNullOrBlank() ||
                !contentDescription.isNullOrBlank() ||
                isClickable ||
                isCheckable
    }
}
