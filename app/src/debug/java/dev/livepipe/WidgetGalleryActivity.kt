package dev.livepipe

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Debug-only design review: renders each widget at real launcher cell sizes, light or dark, ready or live.
 * adb shell am start -n dev.livepipe.debug/dev.livepipe.WidgetGalleryActivity --ez night true --ez live false --es grid tall
 */
class WidgetGalleryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val night = intent.getBooleanExtra("night", true)
        val live = intent.getBooleanExtra("live", false)
        val tall = intent.getStringExtra("grid") != "square"
        val cfg = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        val ctx = createConfigurationContext(cfg)
        val a = Destination("a", "Ranked grind", "rtmps://a.rtmp.youtube.com/live2", "", pinned = true)
        val b = Destination("b", "Chill builds", "rtmp://live.twitch.tv/app", "")
        val c = Destination("c", "Late night IRL", "rtmps://live-api-s.facebook.com:443/rtmp/", "")
        val k = Destination("k", "Speedrun Sunday", "rtmps://fa723fc1b171.global-contribute.live-video.net/app", "")
        val controls = StreamService.Companion.Controls(false, false, true, false, false, false, false, false, false)
        val snap = Snap(listOf(a, b, c, k), a, if (live) StreamService.State.LIVE else StreamService.State.IDLE,
            SystemClock.elapsedRealtime() - 4_521_000, if (live) controls else null, Prefs())
        // Cell sizes: this OnePlus (5 columns, tall rows) or a Pixel-style square grid.
        val sizes = if (tall) listOf(
            R.layout.widget_orb to (66 to 104), R.layout.widget_studio to (140 to 222), R.layout.widget_strip to (300 to 104),
            R.layout.widget_medium to (300 to 222), R.layout.widget_dashboard to (300 to 460),
        ) else listOf(
            R.layout.widget_orb to (74 to 74), R.layout.widget_studio to (160 to 160), R.layout.widget_strip to (328 to 74),
            R.layout.widget_medium to (328 to 160), R.layout.widget_dashboard to (328 to 340),
        )
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFFFFAF7B.toInt()))     // a typical bright wallpaper
        }
        sizes.forEach { (layout, wh) ->
            val frame = FrameLayout(this)
            col.addView(frame, LinearLayout.LayoutParams((wh.first * d).toInt(), (wh.second * d).toInt()).apply { topMargin = (14 * d).toInt() })
            frame.addView(Widgets.views(ctx, layout, snap, rows = if (wh.second < 410) 3 else 4).apply(ctx, frame))
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
