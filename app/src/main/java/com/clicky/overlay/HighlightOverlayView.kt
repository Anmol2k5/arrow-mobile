package com.clicky.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class HighlightOverlayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#4285F4")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4285F4")
        alpha = 30
    }

    private val targetRects = mutableListOf<Rect>()
    private var currentAlpha = 255
    private var animator: ValueAnimator? = null

    init {
        startPulseAnimation()
    }

    private fun startPulseAnimation() {
        animator = ValueAnimator.ofInt(80, 255).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                currentAlpha = animation.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    fun setHighlightRects(rects: List<Rect>) {
        targetRects.clear()
        targetRects.addAll(rects)
        invalidate()
    }

    fun clearHighlights() {
        targetRects.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.alpha = currentAlpha
        fillPaint.alpha = (currentAlpha * 0.15f).toInt()

        for (rect in targetRects) {
            val rectF = RectF(rect)
            rectF.inset(-3f, -3f)
            canvas.drawRoundRect(rectF, 12f, 12f, fillPaint)
            canvas.drawRoundRect(rectF, 12f, 12f, paint)
        }
    }

    fun cleanup() {
        animator?.cancel()
        animator = null
    }
}
