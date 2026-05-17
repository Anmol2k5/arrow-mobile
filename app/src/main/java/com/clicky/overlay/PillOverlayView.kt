package com.clicky.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class PillOverlayView(context: Context) : LinearLayout(context) {

    private val textView: TextView
    private var cardView: CardView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER

        cardView = CardView(context).apply {
            radius = 24f
            cardElevation = 8f
            setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        textView = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 16f
            setPadding(32, 16, 32, 16)
            text = "Clicky Copilot"
        }

        cardView.addView(textView)
        addView(cardView)
    }

    fun showInstruction(text: String) {
        textView.text = text
        visibility = View.VISIBLE
    }

    fun hide() {
        visibility = View.GONE
    }

    fun showThinking() {
        textView.text = "Thinking..."
        visibility = View.VISIBLE
    }
}
