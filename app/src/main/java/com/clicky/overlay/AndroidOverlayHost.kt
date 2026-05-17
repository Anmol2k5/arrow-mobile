package com.clicky.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams

class AndroidOverlayHost(private val context: Context) {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val overlayViews = mutableMapOf<View, LayoutParams>()

    fun addOverlayView(
        view: View,
        width: Int = LayoutParams.MATCH_PARENT,
        height: Int = LayoutParams.MATCH_PARENT,
        flags: Int = LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        gravity: Int = Gravity.TOP or Gravity.START
    ) {
        if (overlayViews.containsKey(view)) return

        val params = LayoutParams().apply {
            this.width = width
            this.height = height
            this.gravity = gravity
            this.format = PixelFormat.TRANSLUCENT
            this.flags = flags
            this.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                LayoutParams.TYPE_PHONE
            }
        }

        try {
            windowManager.addView(view, params)
            overlayViews[view] = params
        } catch (e: WindowManager.BadTokenException) {
            e.printStackTrace()
        }
    }

    fun removeOverlayView(view: View) {
        try {
            windowManager.removeView(view)
            overlayViews.remove(view)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    fun removeAllOverlays() {
        overlayViews.keys.toList().forEach { removeOverlayView(it) }
    }

    fun updateViewLayout(view: View, params: LayoutParams) {
        if (overlayViews.containsKey(view)) {
            windowManager.updateViewLayout(view, params)
            overlayViews[view] = params
        }
    }
}
