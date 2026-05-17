package com.clicky.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object NodeSnapshotParser {

    fun parse(root: AccessibilityNodeInfo): List<AccessibilityNodeSnapshot> {
        val snapshots = mutableListOf<AccessibilityNodeSnapshot>()
        traverse(root, snapshots, 0)
        return snapshots
    }

    private fun traverse(node: AccessibilityNodeInfo, snapshots: MutableList<AccessibilityNodeSnapshot>, depth: Int) {
        try {
            val snapshot = AccessibilityNodeSnapshot(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                viewIdResourceName = node.viewIdResourceName,
                className = node.className?.toString(),
                boundsInScreen = Rect().also { node.getBoundsInScreen(it) },
                isVisibleToUser = node.isVisibleToUser,
                isClickable = node.isClickable,
                isCheckable = node.isCheckable,
                isChecked = node.isChecked,
                depth = depth
            )
            snapshots.add(snapshot)

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    traverse(child, snapshots, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        } finally {
            // Do NOT recycle the root node here - caller owns it
        }
    }
}
