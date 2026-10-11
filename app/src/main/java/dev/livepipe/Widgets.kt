package dev.livepipe

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import dev.livepipe.ui.platformOf

/**
 * Home-screen widgets, smallest to largest, following what streaming and control apps ship (Twitch/Twick
 * channel status with platform-tinted avatars, Elgato-style toggles with clear state, Spotify-style size
 * ladder) and Android's widget quality guidelines (system radius, light/dark, header, empty states, previews):
 * Go Live 1×1, Studio 2×2, Control strip 4×1, Dashboard 4×4.
 */
abstract class StreamWidget(private val layout: Int) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = mgr.updateAppWidget(ids, Widgets.views(ctx, layout))

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == Widgets.ACTION) Widgets.handle(ctx, intent)
    }
}

class OrbWidget : StreamWidget(R.layout.widget_orb)
class StudioWidget : StreamWidget(R.layout.widget_studio)
class StripWidget : StreamWidget(R.layout.widget_strip)
class DashboardWidget : StreamWidget(R.layout.widget_dashboard)

/**
 * Fills the widgets from the stream state and wires their taps. Live controls (end, break, mute, record) go
 * straight to the running stream; Go Live opens the app, which asks Android for screen-capture consent.
 */
object Widgets {
    const val ACTION = "dev.livepipe.WIDGET"
    const val ACTION_GO_LIVE = "dev.livepipe.GO_LIVE"
    const val ACTION_SUPER_CHAT = "dev.livepipe.OPEN_SUPER_CHAT"

    private val all = listOf(
        OrbWidget::class.java to R.layout.widget_orb,
        StudioWidget::class.java to R.layout.widget_studio,
        StripWidget::class.java to R.layout.widget_strip,
        DashboardWidget::class.java to R.layout.widget_dashboard,
    )

    /** Redraws every placed widget. Cheap; call on any state change. */
    fun refresh(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        all.forEach { (cls, layout) ->
            val ids = m.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) m.updateAppWidget(ids, views(ctx, layout))
        }
    }

    /** Taps that don't need the app open: pick a channel, flip Record or Bubble. */
    fun handle(ctx: Context, intent: Intent) {
        val store = Store(ctx)
        intent.getStringExtra("select")?.let { store.activeId = it }
        when (intent.getStringExtra("pref")) {
            "record" -> store.prefsBundle = store.prefsBundle.let { it.copy(recordWhileStreaming = !it.recordWhileStreaming) }
            "bubble" -> store.prefsBundle = store.prefsBundle.let { it.copy(bubbleEnabled = !it.bubbleEnabled) }
        }
        refresh(ctx)
    }

    /** Letter avatar tinted with the channel's platform colour. */
    private fun avatar(v: RemoteViews, id: Int, d: Destination) {
        val platform = platformOf(d.url)
        v.setTextViewText(id, d.name.trim().take(1).uppercase().ifBlank { "•" })
        v.setInt(id, "setBackgroundResource", when (platform) {
            "YouTube" -> R.drawable.wg_av_yt; "Twitch" -> R.drawable.wg_av_tw; "Kick" -> R.drawable.wg_av_kick
            "Facebook" -> R.drawable.wg_av_fb; else -> R.drawable.wg_av_rtmp
        })
        v.setTextColor(id, if (platform == "Kick") Color.BLACK else Color.WHITE)
    }

    fun views(ctx: Context, layout: Int): RemoteViews {
        val store = Store(ctx)
        val p = store.prefsBundle
        val dests = store.destinations.let { l -> l.filter { it.pinned } + l.filterNot { it.pinned } }
        val active = store.active()
        val state = StreamService.state.value
        val live = state == StreamService.State.LIVE
        val busy = live || state == StreamService.State.CONNECTING
        val c = StreamService.controls()
        val v = RemoteViews(ctx.packageName, layout)
        val text = ctx.getColor(R.color.wg_text)
        val icon = ctx.getColor(R.color.wg_icon)

        val open = activity(ctx, Intent.ACTION_MAIN, 1)
        val go = when {
            busy -> service(ctx, StreamService.ACTION_STOP, 2)
            active != null -> activity(ctx, ACTION_GO_LIVE, 3)
            else -> open
        }
        fun status() {
            v.setTextViewText(R.id.wg_status, when { live -> "● LIVE"; busy -> "CONNECTING"; active == null -> "SET UP"; else -> "READY" })
            v.setInt(R.id.wg_status, "setBackgroundResource", if (live) R.drawable.wg_live else R.drawable.wg_badge)
            v.setTextColor(R.id.wg_status, if (live) Color.WHITE else text)
        }
        fun timer() {
            v.setViewVisibility(R.id.wg_timer, if (live) View.VISIBLE else View.GONE)
            if (live) v.setChronometer(R.id.wg_timer, StreamService.liveSince.value, null, true)
        }
        fun goPill() {
            v.setTextViewText(R.id.wg_go, if (busy) "End stream" else if (active == null) "Set up" else "Go Live")
            v.setInt(R.id.wg_go, "setBackgroundResource", if (busy) R.drawable.wg_end else R.drawable.wg_go)
            v.setOnClickPendingIntent(R.id.wg_go, go)
        }
        // A live-only control: acts on the stream when live, otherwise opens the app.
        fun liveTap(action: String, code: Int) = if (live) service(ctx, action, code) else open
        // Glass control: ember fill + white glyph when on; dimmed when it can't act yet.
        fun control(id: Int, glyph: Int, on: Boolean, enabled: Boolean, round: Boolean) {
            v.setInt(id, "setBackgroundResource", when {
                on -> if (round) R.drawable.wg_round_on else R.drawable.wg_tile_on
                else -> if (round) R.drawable.wg_round else R.drawable.wg_tile
            })
            v.setInt(glyph, "setColorFilter", if (on) Color.WHITE else icon)
            v.setInt(glyph, "setImageAlpha", if (enabled || on) 255 else 90)
        }

        when (layout) {
            R.layout.widget_orb -> {
                // The orb never ends a stream (too easy to brush); while live it opens the app.
                v.setInt(R.id.wg_orb, "setBackgroundResource", if (busy) R.drawable.wg_orb_live else R.drawable.wg_orb)
                v.setViewVisibility(R.id.wg_orb_icon, if (live) View.GONE else View.VISIBLE)
                v.setTextViewText(R.id.wg_orb_label, if (live) "LIVE" else if (busy) "…" else if (active == null) "Set up" else "Go Live")
                timer()
                v.setOnClickPendingIntent(R.id.wg_orb, if (busy) open else go)
            }
            R.layout.widget_studio -> {
                status(); timer(); goPill()
                if (active != null) avatar(v, R.id.wg_avatar, active)
                v.setViewVisibility(R.id.wg_avatar, if (active != null) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.wg_channel, active?.name ?: "No channel yet")
                v.setTextViewText(R.id.wg_detail, active?.let { if (live) platformOf(it.url) else "${platformOf(it.url)} · ${it.height}p${it.fps}" }
                    ?: "Add one in the app")
                v.setOnClickPendingIntent(R.id.wg_root, open)
            }
            R.layout.widget_strip -> {
                // Live: the pill turns red with a stop icon and the running time; tap to end.
                v.setImageViewResource(R.id.wg_go_icon, if (busy) R.drawable.ic_stop else R.drawable.ic_play)
                v.setViewVisibility(R.id.wg_go_label, if (live) View.GONE else View.VISIBLE)
                v.setTextViewText(R.id.wg_go_label, if (busy) "…" else if (active == null) "Set up" else "Go Live")
                timer()
                v.setInt(R.id.wg_go_btn, "setBackgroundResource", if (busy) R.drawable.wg_end else R.drawable.wg_go)
                v.setOnClickPendingIntent(R.id.wg_go_btn, go)
                control(R.id.wg_break, R.id.wg_break, c?.onBreak == true, live, true)
                v.setOnClickPendingIntent(R.id.wg_break, liveTap(StreamService.ACTION_BREAK, 4))
                v.setImageViewResource(R.id.wg_mic, if (c?.micMuted == true) R.drawable.ic_mic_off else R.drawable.ic_mic)
                control(R.id.wg_mic, R.id.wg_mic, c?.micMuted == true, live, true)
                v.setOnClickPendingIntent(R.id.wg_mic, liveTap(StreamService.ACTION_MIC, 5))
                v.setOnClickPendingIntent(R.id.wg_sc, activity(ctx, ACTION_SUPER_CHAT, 6))
            }
            R.layout.widget_dashboard -> {
                status(); timer(); goPill()
                val chips = listOf(
                    listOf(R.id.wg_ch1, R.id.wg_ch1_av, R.id.wg_ch1_name, R.id.wg_ch1_detail),
                    listOf(R.id.wg_ch2, R.id.wg_ch2_av, R.id.wg_ch2_name, R.id.wg_ch2_detail),
                    listOf(R.id.wg_ch3, R.id.wg_ch3_av, R.id.wg_ch3_name, R.id.wg_ch3_detail),
                )
                v.setViewVisibility(R.id.wg_empty, if (dests.isEmpty()) View.VISIBLE else View.GONE)
                chips.forEachIndexed { i, (row, av, name, detail) ->
                    val d = dests.getOrNull(i)
                    v.setViewVisibility(row, if (d == null) View.GONE else View.VISIBLE)
                    if (d == null) return@forEachIndexed
                    val on = d.id == active?.id
                    avatar(v, av, d)
                    v.setTextViewText(name, (if (d.pinned) "★ " else "") + d.name)
                    v.setTextViewText(detail, if (on) "✓  ${platformOf(d.url)}" else platformOf(d.url))
                    v.setInt(row, "setBackgroundResource", if (on) R.drawable.wg_chip_on else R.drawable.wg_chip)
                    // Switching channel mid-stream isn't possible; while live the rows just open the app.
                    v.setOnClickPendingIntent(row, if (busy) open else broadcast(ctx, 10 + i) { putExtra("select", d.id) })
                }
                val rec = if (live) c?.recording == true else p.recordWhileStreaming
                control(R.id.wg_rec, R.id.wg_rec_icon, rec, true, false)
                v.setTextColor(R.id.wg_rec_label, if (rec) Color.WHITE else text)
                v.setOnClickPendingIntent(R.id.wg_rec, if (live) service(ctx, StreamService.ACTION_RECORD, 20) else broadcast(ctx, 21) { putExtra("pref", "record") })
                control(R.id.wg_bub, R.id.wg_bub_icon, p.bubbleEnabled, true, false)
                v.setTextColor(R.id.wg_bub_label, if (p.bubbleEnabled) Color.WHITE else text)
                v.setOnClickPendingIntent(R.id.wg_bub, broadcast(ctx, 22) { putExtra("pref", "bubble") })
                control(R.id.wg_brk, R.id.wg_brk_icon, c?.onBreak == true, live, false)
                v.setTextColor(R.id.wg_brk_label, if (c?.onBreak == true) Color.WHITE else text)
                v.setOnClickPendingIntent(R.id.wg_brk, liveTap(StreamService.ACTION_BREAK, 23))
                v.setImageViewResource(R.id.wg_mute_icon, if (c?.micMuted == true) R.drawable.ic_mic_off else R.drawable.ic_mic)
                control(R.id.wg_mute, R.id.wg_mute_icon, c?.micMuted == true, live, false)
                v.setTextColor(R.id.wg_mute_label, if (c?.micMuted == true) Color.WHITE else text)
                v.setOnClickPendingIntent(R.id.wg_mute, liveTap(StreamService.ACTION_MIC, 24))
            }
        }
        return v
    }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun activity(ctx: Context, action: String, code: Int) = PendingIntent.getActivity(ctx, code,
        Intent(ctx, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS)

    private fun service(ctx: Context, action: String, code: Int) =
        PendingIntent.getService(ctx, code, Intent(ctx, StreamService::class.java).setAction(action), FLAGS)

    private fun broadcast(ctx: Context, code: Int, extras: Intent.() -> Unit) =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, DashboardWidget::class.java).setAction(ACTION).apply(extras), FLAGS)
}
