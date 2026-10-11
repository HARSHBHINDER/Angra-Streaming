package dev.livepipe

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import dev.livepipe.ui.platformOf

/**
 * Home-screen widgets in Apple's widget language: a glance (platform avatar, status capsule, a big state line
 * that becomes the live timer, one-line caption) and Shortcuts-style action tiles (icon top-left, label
 * bottom-left; filled = on, glass = off). Size ladder: 1x1 action, 2x2 glance, 4x1 actions, 4x2 glance +
 * actions, 4x4 glance + channel list + actions. 16dp margins, 8dp gutters, concentric radii.
 */
abstract class StreamWidget(private val layout: Int) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = mgr.updateAppWidget(ids, Widgets.build(ctx, layout))

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == Widgets.ACTION) Widgets.handle(ctx, intent)
    }
}

class OrbWidget : StreamWidget(R.layout.widget_orb)
class StudioWidget : StreamWidget(R.layout.widget_studio)
class StripWidget : StreamWidget(R.layout.widget_strip)
class MediumWidget : StreamWidget(R.layout.widget_medium)
class DashboardWidget : StreamWidget(R.layout.widget_dashboard)

/** Everything a widget shows, so it can be rendered from live state or from samples. */
data class Snap(
    val dests: List<Destination>,
    val active: Destination?,
    val state: StreamService.State,
    val liveSince: Long,
    val c: StreamService.Companion.Controls?,
    val prefs: Prefs,
) {
    val live get() = state == StreamService.State.LIVE
    val busy get() = live || state == StreamService.State.CONNECTING

    companion object {
        fun now(ctx: Context): Snap {
            val store = Store(ctx)
            val all = store.destinations
            return Snap(all.filter { it.pinned } + all.filterNot { it.pinned }, store.active(), StreamService.state.value,
                StreamService.liveSince.value, StreamService.controls(), store.prefsBundle)
        }
    }
}

/**
 * Fills the widgets and wires their taps. Live controls (end, break, mute, record) go straight to the running
 * stream; Go Live opens the app, which asks Android for screen-capture consent.
 */
object Widgets {
    const val ACTION = "dev.livepipe.WIDGET"
    const val ACTION_GO_LIVE = "dev.livepipe.GO_LIVE"
    const val ACTION_SUPER_CHAT = "dev.livepipe.OPEN_SUPER_CHAT"

    private val all = listOf(
        OrbWidget::class.java to R.layout.widget_orb,
        StudioWidget::class.java to R.layout.widget_studio,
        StripWidget::class.java to R.layout.widget_strip,
        MediumWidget::class.java to R.layout.widget_medium,
        DashboardWidget::class.java to R.layout.widget_dashboard,
    )
    private val tiles = listOf(
        Triple(R.id.wg_t1, R.id.wg_t1_icon, R.id.wg_t1_label), Triple(R.id.wg_t2, R.id.wg_t2_icon, R.id.wg_t2_label),
        Triple(R.id.wg_t3, R.id.wg_t3_icon, R.id.wg_t3_label), Triple(R.id.wg_t4, R.id.wg_t4_icon, R.id.wg_t4_label),
    )

    /** Redraws every placed widget. Cheap; call on any state change. */
    fun refresh(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        val snap = Snap.now(ctx)
        all.forEach { (cls, layout) ->
            val ids = m.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) m.updateAppWidget(ids, build(ctx, layout, snap))
        }
    }

    /** Taps that don't need the app open: pick a channel, flip Record. */
    fun handle(ctx: Context, intent: Intent) {
        val store = Store(ctx)
        intent.getStringExtra("select")?.let { store.activeId = it }
        if (intent.getStringExtra("pref") == "record") {
            store.prefsBundle = store.prefsBundle.let { it.copy(recordWhileStreaming = !it.recordWhileStreaming) }
        }
        refresh(ctx)
    }

    private class Action(val icon: Int, val label: String, val bg: Int, val on: Boolean, val enabled: Boolean, val tap: PendingIntent)

    /** The dashboard lists 4 channels when tall enough (Android 12+ picks per size), else 3 so its tiles keep their height. */
    fun build(ctx: Context, layout: Int, s: Snap = Snap.now(ctx)): RemoteViews =
        if (layout != R.layout.widget_dashboard || Build.VERSION.SDK_INT < 31) views(ctx, layout, s, rows = 3)
        else RemoteViews(mapOf(SizeF(250f, 250f) to views(ctx, layout, s, rows = 3), SizeF(250f, 410f) to views(ctx, layout, s, rows = 4)))

    fun views(ctx: Context, layout: Int, s: Snap = Snap.now(ctx), rows: Int = 4): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val text = ctx.getColor(R.color.wg_text)
        val open = activity(ctx, Intent.ACTION_MAIN, 1)
        val go = when {
            s.busy -> service(ctx, StreamService.ACTION_STOP, 2)
            s.active != null -> activity(ctx, ACTION_GO_LIVE, 3)
            else -> open
        }
        fun liveTap(action: String, code: Int) = if (s.live) service(ctx, action, code) else open

        val goAction = when {
            s.busy -> Action(R.drawable.ic_stop, "End", R.drawable.wg_tile_end, true, true, go)
            s.active == null -> Action(R.drawable.ic_play, "Set up", R.drawable.wg_tile_go, true, true, go)
            else -> Action(R.drawable.ic_play, "Go Live", R.drawable.wg_tile_go, true, true, go)
        }
        val onBreak = s.c?.onBreak == true
        val muted = s.c?.micMuted == true
        val breakAction = Action(R.drawable.ic_pause, if (onBreak) "On break" else "Break",
            if (onBreak) R.drawable.wg_tile_on else R.drawable.wg_tile_off, onBreak, s.live, liveTap(StreamService.ACTION_BREAK, 4))
        val muteAction = Action(if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic, if (muted) "Muted" else "Mute",
            if (muted) R.drawable.wg_tile_on else R.drawable.wg_tile_off, muted, s.live, liveTap(StreamService.ACTION_MIC, 5))
        val superChat = Action(R.drawable.ic_dollar, "Super Chat", R.drawable.wg_tile_sc, true, true, activity(ctx, ACTION_SUPER_CHAT, 6))
        val rec = if (s.live) s.c?.recording == true else s.prefs.recordWhileStreaming
        val recAction = Action(R.drawable.ic_record, if (rec) "Recording" else "Record", if (rec) R.drawable.wg_tile_end else R.drawable.wg_tile_off,
            rec, true, if (s.live) service(ctx, StreamService.ACTION_RECORD, 20) else broadcast(ctx, 21) { putExtra("pref", "record") })

        fun put(actions: List<Action>) = actions.forEachIndexed { i, a ->
            val (id, icon, label) = tiles[i]
            v.setInt(id, "setBackgroundResource", a.bg)
            v.setImageViewResource(icon, a.icon)
            val fg = if (a.on) Color.WHITE else text
            v.setInt(icon, "setColorFilter", fg)
            v.setInt(icon, "setImageAlpha", if (a.enabled) 255 else 77)
            v.setTextViewText(label, a.label)
            v.setTextColor(label, if (a.enabled) fg else (fg and 0x00FFFFFF) or 0x4D000000)
            v.setOnClickPendingIntent(id, a.tap)
        }
        fun glance() {
            val a = s.active
            if (a != null) avatar(v, R.id.wg_avatar, a) else {
                v.setTextViewText(R.id.wg_avatar, "+")
                v.setInt(R.id.wg_avatar, "setBackgroundResource", R.drawable.wg_av_rtmp)
            }
            v.setTextViewText(R.id.wg_status, when { s.live -> "● LIVE"; s.busy -> "CONNECTING"; a == null -> "SET UP"; else -> "READY" })
            v.setInt(R.id.wg_status, "setBackgroundResource", if (s.live) R.drawable.wg_capsule_live else R.drawable.wg_capsule)
            v.setTextColor(R.id.wg_status, if (s.live) Color.WHITE else text)
            // Big state line: "Ready" / "Set up" / "Connecting…", or the running timer while live.
            v.setViewVisibility(R.id.wg_title, if (s.live) View.GONE else View.VISIBLE)
            v.setTextViewText(R.id.wg_title, if (s.busy) "Connecting…" else if (a == null) "Set up" else "Ready")
            timer(v, s)
            v.setTextViewText(R.id.wg_sub, when {
                a == null -> "Add a channel to go live"
                s.live -> "${a.name} · ${platformOf(a.url)}"
                else -> "${a.name} · ${a.height}p${a.fps}"
            })
        }

        when (layout) {
            R.layout.widget_orb -> {
                // The 1x1 never ends a stream (too easy to brush); while live it opens the app.
                v.setInt(R.id.wg_orb, "setBackgroundResource", if (s.busy) R.drawable.wg_orb_live else R.drawable.wg_orb)
                v.setViewVisibility(R.id.wg_orb_icon, if (s.busy) View.GONE else View.VISIBLE)
                v.setViewVisibility(R.id.wg_orb_tag, if (s.busy) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.wg_orb_tag, if (s.live) "● LIVE" else "CONNECTING")
                v.setViewVisibility(R.id.wg_orb_label, if (s.live) View.GONE else View.VISIBLE)
                v.setTextViewText(R.id.wg_orb_label, if (s.busy) "…" else if (s.active == null) "Set up" else "Go Live")
                timer(v, s)
                v.setOnClickPendingIntent(R.id.wg_orb, if (s.busy) open else go)
            }
            R.layout.widget_studio -> {
                glance()
                v.setOnClickPendingIntent(R.id.wg_root, if (s.busy) open else go)   // small widgets: one tap target
            }
            R.layout.widget_strip -> put(listOf(goAction, breakAction, muteAction, superChat))
            R.layout.widget_medium -> {
                glance()
                put(listOf(goAction, breakAction, muteAction, superChat))
                v.setOnClickPendingIntent(R.id.wg_root, open)
            }
            R.layout.widget_dashboard -> {
                glance()
                v.setTextViewText(R.id.wg_go, if (s.busy) "End" else if (s.active == null) "Set up" else "Go Live")
                v.setInt(R.id.wg_go, "setBackgroundResource", if (s.busy) R.drawable.wg_capsule_end else R.drawable.wg_capsule_go)
                v.setOnClickPendingIntent(R.id.wg_go, go)
                v.setViewVisibility(R.id.wg_empty, if (s.dests.isEmpty()) View.VISIBLE else View.GONE)
                listOf(
                    listOf(R.id.wg_ch1, R.id.wg_ch1_av, R.id.wg_ch1_name, R.id.wg_ch1_detail, R.id.wg_ch1_check, 0),
                    listOf(R.id.wg_ch2, R.id.wg_ch2_av, R.id.wg_ch2_name, R.id.wg_ch2_detail, R.id.wg_ch2_check, R.id.wg_sep2),
                    listOf(R.id.wg_ch3, R.id.wg_ch3_av, R.id.wg_ch3_name, R.id.wg_ch3_detail, R.id.wg_ch3_check, R.id.wg_sep3),
                    listOf(R.id.wg_ch4, R.id.wg_ch4_av, R.id.wg_ch4_name, R.id.wg_ch4_detail, R.id.wg_ch4_check, R.id.wg_sep4),
                ).forEachIndexed { i, ids ->
                    val d = if (i < rows) s.dests.getOrNull(i) else null
                    val show = if (d == null) View.GONE else View.VISIBLE
                    v.setViewVisibility(ids[0], show)
                    if (ids[5] != 0) v.setViewVisibility(ids[5], show)
                    if (d == null) return@forEachIndexed
                    val on = d.id == s.active?.id
                    avatar(v, ids[1], d)
                    v.setTextViewText(ids[2], (if (d.pinned) "★ " else "") + d.name)
                    v.setTextViewText(ids[3], platformOf(d.url))
                    v.setViewVisibility(ids[4], if (on) View.VISIBLE else View.INVISIBLE)
                    // The channel can't change mid-stream; while live the rows open the app.
                    v.setOnClickPendingIntent(ids[0], if (s.busy) open else broadcast(ctx, 10 + i) { putExtra("select", d.id) })
                }
                put(listOf(recAction, breakAction, muteAction, superChat))
                v.setOnClickPendingIntent(R.id.wg_root, open)
            }
        }
        return v
    }

    private fun timer(v: RemoteViews, s: Snap) {
        v.setViewVisibility(R.id.wg_timer, if (s.live) View.VISIBLE else View.GONE)
        if (s.live) v.setChronometer(R.id.wg_timer, s.liveSince, null, true)
    }

    /** Letter avatar tinted with the channel's platform colour (the Twick / Twitch widget pattern). */
    private fun avatar(v: RemoteViews, id: Int, d: Destination) {
        val platform = platformOf(d.url)
        v.setTextViewText(id, d.name.trim().take(1).uppercase().ifBlank { "•" })
        v.setInt(id, "setBackgroundResource", when (platform) {
            "YouTube" -> R.drawable.wg_av_yt; "Twitch" -> R.drawable.wg_av_tw; "Kick" -> R.drawable.wg_av_kick
            "Facebook" -> R.drawable.wg_av_fb; else -> R.drawable.wg_av_rtmp
        })
        v.setTextColor(id, if (platform == "Kick") Color.BLACK else Color.WHITE)
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
