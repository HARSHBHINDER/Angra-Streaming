package dev.livepipe

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Floating control bubble. Shows a custom icon or the elapsed stream timer. Drag it anywhere; on release it
 * snaps to the nearest screen edge. With edge-hide on, an idle bubble tucks behind the edge leaving a slim
 * handle; tapping the handle pops it out. Tapping the bubble blooms the control orbit: glass orbs fan out
 * on an arc toward the middle of the screen (mute mic, hide screen, record, end, open app) over a blurred
 * backdrop, with a live status capsule. Runs in preview mode (before going live) so it can be placed, and
 * live mode during a stream. The OS capture indicator is separate and always visible.
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

    // Control orbit: a full-screen overlay window, alive only while open.
    private var stage: FrameLayout? = null
    private var stageLp: WindowManager.LayoutParams? = null
    private var closing = false
    private var endArmed = false
    private lateinit var hub: ImageView
    private lateinit var capsule: LinearLayout
    private lateinit var capsuleText: TextView
    private lateinit var liveDot: View
    private var pulse: ObjectAnimator? = null
    private lateinit var mic: Orb
    private lateinit var screenOrb: Orb
    private lateinit var rec: Orb
    private lateinit var end: Orb
    private lateinit var app: Orb
    private var hubX = 0f; private var hubY = 0f

    private val tick = object : Runnable {
        override fun run() {
            timer.text = clock()
            handler.postDelayed(this, 1000)
        }
    }
    private val orbitTick = object : Runnable {
        override fun run() { refreshOrbit(); handler.postDelayed(this, 1000) }
    }
    private val tuck = Runnable { if (edgeHide) tuckAway() }
    private val autoClose = Runnable { closeOrbit() }
    private val disarmEnd = Runnable { endArmed = false; refreshOrbit() }

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
            setTextColor(Color.WHITE); textSize = 12f; visibility = View.GONE
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

    private fun scheduleTuck() { handler.removeCallbacks(tuck); if (stage == null) handler.postDelayed(tuck, IDLE_MS) }

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
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) { lp.x = startX + dx; lp.y = startY + dy; wm.updateViewLayout(root, lp) }   // 1:1 with the finger
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> when {
                    moved || tucked -> snap(animate = true)        // dropped, or tapped the handle: pop back out
                    else -> openOrbit()
                }
            }
            true
        }
    }

    /* ---------- control orbit ---------- */

    /** One glass orb with a caption; paints itself idle / active (red glow) / disabled. */
    @Suppress("ClickableViewAccessibility")
    private inner class Orb(res: Int, private val onTap: () -> Unit) {
        val disc = ImageView(this@BubbleService).apply {
            setImageResource(res)
            val pad = dp(15); setPadding(pad, pad, pad, pad)
            elevation = dp(10).toFloat(); outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        val caption = TextView(this@BubbleService).apply {
            textSize = 11f; setTextColor(0xE6FFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(dp(3).toFloat(), 0f, 0f, 0x99000000.toInt())
        }
        val view = LinearLayout(this@BubbleService).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false
            addView(disc, LinearLayout.LayoutParams(dp(ORB), dp(ORB)))
            addView(caption, LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        var enabled = true

        init {
            disc.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.84f).scaleY(0.84f).setDuration(90)
                        .setInterpolator(DecelerateInterpolator()).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(OvershootInterpolator(3f)).start()
                        val inside = e.x in 0f..v.width.toFloat() && e.y in 0f..v.height.toFloat()
                        if (e.actionMasked == MotionEvent.ACTION_UP && inside && enabled) {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onTap(); refreshOrbit()
                            handler.removeCallbacks(autoClose); handler.postDelayed(autoClose, ORBIT_MS)
                        }
                    }
                }
                true
            }
        }

        fun paint(label: String, on: Boolean, enabled: Boolean, res: Int? = null) {
            this.enabled = enabled
            res?.let { disc.setImageResource(it) }
            caption.text = label; disc.contentDescription = label
            disc.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                if (on) intArrayOf(0xFFFF6A45.toInt(), 0xFFFF2D55.toInt())       // ember to Apple pink-red
                else intArrayOf(0xF2414145.toInt(), 0xF21C1C1E.toInt())).apply {  // dark glass, lit from above
                shape = GradientDrawable.OVAL
                setStroke(dp(1), if (on) 0x66FFFFFF else 0x30FFFFFF)
            }
            if (Build.VERSION.SDK_INT >= 28) {
                disc.outlineSpotShadowColor = if (on) RED else Color.BLACK
                disc.outlineAmbientShadowColor = if (on) RED else Color.BLACK
            }
            disc.alpha = if (enabled) 1f else 0.55f
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun openOrbit() {
        if (stage != null) return
        handler.removeCallbacks(tuck)
        root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

        mic = Orb(R.drawable.ic_mic) { StreamService.toggleMic() }
        screenOrb = Orb(R.drawable.ic_pause) { StreamService.toggleBreak() }
        rec = Orb(R.drawable.ic_record) { StreamService.toggleRecord() }
        end = Orb(R.drawable.ic_stop) {
            if (endArmed) startService(Intent(this, StreamService::class.java).setAction(StreamService.ACTION_STOP))
            else { endArmed = true; handler.postDelayed(disarmEnd, 3000) }
        }
        app = Orb(R.drawable.ic_home) {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            closeOrbit()
        }
        hub = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            val pad = dp(17); setPadding(pad, pad, pad, pad)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFF8A50.toInt(), ACCENT))
                .apply { shape = GradientDrawable.OVAL }
            elevation = dp(14).toFloat(); outlineProvider = ViewOutlineProvider.BACKGROUND
            if (Build.VERSION.SDK_INT >= 28) { outlineSpotShadowColor = ACCENT; outlineAmbientShadowColor = ACCENT }
            contentDescription = "Close controls"
            setOnClickListener { closeOrbit() }
        }
        liveDot = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(RED) } }
        capsuleText = TextView(this).apply {
            textSize = 13f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"                    // the timer doesn't jitter
        }
        capsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(9), dp(16), dp(9))
            elevation = dp(8).toFloat()
            addView(liveDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(9) })
            addView(capsuleText)
        }

        val scrim = ColorDrawable(0x73000000).apply { alpha = 0 }
        val st = FrameLayout(this).apply {
            background = scrim; clipChildren = false
            setOnClickListener { closeOrbit() }             // tap anywhere off the orbs folds them back
            listOf(mic, screenOrb, rec, end, app).forEach { addView(it.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
            addView(capsule, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(hub, FrameLayout.LayoutParams(size, size))
            visibility = View.INVISIBLE                     // revealed once laid out, so nothing flashes at 0,0
        }
        stage = st; closing = false; endArmed = false
        refreshOrbit()

        val slp = WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        // Real frosted backdrop on Android 12+ (when the device has cross-window blur on).
        val blur = Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled
        if (blur) slp.flags = slp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        stageLp = slp
        runCatching { wm.addView(st, slp) }.onFailure { stage = null; return }

        st.post {
            val so = IntArray(2).also { st.getLocationOnScreen(it) }
            val bo = IntArray(2).also { root.getLocationOnScreen(it) }
            hubX = (bo[0] - so[0] + size / 2).toFloat(); hubY = (bo[1] - so[1] + size / 2).toFloat()
            root.visibility = View.INVISIBLE                // the hub stands in for the bubble while open
            val w = st.width.toFloat(); val h = st.height.toFloat()
            val r = dp(RADIUS).toFloat()
            val centres = orbitCentres(hubX, hubY, w, h, 5, r, SPAN_DEG)
            val m = dp(40).toFloat()
            listOf(mic, screenOrb, rec, end, app).forEachIndexed { i, o ->
                o.view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val (cx, cy) = centres[i]
                val tx = cx.coerceIn(m, w - m) - o.view.measuredWidth / 2f
                val ty = cy.coerceIn(m, h - m - dp(20)) - dp(ORB) / 2f
                o.view.translationX = hubX - o.view.measuredWidth / 2f; o.view.translationY = hubY - dp(ORB) / 2f
                o.view.scaleX = 0.3f; o.view.scaleY = 0.3f; o.view.alpha = 0f
                o.view.animate().translationX(tx).translationY(ty).scaleX(1f).scaleY(1f).alpha(1f)
                    .setStartDelay(i * 32L).setDuration(420).setInterpolator(OvershootInterpolator(1.3f)).start()
            }
            // Status capsule floats just above the arc (below it when there's no room), centred over the orbit.
            capsule.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val cw = capsule.measuredWidth.toFloat(); val ch = capsule.measuredHeight.toFloat()
            val a = atan2(h / 2 - hubY, w / 2 - hubX)
            val top = centres.minOf { it.second } - dp(ORB) / 2f
            val bottom = centres.maxOf { it.second } + dp(ORB) / 2f + dp(22)      // + caption
            val gap = dp(16)
            capsule.translationX = (hubX + cos(a) * r / 2 - cw / 2).coerceIn(dp(12).toFloat(), w - cw - dp(12))
            capsule.translationY = if (top - gap - ch >= dp(40)) top - gap - ch else (bottom + gap).coerceAtMost(h - ch - dp(12))
            capsule.alpha = 0f; capsule.scaleX = 0.85f; capsule.scaleY = 0.85f
            capsule.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(140).setDuration(360)
                .setInterpolator(OvershootInterpolator(1.2f)).start()
            hub.translationX = hubX - size / 2f; hub.translationY = hubY - size / 2f
            hub.rotation = -90f
            hub.animate().rotation(0f).setDuration(380).setInterpolator(OvershootInterpolator(1.6f)).start()
            ObjectAnimator.ofInt(scrim, "alpha", 0, 255).setDuration(220).start()
            if (blur) ValueAnimator.ofInt(0, dp(26)).apply {
                duration = 260
                addUpdateListener { slp.blurBehindRadius = it.animatedValue as Int; runCatching { wm.updateViewLayout(st, slp) } }
                start()
            }
            st.visibility = View.VISIBLE
        }
        handler.post(orbitTick)
        handler.postDelayed(autoClose, ORBIT_MS)
    }

    /** Folds the orbs back into the bubble, then removes the overlay. */
    private fun closeOrbit() {
        val st = stage ?: return
        if (closing) return
        closing = true
        handler.removeCallbacks(orbitTick); handler.removeCallbacks(autoClose); handler.removeCallbacks(disarmEnd)
        pulse?.cancel(); pulse = null
        listOf(mic, screenOrb, rec, end, app).forEach { o ->
            o.view.animate().translationX(hubX - o.view.width / 2f).translationY(hubY - dp(ORB) / 2f)
                .scaleX(0.3f).scaleY(0.3f).alpha(0f).setStartDelay(0).setDuration(170)
                .setInterpolator(AccelerateInterpolator()).start()
        }
        capsule.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setStartDelay(0).setDuration(140).start()
        hub.animate().rotation(-90f).setDuration(170).start()
        (st.background as? ColorDrawable)?.let { ObjectAnimator.ofInt(it, "alpha", it.alpha, 0).setDuration(170).start() }
        handler.postDelayed({
            runCatching { wm.removeView(st) }
            stage = null; stageLp = null; closing = false; endArmed = false
            root.visibility = View.VISIBLE
            scheduleTuck()
        }, 190)
    }

    /** Live state into the orbit; in preview the controls are shown but dimmed. */
    private fun refreshOrbit() {
        if (stage == null) return
        val c = StreamService.controls()
        val live = c != null
        mic.paint(if (c?.micMuted == true) "Unmute" else "Mute", c?.micMuted == true, live,
            if (c?.micMuted == true) R.drawable.ic_mic_off else R.drawable.ic_mic)
        screenOrb.paint(if (c?.onBreak == true) "End break" else "Break", c?.onBreak == true, live)
        rec.paint(if (c?.recording == true) "Stop rec" else "Record", c?.recording == true, live)
        end.paint(if (endArmed) "Tap to end" else "End", endArmed, live)
        app.paint("Open app", false, true)

        capsuleText.text = when {
            !live -> "Preview  ·  go live to use controls"
            endArmed -> "Tap End again to stop streaming"
            c.onBreak -> "ON BREAK  ${clock()}  ·  viewers see your break screen"
            else -> "LIVE  ${clock()}  ·  %.1f Mbps".format(StreamService.lastBitrate.value / 1_000_000f)
        }
        capsule.background = GradientDrawable().apply {
            cornerRadius = dp(100).toFloat()
            setColor(if (endArmed) 0xF2FF3B30.toInt() else 0xE61C1C1E.toInt())
            setStroke(dp(1), 0x30FFFFFF)
        }
        (liveDot.background as GradientDrawable).setColor(if (live && !endArmed) RED else 0xFF8E8E93.toInt())
        if (live && !endArmed && pulse == null) {
            pulse = ObjectAnimator.ofFloat(liveDot, "alpha", 1f, 0.25f).apply {
                duration = 750; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start()
            }
        } else if ((!live || endArmed) && pulse != null) { pulse?.cancel(); pulse = null; liveDot.alpha = 1f }
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun screen(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }.let { it.x to it.y }

    override fun onDestroy() {
        stage?.let { runCatching { wm.removeView(it) } }; stage = null
        pulse?.cancel()
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
        private const val ORBIT_MS = 8000L               // orbit folds itself after this long untouched
        private const val DEFAULT_ICON_ZOOM = 1.75f
        private const val ORB = 54                       // orb diameter, dp
        private const val RADIUS = 118                   // orbit radius, dp
        private const val SPAN_DEG = 150.0
        private const val ACCENT = 0xFFF2542D.toInt()
        private const val RED = 0xFFFF3B30.toInt()

        /**
         * Centres of n orbs spread evenly over an arc of [spanDeg] around (cx, cy), opening toward the middle
         * of a w×h screen, so a bubble on any edge or corner fans its controls inward.
         */
        fun orbitCentres(cx: Float, cy: Float, w: Float, h: Float, n: Int, r: Float, spanDeg: Double): List<Pair<Float, Float>> {
            val mid = atan2((h / 2 - cy).toDouble(), (w / 2 - cx).toDouble())
            val span = Math.toRadians(spanDeg)
            return List(n) { i ->
                val a = mid - span / 2 + if (n == 1) span / 2 else span * i / (n - 1)
                (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()
            }
        }

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
