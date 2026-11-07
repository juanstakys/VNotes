package app.vnotes

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Non-modal snackbar, placed immediately above the composer (and therefore above the IME). */
class UndoSnackbar(context: Context) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var undoAction: (() -> Unit)? = null
    private val timeout = Runnable { dismiss() }
    private val message = TextView(context).apply { setTextColor(Color.WHITE); textSize = 14f }
    private val undo = Button(context).apply {
        text = "UNDO"; isAllCaps = true; textSize = 14f
        setTextColor(Color.rgb(185, 239, 193)); background = null
        minHeight = dp(48); minimumWidth = dp(72)
        setOnClickListener { val action = undoAction; dismiss(); action?.invoke() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(4), dp(4), dp(4))
        background = GradientDrawable().apply { setColor(Color.rgb(37,49,40)); cornerRadius = dp(12).toFloat() }
        elevation = dp(3).toFloat()
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        addView(message, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(undo, LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)))
        visibility = GONE
    }
    fun show(text: String, action: () -> Unit) {
        handler.removeCallbacks(timeout); undoAction = action; message.text = text; visibility = VISIBLE
        val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val delay = if (Build.VERSION.SDK_INT >= 29) accessibility.getRecommendedTimeoutMillis(
            6000, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS
        ).toLong() else if (accessibility.isTouchExplorationEnabled) 15000L else 6000L
        handler.postDelayed(timeout, delay)
    }
    fun dismiss() { handler.removeCallbacks(timeout); undoAction = null; visibility = GONE }
    override fun onDetachedFromWindow() { dismiss(); super.onDetachedFromWindow() }
}
