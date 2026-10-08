package dev.livepipe

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/**
 * Draggable floating control bubble. Shows a custom icon or the elapsed stream timer; tap toggles
 * which. It only exists while streaming, so it disappears when the stream stops. The OS screen-capture
 * indicator is separate and always visible — this bubble's transparency does not affect it.
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

    private val tick = object : Runnable {
        override fun run() {
            val s = (SystemClock.elapsedRealtime() - startElapsed) / 1000
            timer.text = "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(56)

        icon = ImageView(this).apply {
            setImageResource(android.R.drawable.presence_video_online)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        timer = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 13f; visibility = View.GONE
            gravity = Gravity.CENTER; text = "0:00:00"
        }
        root = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC1C1C1E.toInt()) // iOS-style dark translucent pill
            }
            clipToOutline = true
            addView(icon, FrameLayout.LayoutParams(size, size))
            addView(timer, FrameLayout.LayoutParams(size, size))
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        lp = WindowManager.LayoutParams(size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16); y = dp(120)
        }
        root.setOnTouchListener(dragAndTap())
        wm.addView(root, lp)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startElapsed = intent?.getLongExtra(EXTRA_START, SystemClock.elapsedRealtime())
            ?: SystemClock.elapsedRealtime()
        apply(
            showsTimer = intent?.getBooleanExtra(EXTRA_TIMER, false) ?: false,
            transparency = intent?.getFloatExtra(EXTRA_ALPHA, 0.2f) ?: 0.2f,
            imageUri = intent?.getStringExtra(EXTRA_IMAGE).orEmpty(),
        )
        return START_STICKY
    }

    private fun apply(showsTimer: Boolean, transparency: Float, imageUri: String) {
        this.showsTimer = showsTimer
        // Clamp so the bubble can go very faint but never fully gone while live.
        root.alpha = (1f - transparency.coerceIn(0f, Prefs.BUBBLE_MAX_TRANSPARENCY)).coerceAtLeast(0.05f)
        if (imageUri.isNotBlank()) runCatching {
            contentResolver.openInputStream(Uri.parse(imageUri))?.use {
                icon.setImageBitmap(android.graphics.BitmapFactory.decodeStream(it))
            }
        }
        render()
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

    private fun dragAndTap(): View.OnTouchListener {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        return View.OnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    lp.x = startX + dx; lp.y = startY + dy
                    wm.updateViewLayout(root, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    showsTimer = !showsTimer; render()
                    startService(Intent(this, StreamService::class.java).setAction(StreamService.ACTION_BUBBLE_TAP))
                }
            }
            true
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { wm.removeView(root) }
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_START = "start"
        const val EXTRA_TIMER = "timer"
        const val EXTRA_ALPHA = "alpha"
        const val EXTRA_IMAGE = "image"

        fun start(ctx: Context, p: Prefs, startElapsed: Long) {
            ctx.startService(Intent(ctx, BubbleService::class.java)
                .putExtra(EXTRA_START, startElapsed)
                .putExtra(EXTRA_TIMER, p.bubbleShowsTimer)
                .putExtra(EXTRA_ALPHA, p.bubbleTransparency)
                .putExtra(EXTRA_IMAGE, p.bubbleImageUri))
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, BubbleService::class.java))
    }
}
