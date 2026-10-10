package dev.livepipe

/**
 * Everything the bubble can do while live. The orbit shows the [SLOTS] actions saved in [Prefs.bubbleSlots];
 * the user swaps them in Settings → Bubble controls. [live] = only works while a stream is running.
 */
enum class BubbleAction(val id: String, val label: String, val onLabel: String, val icon: Int, val live: Boolean) {
    MIC("mic", "Mute", "Unmute", R.drawable.ic_mic, true),
    BREAK("break", "Break", "End break", R.drawable.ic_pause, true),
    RECORD("record", "Record", "Stop rec", R.drawable.ic_record, true),
    END("end", "End", "Tap to end", R.drawable.ic_stop, true),
    APP("app", "Open app", "Open app", R.drawable.ic_home, false),
    SCREENSHOT("shot", "Screenshot", "Screenshot", R.drawable.ic_camera, true),
    REC_PAUSE("recpause", "Pause rec", "Resume rec", R.drawable.ic_pause_circle, true),
    GAME("game", "Game audio", "Game muted", R.drawable.ic_volume_off, true),
    FACECAM("facecam", "Facecam", "Facecam on", R.drawable.ic_video, true),
    OVERLAY("overlay", "Overlay", "Overlay off", R.drawable.ic_layers, true),
    DND("dnd", "Silence alerts", "Alerts off", R.drawable.ic_dnd, false),
    HIGHLIGHT("mark", "Highlight", "Highlight", R.drawable.ic_flag, true),
    DATA_SAVER("saver", "Data saver", "Saver on", R.drawable.ic_arrow_down, true),
    ZOOM("zoom", "Zoom", "Zoomed", R.drawable.ic_zoom, true),
    TIMER("timer", "Timer face", "Icon face", R.drawable.ic_timer, false),
    HIDE("hide", "Hide 30 s", "Hide 30 s", R.drawable.ic_eye_off, false);

    companion object {
        const val SLOTS = 5
        val DEFAULT = listOf(MIC, BREAK, RECORD, END, APP)
        val DEFAULT_IDS = DEFAULT.joinToString(",") { it.id }

        fun of(id: String) = entries.firstOrNull { it.id == id }

        /** Saved ids into exactly [SLOTS] distinct actions; unknown or missing ids fall back to the defaults. */
        fun slots(ids: String): List<BubbleAction> {
            val picked = ids.split(',').mapNotNull { of(it.trim()) }.distinct().take(SLOTS)
            return picked + DEFAULT.filterNot { it in picked }.take(SLOTS - picked.size)
        }

        /** Puts [action] into slot [i]; if it already sits in another slot, the two swap places. */
        fun assign(slots: List<BubbleAction>, i: Int, action: BubbleAction): List<BubbleAction> {
            val m = slots.toMutableList()
            val j = m.indexOf(action)
            if (j >= 0) m[j] = m[i]
            m[i] = action
            return m
        }
    }
}
