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
    val facecamCorner: Int = 2,             // 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right
) {
    /** Full endpoint RootEncoder streams to. Guards the slash between url and key. */
    fun endpoint(): String = if (key.isBlank()) url else url.trimEnd('/') + "/" + key

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("key", key)
        put("w", width); put("h", height); put("fps", fps)
        put("vb", videoBitrate); put("ab", audioBitrate); put("sr", sampleRate); put("st", stereo)
        put("hevc", hevc); put("kf", keyframeSeconds); put("port", portrait); put("ovl", overlayUri)
        put("dly", delaySeconds); put("fc", facecam); put("fcs", facecamScale); put("fcc", facecamCorner)
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
        )
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
    val bubbleTransparency: Float = 0.2f,        // 0f opaque .. 0.95f near-invisible (control only; OS indicator stays)
    val bubbleImageUri: String = "",             // custom image; empty = default icon
    val recordWhileStreaming: Boolean = false,
    val audioSource: Int = AUDIO_MIX,            // mic / game / both
    val adaptiveBitrate: Boolean = true,         // lower bitrate automatically when the network can't keep up
    val micDeviceId: Int = -1,                   // AudioDeviceInfo.id for the chosen mic; -1 = system default/auto
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
                bubbleShowsTimer = o.optBoolean("bt", false),
                bubbleTransparency = o.optDouble("btr", 0.2).toFloat(),
                bubbleImageUri = o.optString("bimg", ""),
                recordWhileStreaming = o.optBoolean("rws", false),
                audioSource = o.optInt("as", Prefs.AUDIO_MIX),
                adaptiveBitrate = o.optBoolean("ab", true),
                micDeviceId = o.optInt("mic", -1),
            )
        }
        set(p) {
            val o = JSONObject().apply {
                put("be", p.bubbleEnabled); put("bt", p.bubbleShowsTimer); put("btr", p.bubbleTransparency.toDouble())
                put("bimg", p.bubbleImageUri); put("rws", p.recordWhileStreaming)
                put("as", p.audioSource); put("ab", p.adaptiveBitrate)
                put("mic", p.micDeviceId)
            }
            prefs.edit().putString("prefs", o.toString()).apply()
        }
}
