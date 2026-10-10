package dev.livepipe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Currency

/** One paid message: a Super Chat or Super Sticker. [micros] = amount × 1,000,000 in [currency]. */
data class SuperChat(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val amount: String,                 // as YouTube shows it, e.g. "$10.00", "₹500.00"
    val micros: Long,
    val currency: String,
    val message: String,
    val tier: Int,                      // YouTube colour tier, 1 (lowest) .. 7
    val sticker: Boolean = false,
    val total: String? = null,          // this supporter's total this stream, when they've given more than once
    val avatar: Bitmap? = null,
)

/**
 * Watches a YouTube live chat for Super Chats and Super Stickers through the official YouTube Data API v3,
 * using the streamer's own API key. [source] = a live video link/ID, a channel ID (UC…) or an @handle.
 * Runs on its own thread; [onEvent] and [onStatus] are called from it.
 * ponytail: polls at YouTube's own interval (at least 8 s), so the default 10k/day quota covers roughly 2–4 h
 * of live chat; longer streams need a quota increase on the user's Google Cloud project.
 */
class YouTubeChat(
    private val apiKey: String,
    private val source: String,
    private val onEvent: (SuperChat) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    @Volatile private var running = true
    private val thread = Thread({ loop() }, "youtube-chat").apply { isDaemon = true; start() }

    fun stop() { running = false; thread.interrupt() }

    private fun loop() {
        var chatId: String? = null
        var page: String? = null
        var first = true
        val totals = HashMap<String, Long>()          // author channel + currency -> micros this stream
        while (running) {
            try {
                if (chatId == null) {
                    onStatus("Looking for your YouTube live stream…")
                    chatId = liveChatId()
                    if (chatId == null) { onStatus("Not live on YouTube yet. Checking again shortly."); Thread.sleep(45_000); continue }
                    onStatus("Connected. Watching live chat for Super Chats.")
                    page = null; first = true
                }
                val r = get("liveChat/messages?part=snippet,authorDetails&maxResults=200&liveChatId=${enc(chatId)}" +
                    (page?.let { "&pageToken=${enc(it)}" } ?: ""))
                page = r.optString("nextPageToken").ifBlank { null }
                // The first page is chat history from before we connected: skip it so old Super Chats don't replay.
                if (!first) {
                    val items = r.optJSONArray("items")
                    for (i in 0 until (items?.length() ?: 0)) parse(items!!.getJSONObject(i), totals)?.let(onEvent)
                }
                first = false
                Thread.sleep(maxOf(r.optLong("pollingIntervalMillis", 10_000), 8_000))
            } catch (_: InterruptedException) {
                return
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                onStatus("YouTube: $msg")
                if ("ended" in msg || "not found" in msg.lowercase()) chatId = null   // stream ended or restarted
                runCatching { Thread.sleep(20_000) }.onFailure { return }
            }
        }
    }

    private fun parse(item: JSONObject, totals: HashMap<String, Long>): SuperChat? {
        val sn = item.optJSONObject("snippet") ?: return null
        val sticker = when (sn.optString("type")) {
            "superChatEvent" -> false
            "superStickerEvent" -> true
            else -> return null
        }
        val det = sn.optJSONObject(if (sticker) "superStickerDetails" else "superChatDetails") ?: return null
        val author = item.optJSONObject("authorDetails")
        val micros = det.optString("amountMicros").toLongOrNull() ?: 0L
        val currency = det.optString("currency")
        val key = author?.optString("channelId").orEmpty() + currency
        val sum = (totals[key] ?: 0L) + micros
        totals[key] = sum
        val avatarUrl = author?.optString("profileImageUrl").orEmpty()
        return SuperChat(
            id = item.optString("id"),
            name = author?.optString("displayName")?.ifBlank { null } ?: "Someone",
            avatarUrl = avatarUrl,
            amount = det.optString("amountDisplayString"),
            micros = micros, currency = currency,
            message = if (sticker) det.optJSONObject("superStickerMetadata")?.optString("altText").orEmpty().ifBlank { "Super Sticker" }
                else det.optString("userComment"),
            tier = det.optInt("tier", 1),
            sticker = sticker,
            total = if (sum > micros) money(sum, currency) else null,
            avatar = download(avatarUrl),
        )
    }

    private fun liveChatId(): String? {
        val src = parse(source) ?: throw IOException("Enter your @handle, channel ID or live link")
        val videoId = src.videoId ?: run {
            val channel = src.channelId ?: get("channels?part=id&forHandle=${enc("@" + src.handle)}")
                .optJSONArray("items")?.optJSONObject(0)?.optString("id") ?: throw IOException("Channel not found")
            get("search?part=id&type=video&eventType=live&maxResults=1&channelId=${enc(channel)}")
                .optJSONArray("items")?.optJSONObject(0)?.optJSONObject("id")?.optString("videoId") ?: return null
        }
        return get("videos?part=liveStreamingDetails&id=${enc(videoId)}").optJSONArray("items")?.optJSONObject(0)
            ?.optJSONObject("liveStreamingDetails")?.optString("activeLiveChatId")?.ifBlank { null }
    }

    /** API key goes in a header, not the URL, so it never lands in logs or proxies' URL records. */
    private fun get(path: String): JSONObject {
        val c = URL("https://www.googleapis.com/youtube/v3/$path").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("X-Goog-Api-Key", apiKey)
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException(runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull() ?: "HTTP $code")
            }
            return JSONObject(body)
        } finally { c.disconnect() }
    }

    private fun download(url: String): Bitmap? = if (url.isBlank()) null else runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5_000; c.readTimeout = 5_000
        try { c.inputStream.use { BitmapFactory.decodeStream(it) } } finally { c.disconnect() }
    }.getOrNull()

    data class Source(val videoId: String? = null, val channelId: String? = null, val handle: String? = null)

    companion object {
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        /** What the streamer typed: a live/video link or ID, a channel ID, or an @handle (bare or in a URL). */
        fun parse(s: String): Source? {
            val t = s.trim()
            Regex("(?:v=|youtu\\.be/|/live/|/shorts/)([A-Za-z0-9_-]{11})").find(t)?.let { return Source(videoId = it.groupValues[1]) }
            Regex("(UC[A-Za-z0-9_-]{22})").find(t)?.let { return Source(channelId = it.groupValues[1]) }
            Regex("@([A-Za-z0-9._-]{3,30})").find(t)?.let { return Source(handle = it.groupValues[1]) }
            if (Regex("^[A-Za-z0-9_-]{11}$").matches(t)) return Source(videoId = t)
            return null
        }

        fun money(micros: Long, currency: String): String = runCatching {
            NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency) }.format(micros / 1_000_000.0)
        }.getOrDefault("%.2f %s".format(micros / 1_000_000.0, currency))
    }
}

/**
 * Draws the on-stream Super Chat card. The same bitmap is used for the stream and for every preview in the
 * app, so what the streamer picks is exactly what viewers get. No app name or branding on any style.
 */
object SuperChatCard {
    val STYLES = listOf("Ember", "Classic", "Minimal", "Neon")

    // YouTube's Super Chat colours per tier: (deep, bright). Tier 1 is the lowest amount.
    private val TIERS = listOf(
        0xFF1565C0 to 0xFF1E88E5, 0xFF00B8D4 to 0xFF00E5FF, 0xFF00BFA5 to 0xFF1DE9B6, 0xFFFFB300 to 0xFFFFCA28,
        0xFFE65100 to 0xFFF57C00, 0xFFC2185B to 0xFFE91E63, 0xFFD00000 to 0xFFE62117,
    )
    private const val MARGIN = 44                     // room for the glow around the card

    fun deep(tier: Int) = TIERS[(tier - 1).coerceIn(0, 6)].first.toInt()
    fun bright(tier: Int) = TIERS[(tier - 1).coerceIn(0, 6)].second.toInt()
    private fun lightTier(tier: Int) = tier in 2..4    // cyan, green, yellow read better with dark text

    fun sample(tier: Int = 4) = SuperChat(
        "sample", "Alex Rivera", "", when (tier) { 1 -> "$1.00"; 2 -> "$2.00"; 3 -> "$5.00"; 4 -> "$10.00"; 5 -> "$20.00"; 6 -> "$50.00"; else -> "$100.00" },
        0, "USD", "Clutch play!! Keep the streams coming", tier, total = "$25.00",
    )

    fun render(e: SuperChat, style: Int, showTotal: Boolean): Bitmap = when (style) {
        1 -> classic(e, showTotal)
        2 -> minimal(e)
        3 -> neon(e, showTotal)
        else -> ember(e, showTotal)
    }

    /* ---------- Ember: dark glass, tier-coloured glow and sheen, avatar ring, amount pill ---------- */
    private fun ember(e: SuperChat, showTotal: Boolean): Bitmap {
        val w = 960; val pad = 36; val r = 48f
        val name = paint(40f, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL))
        val label = paint(22f, bright(e.tier), Typeface.DEFAULT_BOLD).apply { letterSpacing = 0.14f }
        val msg = text(e.message, paint(36f, 0xEBFFFFFF.toInt()), w - 2 * pad, 4)
        val totalLine = e.total?.takeIf { showTotal }?.let { "Total this stream  ·  $it" }
        val h = pad + (2 * r).toInt() + (msg?.let { 26 + it.height } ?: 0) + (totalLine?.let { 54 } ?: 0) + pad
        return card(w, h) { c, box ->
            val shape = Path().apply { addRoundRect(box, 40f, 40f, Path.Direction.CW) }
            c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, box.top, 0f, box.bottom, 0xF5202024.toInt(), 0xF50C0C0E.toInt(), Shader.TileMode.CLAMP)
                setShadowLayer(40f, 0f, 10f, (bright(e.tier) and 0x00FFFFFF) or 0x8C000000.toInt())
            })
            c.save(); c.clipPath(shape)
            c.drawRect(box.left, box.top, box.right, box.top + 9, Paint().apply {
                shader = LinearGradient(box.left, 0f, box.right, 0f, deep(e.tier), bright(e.tier), Shader.TileMode.CLAMP)
            })
            c.restore()
            c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x26FFFFFF })
            val left = box.left + pad; val top = box.top + pad
            avatar(c, e, left + r, top + r, r, bright(e.tier))
            val tx = left + 2 * r + 26
            val tag = if (e.sticker) "SUPER STICKER" else "SUPER CHAT"
            label.textAlign = Paint.Align.RIGHT
            c.drawText(tag, box.right - pad, top + 26, label)
            c.drawText(ellipsize(e.name, name, box.right - pad - tx - label.measureText(tag) - 20), tx, top + 40, name)
            pill(c, e.amount, tx, top + 54, 46f, bright(e.tier), if (lightTier(e.tier)) 0xE6000000.toInt() else Color.WHITE)
            var y = top + 2 * r
            msg?.let { y += 26; c.save(); c.translate(left, y); it.draw(c); c.restore(); y += it.height }
            totalLine?.let {
                y += 22
                c.drawLine(left, y, box.right - pad, y, Paint().apply { color = 0x1FFFFFFF; strokeWidth = 2f })
                c.drawText(it, left, y + 38, paint(26f, 0xA6FFFFFF.toInt(), Typeface.create("sans-serif-medium", Typeface.NORMAL)))
            }
        }
    }

    /* ---------- Classic: YouTube's own look, header band + lighter body in the tier colours ---------- */
    private fun classic(e: SuperChat, showTotal: Boolean): Bitmap {
        val w = 860; val pad = 28; val r = 40f
        val dark = lightTier(e.tier)
        val fg = if (dark) 0xDE000000.toInt() else Color.WHITE
        val fgSoft = if (dark) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
        val body = listOfNotNull(e.message.ifBlank { null }, e.total?.takeIf { showTotal }?.let { "Total this stream: $it" }).joinToString("\n")
        val msg = text(body, paint(34f, fg), w - 2 * pad, 5)
        val head = pad * 2 + (2 * r).toInt()
        val h = head + (msg?.let { it.height + 2 * pad } ?: 0)
        return card(w, h) { c, box ->
            val shape = Path().apply { addRoundRect(box, 20f, 20f, Path.Direction.CW) }
            c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bright(e.tier); setShadowLayer(24f, 0f, 8f, 0x66000000) })
            c.save(); c.clipPath(shape)
            c.drawRect(box.left, box.top, box.right, box.top + head, Paint().apply { color = deep(e.tier) })
            c.restore()
            avatar(c, e, box.left + pad + r, box.top + pad + r, r, 0)
            val tx = box.left + pad + 2 * r + 22
            c.drawText(ellipsize(e.name, paint(32f, fgSoft), box.right - pad - tx), tx, box.top + pad + 32, paint(32f, fgSoft))
            c.drawText(e.amount, tx, box.top + pad + 76, paint(36f, fg, Typeface.DEFAULT_BOLD))
            msg?.let { c.save(); c.translate(box.left + pad, box.top + head + pad); it.draw(c); c.restore() }
        }
    }

    /* ---------- Minimal: one slim line, amount badge + name + message ---------- */
    private fun minimal(e: SuperChat): Bitmap {
        val h = 104; val r = 30f
        val amount = paint(34f, if (lightTier(e.tier)) 0xE6000000.toInt() else Color.WHITE, Typeface.DEFAULT_BOLD)
        val nameP = paint(32f, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL))
        val msgP = paint(32f, 0xCCFFFFFF.toInt())
        val badgeW = amount.measureText(e.amount) + 40
        val maxText = 640f
        val nameW = minOf(nameP.measureText(e.name), 260f)
        val msgW = minOf(msgP.measureText(e.message), maxText - nameW)
        val w = (22 + 2 * r + 18 + badgeW + 20 + nameW + (if (e.message.isNotBlank()) 16 + msgW else 0f) + 36).toInt()
        return card(w, h) { c, box ->
            c.drawRoundRect(box, h / 2f, h / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xD90A0A0C.toInt(); setShadowLayer(26f, 0f, 6f, 0x80000000.toInt())
            })
            val cy = box.centerY()
            var x = box.left + 22
            avatar(c, e, x + r, cy, r, bright(e.tier)); x += 2 * r + 18
            pill(c, e.amount, x, cy - 26, 52f, bright(e.tier), amount.color, amount); x += badgeW + 20
            c.drawText(ellipsize(e.name, nameP, nameW), x, cy + 11, nameP); x += nameW + 16
            if (e.message.isNotBlank()) c.drawText(ellipsize(e.message, msgP, msgW), x, cy + 11, msgP)
        }
    }

    /* ---------- Neon: black card, glowing tier outline, the amount front and centre ---------- */
    private fun neon(e: SuperChat, showTotal: Boolean): Bitmap {
        val w = 880; val pad = 40
        val glow = bright(e.tier)
        val msg = text(e.message, paint(34f, 0xE6FFFFFF.toInt()), w - 2 * pad, 3, Layout.Alignment.ALIGN_CENTER)
        val totalLine = e.total?.takeIf { showTotal }?.let { "Total this stream  $it" }
        val h = pad + 96 + 52 + (msg?.let { 20 + it.height } ?: 0) + (totalLine?.let { 48 } ?: 0) + pad
        return card(w, h) { c, box ->
            c.drawRoundRect(box, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF505050A.toInt() })
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 5f; color = glow; setShadowLayer(26f, 0f, 0f, glow)
            }
            c.drawRoundRect(box, 28f, 28f, stroke)
            c.drawRoundRect(box, 28f, 28f, stroke)            // twice: a brighter, tighter glow
            val cx = box.centerX()
            val amt = paint(84f, glow, Typeface.DEFAULT_BOLD).apply { textAlign = Paint.Align.CENTER; setShadowLayer(22f, 0f, 0f, glow) }
            c.drawText(e.amount, cx, box.top + pad + 78, amt)
            val nm = paint(34f, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL)).apply { textAlign = Paint.Align.CENTER }
            c.drawText(ellipsize(e.name, nm, w - 2f * pad), cx, box.top + pad + 96 + 40, nm)
            var y = box.top + pad + 96 + 52
            msg?.let { y += 20; c.save(); c.translate(box.left + pad, y); it.draw(c); c.restore(); y += it.height }
            totalLine?.let {
                c.drawText(it, cx, y + 40, paint(26f, 0x99FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER })
            }
        }
    }

    /* ---------- drawing helpers ---------- */

    private fun card(w: Int, h: Int, draw: (Canvas, RectF) -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(w + 2 * MARGIN, h + 2 * MARGIN, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), RectF(MARGIN.toFloat(), MARGIN.toFloat(), (MARGIN + w).toFloat(), (MARGIN + h).toFloat()))
        return bmp
    }

    private fun paint(size: Float, color: Int, face: Typeface = Typeface.DEFAULT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }

    private fun text(s: String, p: TextPaint, w: Int, maxLines: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout? =
        if (s.isBlank()) null else StaticLayout.Builder.obtain(s, 0, s.length, p, w).setAlignment(align)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()

    private fun ellipsize(s: String, p: TextPaint, w: Float) = TextUtils.ellipsize(s, p, w.coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()

    private fun pill(c: Canvas, s: String, x: Float, y: Float, h: Float, fill: Int, fg: Int, p: TextPaint = paint(32f, fg, Typeface.DEFAULT_BOLD)) {
        p.color = fg
        val w = p.measureText(s) + 40
        c.drawRoundRect(RectF(x, y, x + w, y + h), h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        c.drawText(s, x + 20, y + h / 2 - (p.ascent() + p.descent()) / 2, p)
    }

    /** Round avatar (or the first letter on the tier colour), with an optional ring. */
    private fun avatar(c: Canvas, e: SuperChat, cx: Float, cy: Float, r: Float, ring: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bmp = e.avatar
        if (bmp != null) {
            val s = 2 * r / minOf(bmp.width, bmp.height)
            p.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(s, s); postTranslate(cx - bmp.width * s / 2, cy - bmp.height * s / 2) })
            }
            c.drawCircle(cx, cy, r, p)
        } else {
            p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, bright(e.tier), deep(e.tier), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            val t = paint(r, Color.WHITE, Typeface.DEFAULT_BOLD).apply { textAlign = Paint.Align.CENTER }
            c.drawText(e.name.take(1).uppercase(), cx, cy - (t.ascent() + t.descent()) / 2, t)
        }
        if (ring != 0) c.drawCircle(cx, cy, r + 3, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = ring })
    }
}
