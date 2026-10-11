package dev.livepipe

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** A streaming destination. [url] is the full RTMP(S) ingest URL; [key] is the secret stream key. */
data class Destination(
    val id: String,
    val name: String,
    val url: String,
    val key: String,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val videoBitrate: Int = 4_500_000,
    val audioBitrate: Int = 128_000,
    val sampleRate: Int = 48_000,
    val stereo: Boolean = true,
    val hevc: Boolean = false,              // false = H.264 (universal), true = H.265/HEVC (smaller, less compatible)
    val keyframeSeconds: Int = 2,           // I-frame interval; 2 s is the platform-recommended default
    val portrait: Boolean = false,          // false = landscape (rotation 0), true = portrait (rotation 90)
    val overlayUri: String = "",            // per-channel overlay image/GIF composited top-right; empty = none
    val delaySeconds: Int = 0,              // OBS-style stream delay; applied via streamClient.setDelay()
    val facecam: Boolean = false,           // front-camera PiP composited over the screen
    val facecamScale: Int = 30,             // facecam size as % of frame (10..60)
    val facecamCorner: Int = 2,             // legacy corner (0 TL, 1 TR, 2 BL, 3 BR); only seeds facecamX/Y
    // Scene layout (see Scene.kt). Screen: fill/fit + zoom + pan; layers: centre + size, all canvas fractions.
    val screenFit: Boolean = false,         // false = fill the canvas (no borders), true = show the whole screen
    val screenZoom: Float = 1f,             // extra zoom on top of fill/fit, 1..4
    val screenPanX: Float = 0f,             // -1..1 across the pannable range
    val screenPanY: Float = 0f,
    val overlayX: Float = 0.82f,            // overlay centre, 0..1 of canvas width
    val overlayY: Float = 0.18f,
    val overlayW: Float = 0.25f,            // overlay width as a fraction of canvas width (height follows the image)
    val facecamX: Float = 0.17f,            // facecam centre, 0..1
    val facecamY: Float = 0.81f,
    val pinned: Boolean = false,            // pinned channels list first, in their own order
    val ytSource: String = "",              // YouTube @handle, channel ID or live link for Super Chat alerts
    val alertX: Float = 0.5f,               // Super Chat card centre, 0..1
    val alertY: Float = 0.2f,
    val alertW: Float = 0.42f,              // card width as a fraction of canvas width (height follows the card)
    val chatX: Float = 0.2f,                // live chat box centre, 0..1
    val chatY: Float = 0.62f,
    val chatW: Float = 0.34f,               // chat box width as a fraction of canvas width
) {
    /** Full endpoint RootEncoder streams to. Guards the slash between url and key. */
    fun endpoint(): String = if (key.isBlank()) url else url.trimEnd('/') + "/" + key

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("key", key)
        put("w", width); put("h", height); put("fps", fps)
        put("vb", videoBitrate); put("ab", audioBitrate); put("sr", sampleRate); put("st", stereo)
        put("hevc", hevc); put("kf", keyframeSeconds); put("port", portrait); put("ovl", overlayUri)
        put("dly", delaySeconds); put("fc", facecam); put("fcs", facecamScale); put("fcc", facecamCorner)
        put("sf", screenFit); put("sz", screenZoom.toDouble()); put("spx", screenPanX.toDouble()); put("spy", screenPanY.toDouble())
        put("ox", overlayX.toDouble()); put("oy", overlayY.toDouble()); put("ow", overlayW.toDouble())
        put("fcx", facecamX.toDouble()); put("fcy", facecamY.toDouble()); put("pin", pinned)
        put("yts", ytSource); put("ax", alertX.toDouble()); put("ay", alertY.toDouble()); put("aw", alertW.toDouble())
        put("chx", chatX.toDouble()); put("chy", chatY.toDouble()); put("chw", chatW.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject) = Destination(
            id = o.getString("id"), name = o.getString("name"), url = o.getString("url"), key = o.optString("key"),
            width = o.optInt("w", 1280), height = o.optInt("h", 720), fps = o.optInt("fps", 30),
            videoBitrate = o.optInt("vb", 4_500_000), audioBitrate = o.optInt("ab", 128_000),
            sampleRate = o.optInt("sr", 48_000), stereo = o.optBoolean("st", true),
            hevc = o.optBoolean("hevc", false), keyframeSeconds = o.optInt("kf", 2),
            portrait = o.optBoolean("port", false), overlayUri = o.optString("ovl", ""),
            delaySeconds = o.optInt("dly", 0),
            facecam = o.optBoolean("fc", false), facecamScale = o.optInt("fcs", 30),
            facecamCorner = o.optInt("fcc", 2),
            screenFit = o.optBoolean("sf", false), screenZoom = o.optDouble("sz", 1.0).toFloat(),
            screenPanX = o.optDouble("spx", 0.0).toFloat(), screenPanY = o.optDouble("spy", 0.0).toFloat(),
            overlayX = o.optDouble("ox", 0.82).toFloat(), overlayY = o.optDouble("oy", 0.18).toFloat(),
            overlayW = o.optDouble("ow", 0.25).toFloat(),
            // Channels saved before the scene editor only had a corner: start the facecam there.
            facecamX = o.optDouble("fcx", cornerCentre(o.optInt("fcc", 2), o.optInt("fcs", 30), true)).toFloat(),
            facecamY = o.optDouble("fcy", cornerCentre(o.optInt("fcc", 2), o.optInt("fcs", 30), false)).toFloat(),
            pinned = o.optBoolean("pin", false),
            ytSource = o.optString("yts", ""),
            alertX = o.optDouble("ax", 0.5).toFloat(), alertY = o.optDouble("ay", 0.2).toFloat(),
            alertW = o.optDouble("aw", 0.42).toFloat(),
            chatX = o.optDouble("chx", 0.2).toFloat(), chatY = o.optDouble("chy", 0.62).toFloat(),
            chatW = o.optDouble("chw", 0.34).toFloat(),
        )

        private fun cornerCentre(corner: Int, scalePct: Int, x: Boolean): Double {
            val half = scalePct / 200.0
            val leftOrTop = if (x) corner == 0 || corner == 2 else corner <= 1
            return if (leftOrTop) half else 1 - half
        }
    }
}

/**
 * Bubble + recording preferences. Recording reuses the active channel's resolution/bitrate: a single
 * hardware encoder can't record at a different size than it streams, so there are no separate record
 * knobs (that would need a second encoder, which fights "lightest possible").
 */
data class Prefs(
    val bubbleEnabled: Boolean = true,
    val bubbleShowsTimer: Boolean = false,      // false = icon, true = elapsed timer
    val bubbleShowsFps: Boolean = false,        // shows the live frames per second instead (wins over the timer)
    val bubbleTransparency: Float = 0.2f,        // 0f opaque .. 0.95f near-invisible (control only; OS indicator stays)
    val bubbleImageUri: String = "",             // custom image; empty = default icon
    val recordWhileStreaming: Boolean = false,
    val audioSource: Int = AUDIO_MIX,            // mic / game / both
    val adaptiveBitrate: Boolean = true,         // lower bitrate automatically when the network can't keep up
    val micDeviceId: Int = -1,                   // AudioDeviceInfo.id for the chosen mic; -1 = system default/auto
    val micVolume: Float = 1f,                   // audio mixer, 0..1.5 (0 = muted)
    val gameVolume: Float = 1f,
    val bubbleX: Float = 0f,                     // bubble position as screen fractions (snapped to an edge)
    val bubbleY: Float = 0.3f,
    val superChat: Boolean = false,              // show YouTube Super Chats on stream
    val showChat: Boolean = false,               // show ordinary live chat messages in a box on stream
    val ytApiKey: String = "",                   // the streamer's own YouTube Data API key (encrypted store)
    val alertStyle: Int = 0,                     // SuperChatCard.STYLES index
    val alertSeconds: Int = 8,
    val alertShowTotal: Boolean = true,          // add the supporter's running total this stream
    val breakImageUri: String = "",              // break screen image (copy in app storage); empty = plain black
    val bubbleSlots: String = BubbleAction.DEFAULT_IDS,   // the 5 bubble controls, comma-joined action ids
    val bubbleSecure: Boolean = true,            // FLAG_SECURE on the bubble so capture doesn't show its content
    val bubbleEdgeHide: Boolean = true,          // tuck into a slim edge handle when idle; tap the handle to pop out
    val theme: String = "original",              // ui.Palettes id
    val liveTheme: Boolean = false,              // Live theme: scenes move (~10 fps, paused off-screen); off = a still frame
    val logoUri: String = "",                    // the user's own app logo (copy in app storage); empty = Angra oni
    val charging: Int = Charging.FAST,           // Charging mode; Fast = the phone's own stock charging
) {
    companion object {
        const val BUBBLE_MAX_TRANSPARENCY = 0.95f
        const val AUDIO_MIC = 0
        const val AUDIO_GAME = 1
        const val AUDIO_MIX = 2
    }
}

/** Secrets (stream keys) live in EncryptedSharedPreferences, backed by the Android Keystore. */
class Store(context: Context) {
    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "livepipe", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var destinations: List<Destination>
        get() {
            val raw = prefs.getString("destinations", null) ?: return emptyList()
            val arr = JSONArray(raw)
            return (0 until arr.length()).map { Destination.fromJson(arr.getJSONObject(it)) }
        }
        set(value) {
            val arr = JSONArray(); value.forEach { arr.put(it.toJson()) }
            prefs.edit().putString("destinations", arr.toString()).apply()
        }

    var activeId: String?
        get() = prefs.getString("activeId", null)
        set(value) = prefs.edit().putString("activeId", value).apply()

    fun active(): Destination? = destinations.firstOrNull { it.id == activeId } ?: destinations.firstOrNull()

    var prefsBundle: Prefs
        get() {
            val o = JSONObject(prefs.getString("prefs", "{}") ?: "{}")
            return Prefs(
                bubbleEnabled = o.optBoolean("be", true),
                bubbleShowsTimer = o.optBoolean("bt", false), bubbleShowsFps = o.optBoolean("bfps", false),
                bubbleTransparency = o.optDouble("btr", 0.2).toFloat(),
                bubbleImageUri = o.optString("bimg", ""),
                recordWhileStreaming = o.optBoolean("rws", false),
                audioSource = o.optInt("as", Prefs.AUDIO_MIX),
                adaptiveBitrate = o.optBoolean("ab", true),
                micDeviceId = o.optInt("mic", -1),
                micVolume = o.optDouble("mv", 1.0).toFloat(), gameVolume = o.optDouble("gv", 1.0).toFloat(),
                bubbleX = o.optDouble("bx", 0.0).toFloat(), bubbleY = o.optDouble("by", 0.3).toFloat(),
                bubbleEdgeHide = o.optBoolean("beh", true),
                logoUri = o.optString("logo", ""),
                breakImageUri = o.optString("brk", ""),
                superChat = o.optBoolean("sc", false), showChat = o.optBoolean("chat", false), ytApiKey = o.optString("ytk", ""),
                alertStyle = o.optInt("ast", 0), alertSeconds = o.optInt("asec", 8), alertShowTotal = o.optBoolean("atot", true),
                bubbleSlots = o.optString("bsl", BubbleAction.DEFAULT_IDS),
                bubbleSecure = o.optBoolean("bsec", true),
                theme = o.optString("th", "original"), liveTheme = o.optBoolean("lt", false),
                charging = o.optInt("chg", Charging.FAST),
            )
        }
        set(p) {
            val o = JSONObject().apply {
                put("be", p.bubbleEnabled); put("bt", p.bubbleShowsTimer); put("bfps", p.bubbleShowsFps); put("btr", p.bubbleTransparency.toDouble())
                put("bimg", p.bubbleImageUri); put("rws", p.recordWhileStreaming)
                put("as", p.audioSource); put("ab", p.adaptiveBitrate)
                put("mic", p.micDeviceId); put("mv", p.micVolume.toDouble()); put("gv", p.gameVolume.toDouble())
                put("bx", p.bubbleX.toDouble()); put("by", p.bubbleY.toDouble()); put("beh", p.bubbleEdgeHide); put("logo", p.logoUri); put("brk", p.breakImageUri)
                put("bsl", p.bubbleSlots); put("bsec", p.bubbleSecure)
                put("sc", p.superChat); put("chat", p.showChat); put("ytk", p.ytApiKey); put("ast", p.alertStyle); put("asec", p.alertSeconds)
                put("atot", p.alertShowTotal); put("th", p.theme); put("lt", p.liveTheme); put("chg", p.charging)
            }
            prefs.edit().putString("prefs", o.toString()).apply()
        }
}
