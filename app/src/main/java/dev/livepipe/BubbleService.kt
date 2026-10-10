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
import android.widget.TextView
import kotlin.math.abs

/**
 * Floating control bubble. Shows a custom icon or the elapsed stream timer (tap toggles). Drag it anywhere;
 * on release it snaps to the nearest screen edge. With edge-hide on, an idle bubble tucks behind the edge
 * leaving a slim handle; tapping the handle pops it out. Runs in preview mode (before going live) so it
 * can be placed, and live mode during a stream. The OS capture indicator is separate and always visible.
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

    private val tick = object : Runnable {
        override fun run() {
            val s = if (startElapsed == 0L) 0 else (SystemClock.elapsedRealtime() - startElapsed) / 1000
            timer.text = "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
            handler.postDelayed(this, 1000)
        }
    }
    private val tuck = Runnable { if (edgeHide) tuckAway() }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        timer = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 12f; visibility = View.GONE
            gravity = Gravity.CENTER; text = "0:00:00"
        }
        root = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE61C1C1E.toInt())
                setStroke(dp(2), 0xFFF2542D.toInt())   // ember ring, matches the app accent
            }
            clipToOutline = true
            addView(icon, FrameLayout.LayoutParams(size, size))
            addView(timer, FrameLayout.LayoutParams(size, size))
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        lp = WindowManager.LayoutParams(size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        val p = Store(this).prefsBundle
        val (w, h) = screen()
        lp.x = (p.bubbleX * (w - size)).toInt(); lp.y = (p.bubbleY * (h - size)).toInt()
        root.setOnTouchListener(dragAndTap())
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
        handler.removeCallbacks(tuck); handler.postDelayed(tuck, IDLE_MS)
    }

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
                    else -> {
                        showsTimer = !showsTimer; render()
                        if (startElapsed != 0L)
                            startService(Intent(this, StreamService::class.java).setAction(StreamService.ACTION_BUBBLE_TAP))
                        handler.removeCallbacks(tuck); handler.postDelayed(tuck, IDLE_MS)
                    }
                }
            }
            true
        }
    }

    private fun screen(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }.let { it.x to it.y }

    override fun onDestroy() {
        handler.removeCallbacks(tick); handler.removeCallbacks(tuck); anim?.cancel()
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
