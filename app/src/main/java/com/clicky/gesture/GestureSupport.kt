package com.clicky.gesture

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.os.Build
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo

class GestureSupport(private val service: AccessibilityService) {

    private var gestureInProgress = false

    fun performClick(x: Int, y: Int, duration: Long = 100): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            return service.dispatchGesture(
                android.view.accessibility.AccessibilityGestureEvent(
                    MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0),
                    path,
                    null,
                    0
                ),
                null,
                null
            )
        }
        return false
    }

    fun performLongClick(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
            return service.dispatchGesture(
                android.view.accessibility.AccessibilityGestureEvent(
                    downEvent,
                    path,
                    null,
                    AccessibilityService.GESTURE_LONG_CLICK
                ),
                null,
                null
            )
        }
        return false
    }

    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            return service.dispatchGesture(
                android.view.accessibility.AccessibilityGestureEvent(
                    MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, startX.toFloat(), startY.toFloat(), 0),
                    path,
                    null,
                    durationMs.toInt()
                ),
                null,
                null
            )
        }
        return false
    }

    fun swipeUp(distancePx: Int = 300, durationMs: Long = 300): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2
        val centerY = displayMetrics.heightPixels / 2
        val startY = centerY + distancePx / 2
        val endY = centerY - distancePx / 2
        return performSwipe(centerX, startY, centerX, endY, durationMs)
    }

    fun swipeDown(distancePx: Int = 300, durationMs: Long = 300): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2
        val centerY = displayMetrics.heightPixels / 2
        val startY = centerY - distancePx / 2
        val endY = centerY + distancePx / 2
        return performSwipe(centerX, startY, centerX, endY, durationMs)
    }

    fun swipeLeft(distancePx: Int = 300, durationMs: Long = 300): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2
        val centerY = displayMetrics.heightPixels / 2
        val startX = centerX + distancePx / 2
        val endX = centerX - distancePx / 2
        return performSwipe(startX, centerY, endX, centerY, durationMs)
    }

    fun swipeRight(distancePx: Int = 300, durationMs: Long = 300): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2
        val centerY = displayMetrics.heightPixels / 2
        val startX = centerX - distancePx / 2
        val endX = centerX + distancePx / 2
        return performSwipe(startX, centerY, endX, centerY, durationMs)
    }

    fun scrollDown(): Boolean {
        return swipeDown(400, 250)
    }

    fun scrollUp(): Boolean {
        return swipeUp(400, 250)
    }

    fun findAndClickElement(textContains: String, maxDepth: Int = 20): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        return findAndClickRecursive(rootNode, textContains, 0, maxDepth)
    }

    private fun findAndClickRecursive(
        node: AccessibilityNodeInfo,
        textContains: String,
        currentDepth: Int,
        maxDepth: Int
    ): Boolean {
        if (currentDepth > maxDepth) return false

        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        if ((text.isNotEmpty() && text.contains(textContains, ignoreCase = true)) ||
            (contentDesc.isNotEmpty() && contentDesc.contains(textContains, ignoreCase = true))
        ) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                return performClick(bounds.centerX(), bounds.centerY())
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickRecursive(child, textContains, currentDepth + 1, maxDepth)) {
                child.recycle()
                return true
            }
            child.recycle()
        }

        return false
    }

    fun isGestureInProgress(): Boolean = gestureInProgress
}