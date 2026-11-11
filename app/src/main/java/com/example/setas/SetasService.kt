package com.example.setas

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Serviço de acessibilidade que desenha uma cruz de setas + botão OK por cima de
 * qualquer app. Tudo local, sem internet.
 */
class SetasService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences

    private var root: WakeLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var hidden = false
    private var shownAlpha = 0.85f

    private val hideTask = Runnable { hideNow() }

    // ---------------------------------------------------------------- ciclo de vida

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs.get(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        buildOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::prefs.isInitialized) buildOverlay()
    }

    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        removeOverlay()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        if (key == Prefs.X || key == Prefs.Y) return // só posição: não precisa refazer
        buildOverlay()
    }

    // ---------------------------------------------------------------- overlay

    private fun buildOverlay() {
        removeOverlay()
        if (!prefs.getBoolean(Prefs.ENABLED, true)) return

        val dm = resources.displayMetrics
        val d = dm.density
        val btn = (prefs.getInt(Prefs.SIZE, 60) * d).toInt()
        val gap = (4 * d).toInt()
        val handleH = (28 * d).toInt()
        shownAlpha = prefs.getInt(Prefs.OPACITY, 85) / 100f

        val w = 3 * (btn + 2 * gap)
        val h = handleH + 2 * gap + 3 * (btn + 2 * gap)
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val px = prefs.getInt(Prefs.X, sw - w - (12 * d).toInt()).coerceIn(0, maxOf(0, sw - w))
        val py = prefs.getInt(Prefs.Y, sh - h - (96 * d).toInt()).coerceIn(0, maxOf(0, sh - h))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = px
            this.y = py
        }

        val layout = WakeLayout(
            this,
            isHidden = { hidden },
            onWake = { wake() },
            onActivity = { scheduleHide() }
        )

        // alça para arrastar
        val handle = TextView(this).apply {
            text = "≡  mover"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            background = shape(0xCC263238.toInt(), radius = 12 * d)
            layoutParams = LinearLayout.LayoutParams(w - 2 * gap, handleH).apply {
                setMargins(gap, gap, gap, gap)
            }
        }
        handle.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f
            var sy = 0f
            var ox = 0
            var oy = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX; sy = e.rawY; ox = params.x; oy = params.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (ox + e.rawX - sx).toInt().coerceIn(0, maxOf(0, sw - w))
                        params.y = (oy + e.rawY - sy).toInt().coerceIn(0, maxOf(0, sh - h))
                        wm.updateViewLayout(layout, params)
                    }
                    MotionEvent.ACTION_UP ->
                        prefs.edit().putInt(Prefs.X, params.x).putInt(Prefs.Y, params.y).apply()
                }
                return true
            }
        })

        val ok = circle("OK", btn, gap, 0xCC2E7D32.toInt(), 20f).apply {
            setOnClickListener { pressOk() }
        }

        fun spacer() = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(btn, btn).apply { setMargins(gap, gap, gap, gap) }
        }

        fun row(a: View, b: View, c: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(a); addView(b); addView(c)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(handle)
            addView(row(spacer(), arrow("▲", btn, gap, 0, -1), spacer()))
            addView(row(arrow("◄", btn, gap, -1, 0), ok, arrow("►", btn, gap, 1, 0)))
            addView(row(spacer(), arrow("▼", btn, gap, 0, 1), spacer()))
        }
        layout.addView(column)
        layout.alpha = shownAlpha

        hidden = false
        lp = params
        root = layout
        wm.addView(layout, params)
        scheduleHide()
    }

    private fun removeOverlay() {
        handler.removeCallbacksAndMessages(null)
        root?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // já removida
            }
        }
        root = null
        lp = null
    }

    // ---------------------------------------------------------------- sumir / aparecer

    private fun scheduleHide() {
        handler.removeCallbacks(hideTask)
        val secs = prefs.getInt(Prefs.TIMEOUT, 4)
        handler.postDelayed(hideTask, secs * 1000L)
    }

    private fun hideNow() {
        hidden = true
        root?.animate()?.alpha(0f)?.setDuration(300)?.start()
    }

    private fun wake() {
        hidden = false
        root?.animate()?.alpha(shownAlpha)?.setDuration(150)?.start()
        scheduleHide()
    }

    // ---------------------------------------------------------------- botões

    private fun shape(color: Int, radius: Float = 0f, oval: Boolean = false) = GradientDrawable().apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = radius
        setColor(color)
    }

    private fun circle(label: String, size: Int, margin: Int, color: Int, sp: Float) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = sp
        background = shape(color, oval = true)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            setMargins(margin, margin, margin, margin)
        }
    }

    /** Seta: toque rola uma vez; segurar repete. */
    private fun arrow(label: String, size: Int, margin: Int, dx: Int, dy: Int): TextView {
        val v = circle(label, size, margin, 0xCC263238.toInt(), 24f)
        val repeat = object : Runnable {
            override fun run() {
                scroll(dx, dy)
                handler.postDelayed(this, 450)
            }
        }
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.6f
                    scroll(dx, dy)
                    handler.postDelayed(repeat, 650)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
        return v
    }

    // ---------------------------------------------------------------- ações

    /**
     * dy > 0 = rolar para baixo (dedo sobe); dy < 0 = rolar para cima.
     * dx > 0 = rolar para a direita (dedo vai para a esquerda); dx < 0 = esquerda.
     * O deslize é feito do lado oposto ao dos botões, para nunca cair em cima deles.
     */
    private fun scroll(dx: Int, dy: Int) {
        scheduleHide()
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val cx = (lp?.x ?: 0) + (root?.width ?: 0) / 2f
        val cy = (lp?.y ?: 0) + (root?.height ?: 0) / 2f

        val path = Path()
        if (dy != 0) {
            val x = if (cx > w / 2) w * 0.25f else w * 0.75f
            val from = if (dy > 0) h * 0.65f else h * 0.35f
            val to = if (dy > 0) h * 0.35f else h * 0.65f
            path.moveTo(x, from)
            path.lineTo(x, to)
        } else {
            val y = if (cy > h / 2) h * 0.30f else h * 0.70f
            val from = if (dx > 0) w * 0.75f else w * 0.25f
            val to = if (dx > 0) w * 0.25f else w * 0.75f
            path.moveTo(from, y)
            path.lineTo(to, y)
        }
        gesture(path, 300)
    }

    /** OK = Enter: clica no item em foco; se não houver, toca no centro da tela. */
    private fun pressOk() {
        scheduleHide()
        val window = rootInActiveWindow
        var node: AccessibilityNodeInfo? =
            window?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: window?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            node = node.parent
        }
        val dm = resources.displayMetrics
        val tap = Path().apply { moveTo(dm.widthPixels / 2f, dm.heightPixels / 2f) }
        gesture(tap, 50)
    }

    private fun gesture(path: Path, durationMs: Long) {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(g, null, null)
    }
}

/**
 * Container que, quando os botões estão invisíveis, "engole" o primeiro toque
 * apenas para acordá-los (assim um toque sem querer não aciona nada).
 */
private class WakeLayout(
    ctx: Context,
    private val isHidden: () -> Boolean,
    private val onWake: () -> Unit,
    private val onActivity: () -> Unit
) : FrameLayout(ctx) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isHidden()) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) onWake()
            return true
        }
        onActivity()
        return false
    }
}
