package dev.livepipe

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Floating control bubble. Shows a custom icon or the elapsed stream timer. Drag it anywhere; on release it
 * snaps to the nearest screen edge. With edge-hide on, an idle bubble tucks behind the edge leaving a slim
 * handle; tapping the handle pops it out. Tapping the bubble opens the control panel: mute mic, hide screen,
 * record, end stream, open app. Runs in preview mode (before going live) so it can be placed, and live mode
 * during a stream. The OS capture indicator is separate and always visible.
 */
class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var icon: ImageView
    private lateinit var timer: TextView
    private lateinit var lp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var startElapsed = 0L
    private var showsTimer = false
    private var edgeHide = true
    private var tucked = false
    private var anim: ValueAnimator? = null
    private val size get() = dp(56)
    private val handleVisible get() = dp(14)     // how much of a tucked bubble stays on screen

    // Control panel (second overlay window next to the bubble).
    private var panel: LinearLayout? = null
    private lateinit var status: TextView
    private lateinit var micBtn: ImageView
    private lateinit var screenBtn: ImageView
    private lateinit var recBtn: ImageView
    private lateinit var endBtn: ImageView
    private var endArmed = false
    private var panelClosedAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            timer.text = clock()
            handler.postDelayed(this, 1000)
        }
    }
    private val panelTick = object : Runnable {
        override fun run() { refreshPanel(); handler.postDelayed(this, 1000) }
    }
    private val tuck = Runnable { if (edgeHide) tuckAway() }
    private val closePanel = Runnable { hidePanel() }
    private val disarmEnd = Runnable { endArmed = false; refreshPanel() }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.CENTER_CROP
            // The launcher art sits on a dark tile inside adaptive-icon padding; zoom so the tile fills the circle.
            scaleX = DEFAULT_ICON_ZOOM; scaleY = DEFAULT_ICON_ZOOM
        }
        timer = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 12f; visibility = View.GONE
            gravity = Gravity.CENTER; text = "0:00:00"
        }
        root = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF111111.toInt()) }
            foreground = GradientDrawable().apply {                 // ember ring drawn over the image
                shape = GradientDrawable.OVAL
                setStroke(dp(2), ACCENT)
            }
            clipToOutline = true
            addView(icon, FrameLayout.LayoutParams(size, size))
            addView(timer, FrameLayout.LayoutParams(size, size))
        }
        lp = WindowManager.LayoutParams(size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        val p = Store(this).prefsBundle
        val (w, h) = screen()
        lp.x = (p.bubbleX * (w - size)).toInt(); lp.y = (p.bubbleY * (h - size)).toInt()
        root.setOnTouchListener(dragAndTap())
        // Parked on an edge the bubble sits in the back-gesture zone; claim its bounds so a grab drags it.
        if (Build.VERSION.SDK_INT >= 29) root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, v.width, v.height))
        }
        wm.addView(root, lp)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startElapsed = intent?.getLongExtra(EXTRA_START, 0L) ?: 0L        // 0 = preview, no stream yet
        showsTimer = intent?.getBooleanExtra(EXTRA_TIMER, false) ?: false
        edgeHide = intent?.getBooleanExtra(EXTRA_EDGE_HIDE, true) ?: true
        val transparency = intent?.getFloatExtra(EXTRA_ALPHA, 0.2f) ?: 0.2f
        // Clamp so the bubble can go very faint but never fully gone.
        root.alpha = (1f - transparency.coerceIn(0f, Prefs.BUBBLE_MAX_TRANSPARENCY)).coerceAtLeast(0.05f)
        intent?.getStringExtra(EXTRA_IMAGE)?.takeIf { it.isNotBlank() }?.let { uri ->
            runCatching {
                contentResolver.openInputStream(Uri.parse(uri))?.use { icon.setImageBitmap(android.graphics.BitmapFactory.decodeStream(it)) }
                icon.scaleX = 1f; icon.scaleY = 1f                  // user crops are already a full circle
            }
        }
        render()
        snap(animate = false)
        return START_STICKY
    }

    private fun render() {
        handler.removeCallbacks(tick)
        if (showsTimer) {
            icon.visibility = View.GONE; timer.visibility = View.VISIBLE
            handler.post(tick)
        } else {
            timer.visibility = View.GONE; icon.visibility = View.VISIBLE
        }
    }

    private fun clock(): String {
        val s = if (startElapsed == 0L) 0 else (SystemClock.elapsedRealtime() - startElapsed) / 1000
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Snaps to the nearest of the four edges, keeping the other coordinate. Saves the spot for next time. */
    private fun snap(animate: Boolean) {
        val (w, h) = screen()
        val x = lp.x.coerceIn(0, w - size); val y = lp.y.coerceIn(0, h - size)
        val toLeft = x; val toRight = w - size - x; val toTop = y; val toBottom = h - size - y
        val (tx, ty) = when (minOf(toLeft, toRight, toTop, toBottom)) {
            toLeft -> 0 to y
            toRight -> w - size to y
            toTop -> x to 0
            else -> x to h - size
        }
        moveTo(tx, ty, animate)
        tucked = false
        val store = Store(this)
        store.prefsBundle = store.prefsBundle.copy(bubbleX = tx / (w - size).toFloat(), bubbleY = ty / (h - size).toFloat())
        scheduleTuck()
    }

    private fun scheduleTuck() { handler.removeCallbacks(tuck); if (panel == null) handler.postDelayed(tuck, IDLE_MS) }

    /** Slides the bubble behind its edge, leaving a slim handle on screen. */
    private fun tuckAway() {
        val (w, h) = screen()
        val hide = size - handleVisible
        val (tx, ty) = when {
            lp.x <= 0 -> -hide to lp.y
            lp.x >= w - size -> w - size + hide to lp.y
            lp.y <= 0 -> lp.x to -hide
            else -> lp.x to h - size + hide
        }
        tucked = true
        moveTo(tx, ty, animate = true)
    }

    private fun moveTo(tx: Int, ty: Int, animate: Boolean) {
        anim?.cancel()
        if (!animate) { lp.x = tx; lp.y = ty; runCatching { wm.updateViewLayout(root, lp) }; return }
        val fx = lp.x; val fy = lp.y
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 150                                  // quick and decisive, no bounce
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                val f = it.animatedValue as Float
                lp.x = (fx + (tx - fx) * f).toInt(); lp.y = (fy + (ty - fy) * f).toInt()
                runCatching { wm.updateViewLayout(root, lp) }
            }
            start()
        }
    }

    private fun dragAndTap(): View.OnTouchListener {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        return View.OnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    anim?.cancel(); handler.removeCallbacks(tuck)
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (!moved && (abs(dx) > dp(6) || abs(dy) > dp(6))) { moved = true; hidePanel() }
                    if (moved) { lp.x = startX + dx; lp.y = startY + dy; wm.updateViewLayout(root, lp) }   // 1:1 with the finger
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> when {
                    moved || tucked -> snap(animate = true)        // dropped, or tapped the handle: pop back out
                    panel != null -> hidePanel()
                    // The outside-touch that just closed the panel is this same tap; don't reopen it.
                    SystemClock.uptimeMillis() - panelClosedAt > 300 -> showPanel()
                }
            }
            true
        }
    }

    /* ---------- control panel ---------- */

    @Suppress("ClickableViewAccessibility")
    private fun showPanel() {
        handler.removeCallbacks(tuck)
        status = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 13f; setPadding(dp(4), 0, dp(4), dp(10))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(res: Int, desc: String, onTap: () -> Unit) = ImageView(this).apply {
            setImageResource(res); contentDescription = desc
            setPadding(dp(11), dp(11), dp(11), dp(11))
            setOnClickListener { onTap(); refreshPanel(); handler.removeCallbacks(closePanel); handler.postDelayed(closePanel, PANEL_MS) }
            row.addView(this, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(8) })
        }
        micBtn = button(R.drawable.ic_mic, "Mute microphone") { StreamService.toggleMic() }
        screenBtn = button(R.drawable.ic_eye_off, "Hide screen from viewers") { StreamService.toggleScreen() }
        recBtn = button(R.drawable.ic_record, "Record to phone") { StreamService.toggleRecord() }
        endBtn = button(R.drawable.ic_stop, "End stream") {
            if (StreamService.controls() == null) return@button
            if (endArmed) startService(Intent(this, StreamService::class.java).setAction(StreamService.ACTION_STOP))
            else { endArmed = true; handler.postDelayed(disarmEnd, 3000) }
        }
        button(R.drawable.ic_home, "Open Angra Streaming") {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            hidePanel()
        }.apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = 0 }

        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0xF2161618.toInt()) }
            addView(status); addView(row)
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE) hidePanel(); false }
        }
        panel = p
        refreshPanel()

        // Beside the bubble, towards the middle of the screen; above/below it when parked on the top/bottom edge.
        p.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val mw = p.measuredWidth; val mh = p.measuredHeight; val gap = dp(8)
        val (w, h) = screen()
        var mx = if (lp.x + size / 2 < w / 2) lp.x + size + gap else lp.x - mw - gap
        var my = lp.y + (size - mh) / 2
        if (mx < 0 || mx + mw > w) {
            mx = lp.x + size / 2 - mw / 2
            my = if (lp.y + size / 2 < h / 2) lp.y + size + gap else lp.y - mh - gap
        }
        val plp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = mx.coerceIn(gap, maxOf(gap, w - mw - gap)); y = my.coerceIn(gap, maxOf(gap, h - mh - gap))
        }
        runCatching { wm.addView(p, plp) }
        handler.post(panelTick)
        handler.postDelayed(closePanel, PANEL_MS)
    }

    private fun hidePanel() {
        val p = panel ?: return
        panel = null; endArmed = false; panelClosedAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(panelTick); handler.removeCallbacks(closePanel); handler.removeCallbacks(disarmEnd)
        runCatching { wm.removeView(p) }
        scheduleTuck()
    }

    /** Live state into the panel; in preview the controls are shown but dimmed. */
    private fun refreshPanel() {
        if (panel == null) return
        val c = StreamService.controls()
        status.text = when {
            c == null -> "Preview · controls work once you're live"
            endArmed -> "Tap ■ again to end the stream"
            else -> "● LIVE  ${clock()} · %.1f Mbps".format(StreamService.lastBitrate.value / 1_000_000f)
        }
        fun paint(v: ImageView, on: Boolean, enabled: Boolean) {
            v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on) RED else 0x33FFFFFF) }
            v.alpha = if (enabled) 1f else 0.35f
        }
        val live = c != null
        micBtn.setImageResource(if (c?.micMuted == true) R.drawable.ic_mic_off else R.drawable.ic_mic)
        micBtn.contentDescription = if (c?.micMuted == true) "Unmute microphone" else "Mute microphone"
        paint(micBtn, c?.micMuted == true, live)
        paint(screenBtn, c?.screenHidden == true, live)
        paint(recBtn, c?.recording == true, live)
        paint(endBtn, endArmed, live)
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun screen(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }.let { it.x to it.y }

    override fun onDestroy() {
        hidePanel()
        handler.removeCallbacksAndMessages(null); anim?.cancel()
        runCatching { wm.removeView(root) }
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_START = "start"
        const val EXTRA_TIMER = "timer"
        const val EXTRA_ALPHA = "alpha"
        const val EXTRA_IMAGE = "image"
        const val EXTRA_EDGE_HIDE = "edgeHide"
        private const val IDLE_MS = 2500L
        private const val PANEL_MS = 6000L               // panel closes itself after this long untouched
        private const val DEFAULT_ICON_ZOOM = 1.75f
        private const val ACCENT = 0xFFF2542D.toInt()
        private const val RED = 0xFFFF3B30.toInt()

        @Volatile var previewing = false
            private set

        private fun intent(ctx: Context, p: Prefs, startElapsed: Long) = Intent(ctx, BubbleService::class.java)
            .putExtra(EXTRA_START, startElapsed)
            .putExtra(EXTRA_TIMER, p.bubbleShowsTimer)
            .putExtra(EXTRA_ALPHA, p.bubbleTransparency)
            .putExtra(EXTRA_IMAGE, p.bubbleImageUri)
            .putExtra(EXTRA_EDGE_HIDE, p.bubbleEdgeHide)

        fun start(ctx: Context, p: Prefs, startElapsed: Long) { previewing = false; ctx.startService(intent(ctx, p, startElapsed)) }

        /** Shows the bubble without streaming so it can be placed (and checked) before going live. */
        fun preview(ctx: Context, p: Prefs) { previewing = true; ctx.startService(intent(ctx, p, 0L)) }

        fun stop(ctx: Context) { previewing = false; ctx.stopService(Intent(ctx, BubbleService::class.java)) }
    }
}
