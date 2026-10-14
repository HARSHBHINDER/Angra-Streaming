package dev.livepipe.ui

import dev.livepipe.Charging.Engine as E
import android.content.Intent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.livepipe.Destination
import dev.livepipe.Prefs
import dev.livepipe.R
import dev.livepipe.StreamService
import java.util.UUID
import kotlinx.coroutines.delay

/* ---------- reusable Apple-style building blocks ---------- */

/** iOS press feedback: dim instantly on touch-down, fade back on release. No ripple. */
fun Modifier.iosTap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.35f else 1f, tween(if (pressed) 0 else 220), label = "tap")
    graphicsLayer { this.alpha = alpha }.clickable(src, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun SectionHeader(text: String) = Text(
    text.uppercase(),
    color = Ios.secondaryLabel,
    fontSize = 13.sp, letterSpacing = 0.sp,
    modifier = Modifier.padding(start = 32.dp, top = 28.dp, bottom = 7.dp),
)

@Composable
fun Footer(text: String) = Text(
    text, color = Ios.secondaryLabel, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp,
    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp),
)

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) = Row(
    modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically,
) {
    LocalTheme.current.p.look.seal?.let { Seal(it, filled = !isDark); Spacer(Modifier.size(10.dp)) }
    ThemedTitle(text, 34.sp)
}

@Composable
fun InsetCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().themed(), content = content)

/** Hairline row divider, inset from the leading edge like UITableView. */
@Composable
fun Hairline() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Ios.separator))

@Composable
fun RowItem(label: String, trailing: @Composable () -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, color = MaterialTheme.colorScheme.onSurface)
    trailing()
}

/** Grouped-table text field: label on the left, borderless input on the right. */
@Composable
fun FieldRow(
    label: String, value: String, onChange: (String) -> Unit, placeholder: String = "",
    secure: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
) = Row(
    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, modifier = Modifier.width(92.dp))
    Box(Modifier.weight(1f)) {
        if (value.isEmpty()) Text(placeholder, color = Ios.secondaryLabel.copy(alpha = 0.5f), maxLines = 1)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(Accent),
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** iOS segmented control: gray track, raised thumb that slides between segments. */
@Composable
fun <T> Chips(
    options: List<T>, selected: T, label: (T) -> String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    onSelect: (T) -> Unit,
) {
    val idx = options.indexOf(selected)
    val sh = LocalTheme.current.p.look.shape(Part.SMALL)
    BoxWithConstraints(modifier.height(34.dp).background(Ios.segmentTrack, sh).padding(2.dp)) {
        val w = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(w * idx.coerceAtLeast(0), spring(dampingRatio = 1f, stiffness = 500f), label = "seg")
        if (idx >= 0) Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .shadow(1.dp, sh).background(Ios.segmentThumb, sh)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, opt ->
                Box(Modifier.weight(1f).fillMaxHeight().iosTap { onSelect(opt) }, contentAlignment = Alignment.Center) {
                    Text(
                        label(opt), fontSize = 13.sp, letterSpacing = (-0.1).sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
                        fontWeight = if (i == idx) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** iOS switch: 51x31 capsule, green when on, white thumb that springs across. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.8f, stiffness = 600f), label = "sw")
    val track by animateColorAsState(if (checked) Accent else Ios.switchOff, label = "swc")
    Box(
        Modifier.size(51.dp, 31.dp).background(track, AppleShapes.capsule)
            .toggleable(checked, remember { MutableInteractionSource() }, null, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Box(Modifier.offset(x = x, y = 2.dp).size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** iOS slider: 4dp continuous track (blue fill), round white thumb with soft shadow. No ticks, no gap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0,
    onLive: ((Float) -> Unit)? = null,                 // cheap live effect while dragging (volume, bubble alpha)
) {
    // Dragging only moves local state; the value is saved once, on release.
    var v by remember(value) { mutableFloatStateOf(value) }
    IosSliderBar(v, { v = it; onLive?.invoke(it) }, { if (v != value) onValueChange(v) }, modifier, valueRange, steps)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosSliderBar(
    value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier,
    valueRange: ClosedFloatingPointRange<Float>, steps: Int,
) = Slider(
    value, onChange, modifier.padding(horizontal = 16.dp), valueRange = valueRange, steps = steps, onValueChangeFinished = onDone,
    thumb = { Box(Modifier.size(28.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape)) },
    track = { st ->
        val span = st.valueRange.endInclusive - st.valueRange.start
        val f = if (span == 0f) 0f else ((st.value - st.valueRange.start) / span).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(4.dp).background(Ios.switchOff, CircleShape)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Accent, CircleShape))
        }
    },
)

/** iOS checkmark list (Settings-style picker) for choices too long or many for a segmented control. */
@Composable
fun <T> CheckList(
    options: List<T>, selected: T, label: (T) -> String, emptyHint: String? = null, onSelect: (T) -> Unit,
) = InsetCard {
    options.forEachIndexed { i, opt ->
        if (i > 0) Hairline()
        Row(Modifier.fillMaxWidth().iosTap { onSelect(opt) }.heightIn(min = 44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label(opt), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (opt == selected) Text("✓", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    // Placeholder slot, e.g. "connect a mic": shows the list is waiting for more, not broken.
    if (emptyHint != null) {
        Hairline()
        Text(emptyHint, color = Ios.secondaryLabel, modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 11.dp))
    }
}

/* ---------- shared option lists ---------- */

data class Res(val w: Int, val h: Int) { override fun toString() = "${h}p" }
val RESOLUTIONS = listOf(Res(640, 360), Res(854, 480), Res(1280, 720), Res(1920, 1080))
val FPS = listOf(30, 60)
val AUDIO_BITRATES = listOf(96_000, 128_000, 160_000, 192_000, 256_000)
val SAMPLE_RATES = listOf(44_100, 48_000)

/**
 * What this phone can really stream: sizes and frame rates its hardware encoder accepts, never bigger than
 * the screen (capture is not upscaled; no enhancers). Older phones simply get fewer options.
 */
class DeviceCaps(ctx: android.content.Context) {
    private val encoders = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
    private val screenShort = run {
        val wm = ctx.getSystemService(android.view.WindowManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { minOf(it.width(), it.height()) }
        else android.graphics.Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }.let { minOf(it.x, it.y) }
    }

    private fun hardware(c: android.media.MediaCodecInfo) = if (android.os.Build.VERSION.SDK_INT >= 29) c.isHardwareAccelerated
        else !c.name.startsWith("OMX.google.") && !c.name.startsWith("c2.android.")

    // H.264 falls back to a software encoder if a phone has no hardware one; HEVC must be hardware.
    private fun video(hevc: Boolean): List<android.media.MediaCodecInfo.VideoCapabilities> {
        val mime = if (hevc) "video/hevc" else "video/avc"
        val all = encoders.filter { c -> c.supportedTypes.any { it.equals(mime, true) } }
        return all.filter(::hardware).ifEmpty { if (hevc) emptyList() else all }.map { it.getCapabilitiesForType(mime).videoCapabilities }
    }
    private val avc = video(false)
    private val hevcCaps = video(true)

    val hevc get() = hevcCaps.isNotEmpty()

    fun supports(r: Res, fps: Int, hevc: Boolean, portrait: Boolean): Boolean {
        val (w, h) = if (portrait) r.h to r.w else r.w to r.h
        // Encoders pad to their block size (1080 is coded as 1088), so check the aligned size.
        fun up(v: Int, a: Int) = (v + a - 1) / a * a
        return r.h <= screenShort && (if (hevc) hevcCaps else avc).any {
            it.areSizeAndRateSupported(up(w, it.widthAlignment), up(h, it.heightAlignment), fps.toDouble())
        }
    }

    fun maxBitrate(hevc: Boolean) = (if (hevc) hevcCaps else avc).maxOfOrNull { it.bitrateRange.upper } ?: 12_000_000
}

/** Platform presets — prefill the ingest URL; key is always pasted by the user. */
data class Platform(val name: String, val url: String)
val PLATFORMS = listOf(
    Platform("YouTube", "rtmps://a.rtmp.youtube.com/live2"),
    Platform("Twitch", "rtmp://live.twitch.tv/app"),
    Platform("Facebook", "rtmps://live-api-s.facebook.com:443/rtmp/"),
    Platform("Kick", "rtmps://fa723fc1b171.global-contribute.live-video.net/app"),   // Kick's ingest; the key picks the channel
    Platform("Custom", ""),
)

/* ---------- app shell ---------- */

@Composable
fun App(
    initialDestinations: List<Destination>,
    initialActiveId: String?,
    prefs: Prefs,                              // single source of truth: owned by the Activity
    onPrefs: (Prefs) -> Unit,
    onPersistDestinations: (List<Destination>, String?) -> Unit,
    onToggleStream: () -> Unit,
    onPickBubbleImage: () -> Unit,
    micDevices: List<Pair<Int, String>>,
    onPreviewBubble: (Boolean) -> Unit = {},
    onPickLogo: () -> Unit = {},
    onPinIcon: () -> Unit = {},
    onResetLogo: () -> Unit = {},
    openSuperChat: Boolean = false,                 // home-screen widget asked for the Super Chat screen
    onSuperChatOpened: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(0) }
    var sceneFor by remember { mutableStateOf<Destination?>(null) }   // scene editor opened from the Stream tab
    var sceneAlerts by remember { mutableStateOf(false) }
    var sceneChat by remember { mutableStateOf(false) }               // ...opened on the Super Chat alert layer
    var superChat by remember { mutableStateOf(false) }
    LaunchedEffect(openSuperChat) { if (openSuperChat) { tab = 0; superChat = true; onSuperChatOpened() } }
    var destinations by remember { mutableStateOf(initialDestinations) }
    var activeId by remember { mutableStateOf(initialActiveId) }

    fun saveDest(list: List<Destination>, active: String?) {
        destinations = list; activeId = active; onPersistDestinations(list, active)
    }

    // Liquid Glass themes: the whole screen is recorded once into a layer the floating tab bar refracts.
    val floating = LocalTheme.current.p.look.floatingBar
    val screenLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().then(if (floating) Modifier.drawWithContent {
        screenLayer.record { this@drawWithContent.drawContent() }
        drawLayer(screenLayer)
    } else Modifier)) {
    ThemeBackdrop()
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = { if (!floating) TabBar(tab, null) { tab = it } },
    ) { pad ->
        // Tablets: the content keeps a readable column, centred; the theme still fills the screen behind it.
        Box(Modifier.padding(pad).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            val editingScene = sceneFor
            if (editingScene != null) {
                SceneEditor(editingScene, prefs, onPrefs, onCancel = { sceneFor = null; sceneAlerts = false; sceneChat = false }, onDone = { d ->
                    saveDest(destinations.map { if (it.id == d.id) d else it }, activeId)
                    StreamService.updateScene(d)          // live: layers move on air
                    sceneFor = null; sceneAlerts = false; sceneChat = false
                }, alerts = sceneAlerts, chat = sceneChat)
            } else if (superChat) {
                val active = destinations.firstOrNull { it.id == activeId } ?: destinations.firstOrNull()
                SuperChatScreen(active, prefs, onPrefs,
                    onDest = { d -> saveDest(destinations.map { if (it.id == d.id) d else it }, activeId) },
                    onPlace = { sceneAlerts = true; sceneFor = active },
                    onPlaceChat = { sceneChat = true; sceneFor = active },
                    onClose = { superChat = false })
            } else when (tab) {
                0 -> StreamScreen(destinations, activeId, { saveDest(destinations, it) }, { tab = 1 },
                    prefs, onToggleStream, onPrefs,
                    onScene = { sceneFor = destinations.firstOrNull { it.id == activeId } ?: destinations.firstOrNull() },
                    onSuperChat = { superChat = true }, onResetLogo = onResetLogo,
                    onEditActive = { d -> saveDest(destinations.map { if (it.id == d.id) d else it }, activeId) })
                1 -> DestinationsScreen(destinations, activeId, ::saveDest, prefs, onPrefs)
                else -> SettingsScreen(prefs, onPickBubbleImage, micDevices, onPrefs, onPreviewBubble, onPickLogo, onPinIcon, onResetLogo)
            }
        }
    }
    }
    // Floats over the content (which scrolls beneath it), so the glass has real content to bend.
    if (floating) Box(Modifier.align(Alignment.BottomCenter)) { TabBar(tab, screenLayer) { tab = it } }
    }
}

/**
 * Tab bar in the theme: a floating glass capsule for the glass themes (iOS 26), otherwise a bar whose top edge
 * carries the theme's line (Tron's lit cyan, Cyberpunk's yellow…).
 */
@Composable
private fun TabBar(tab: Int, screen: androidx.compose.ui.graphics.layer.GraphicsLayer?, onTab: (Int) -> Unit) {
    val look = LocalTheme.current.p.look
    val items = listOf(Icons.Filled.PlayArrow to "Stream", Icons.Filled.List to "Channels", Icons.Filled.Settings to "Settings")
    val row = @Composable {
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            items.forEachIndexed { i, (icon, name) ->
                val on = tab == i
                Column(Modifier.weight(1f).fillMaxHeight().iosTap { onTab(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icon, null, tint = if (on) Accent else Ios.secondaryLabel)
                    Text(if (look.titleCaps) name.uppercase() else name, fontSize = 10.sp, letterSpacing = if (look.titleCaps) 1.sp else 0.sp,
                        color = if (on) Accent else Ios.secondaryLabel, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
    if (look.floatingBar) Box(Modifier.padding(horizontal = 28.dp, vertical = 10.dp).widthIn(max = 520.dp).fillMaxWidth().liquidGlass(screen)) { row() }
    else Column {
        val lit = look.edge == Edge.PULSE || look.edge == Edge.NEON || look.edge == Edge.GLITCH || look.edge == Edge.CHROMA
        Box(Modifier.fillMaxWidth().height(if (lit) 1.dp else 0.5.dp).background(if (lit) look.edgeColors.first() else Ios.separator))
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) { row() }
    }
}

/* ---------- Stream tab ---------- */


fun platformOf(url: String) = when {
    "youtube" in url -> "YouTube"; "twitch" in url -> "Twitch"
    "facebook" in url -> "Facebook"; "kick" in url -> "Kick"; else -> "RTMP"
}

private val Tnum = TextStyle(fontFeatureSettings = "tnum")   // fixed-width digits: timers don't jitter

@Composable
fun StreamScreen(
    destinations: List<Destination>, activeId: String?, onSelect: (String) -> Unit, onManage: () -> Unit,
    prefs: Prefs, onToggle: () -> Unit, onPrefs: (Prefs) -> Unit, onScene: () -> Unit,
    onSuperChat: () -> Unit = {},
    onResetLogo: () -> Unit = {},
    onEditActive: (Destination) -> Unit = {},
) {
    val active = destinations.firstOrNull { it.id == activeId } ?: destinations.firstOrNull()
    var setup by remember { mutableStateOf(false) }
    val state by StreamService.state.collectAsState()
    val bps by StreamService.lastBitrate.collectAsState()
    val since by StreamService.liveSince.collectAsState()
    val live = state == StreamService.State.LIVE
    val busy = live || state == StreamService.State.CONNECTING
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) { while (since > 0) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    val secs = if (since > 0) ((now - since) / 1000).coerceAtLeast(0) else 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Brand header: the oni mark + name + a one-line mood.
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            val stamp = LocalTheme.current.p.look.stamp
            // Wa: the seal-red stamp stands in front of the name; your own logo still wins when you've set one.
            if (stamp != null && prefs.logoUri.isBlank()) Stamp(stamp) else AppLogo(prefs.logoUri, 52.dp, onClear = onResetLogo)
            Spacer(Modifier.size(14.dp))
            Column {
                ThemedTitle("Angra Streaming", 28.sp)
                Text(when (state) {
                    StreamService.State.LIVE -> "You're live. Make it count."
                    StreamService.State.CONNECTING -> "Reaching the server…"
                    StreamService.State.ERROR -> "Couldn't connect. Check the URL and key."
                    else -> if (active == null) "Add a channel to get started" else "Ready when you are"
                }, color = Ios.secondaryLabel, fontSize = 15.sp)
                if (stamp != null) microSeason(java.time.LocalDate.now()).let { ko ->
                    Text("${ko.english} · ${ko.sekki}", color = Ios.secondaryLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        StudioCard(active, state, secs, onToggle, onManage)

        // At-a-glance tiles, colour-coded like iOS Settings icons.
        SectionHeader("Stream")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Every tile is live: tap to set the stream up before (or during) a broadcast.
                val open = Modifier.weight(1f).iosTap { if (active == null) onManage() else setup = true }
                StatTile(R.drawable.ic_signal, AppleGreen,
                    if (live) "${bps / 1000}" else active?.let { "%.1f".format(it.videoBitrate / 1e6) } ?: "Add",
                    if (live) "kbps upload" else if (active != null) "Mbps target" else "a channel", open)
                StatTile(R.drawable.ic_video, Color(0xFF0A84FF),
                    active?.let { "${it.height}p${it.fps}" } ?: "—",
                    active?.let { (if (it.hevc) "HEVC" else "H.264") + " · tap to change" } ?: "Quality", open)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val open = Modifier.weight(1f).iosTap { if (active == null) onManage() else setup = true }
                StatTile(R.drawable.ic_timer, Color(0xFFFF9500),
                    active?.delaySeconds?.let { if (it == 0) "Off" else if (it >= 60) "${it / 60}m ${it % 60}s" else "${it}s" } ?: "—",
                    "Stream delay", open)
                StatTile(R.drawable.ic_audio, Color(0xFFBF5AF2),
                    when (prefs.audioSource) { Prefs.AUDIO_MIC -> "Mic"; Prefs.AUDIO_GAME -> "Game"; else -> "Mic + Game" },
                    if (active?.stereo != false) "Stereo · ${(active?.audioBitrate ?: 128_000) / 1000}k" else "Mono", open)
            }
            if (setup && active != null) StreamSetup(active, prefs, busy, onPrefs, onEditActive) { setup = false }
        }

        // Control Center-style quick toggles.
        SectionHeader("Quick controls")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickToggle(R.drawable.ic_record, "Record", prefs.recordWhileStreaming, AppleRed, Modifier.weight(1f)) {
                onPrefs(prefs.copy(recordWhileStreaming = !prefs.recordWhileStreaming))
            }
            QuickToggle(R.drawable.ic_bubble, "Bubble", prefs.bubbleEnabled, Accent, Modifier.weight(1f)) {
                onPrefs(prefs.copy(bubbleEnabled = !prefs.bubbleEnabled))
            }
            QuickToggle(R.drawable.ic_signal, "Adaptive", prefs.adaptiveBitrate, AppleGreen, Modifier.weight(1f)) {
                onPrefs(prefs.copy(adaptiveBitrate = !prefs.adaptiveBitrate))
            }
        }

        // Scene & mixer: arrange layers on the real stream frame; works live, like OBS.
        if (active != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().themed(Part.TILE)
                    .iosTap { onScene() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).background(EmberBrush, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_video), null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scene & Audio Mixer", fontWeight = FontWeight.SemiBold)
                    Text(if (busy) "Adjust layers and volume live" else sceneSummary(active),
                        fontSize = 13.sp, color = Ios.secondaryLabel)
                }
                Text("›", color = Ios.secondaryLabel, fontSize = 22.sp)
            }
        }

        SectionHeader("Audio mixer")
        AudioMixer(prefs, onPrefs)
        Footer("Starts at 100%. Go down to 0 or up to 150%; tap the percentage to return to 100%. Applies live while you stream.")

        BreakCard(prefs, onPrefs, live)

        // Super Chat alerts: paid messages from YouTube live chat, shown on stream.
        val scStatus by StreamService.superChatStatus.collectAsState()
        Row(
            Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().themed(Part.TILE)
                .iosTap { onSuperChat() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).background(Brush.linearGradient(listOf(Color(0xFFFFCA28), Color(0xFFF57C00), Color(0xFFE91E63))),
                RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Text("$", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Chat & Super Chats", fontWeight = FontWeight.SemiBold)
                Text(when {
                    !prefs.superChat && !prefs.showChat -> "Off · show YouTube chat and Super Chats on stream"
                    live && scStatus.isNotBlank() -> scStatus
                    prefs.ytApiKey.isBlank() -> "On · add your YouTube key to connect"
                    else -> "On · ${dev.livepipe.SuperChatCard.STYLES[prefs.alertStyle.coerceIn(0, 3)]} style, ${prefs.alertSeconds} s"
                }, fontSize = 13.sp, color = Ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Ios.secondaryLabel, fontSize = 22.sp)
        }

        // Channel switcher: pick where to go live without leaving the tab (locked while live).
        Row(Modifier.fillMaxWidth().padding(end = 32.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { SectionHeader("Channels") }
            Text("Manage", color = Accent, fontSize = 15.sp, modifier = Modifier.padding(bottom = 7.dp).iosTap { onManage() })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            destinations.forEach { d ->
                val on = d.id == active?.id
                Column(
                    Modifier.width(156.dp).graphicsLayer { alpha = if (busy && !on) 0.4f else 1f }
                        .themed(Part.TILE, selected = on)
                        .iosTap(enabled = !busy) { onSelect(d.id) }.padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(platformOf(d.url).uppercase(), color = Accent, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
                        if (on) Text("✓", color = Accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(d.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${d.height}p · ${d.fps} fps", fontSize = 13.sp, color = Ios.secondaryLabel)
                }
            }
            Box(
                Modifier.size(width = 72.dp, height = 88.dp)
                    .border(1.dp, Ios.separator, RoundedCornerShape(18.dp)).iosTap { onManage() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, "Add channel", tint = Accent) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** The hero: a dark "studio" card that stays dark in both themes, like a camera viewfinder. */
@Composable
fun StudioCard(active: Destination?, state: StreamService.State, secs: Long, onToggle: () -> Unit, onManage: () -> Unit) {
    val live = state == StreamService.State.LIVE
    val busy = live || state == StreamService.State.CONNECTING
    val look = LocalTheme.current.p.look
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().themed(Part.HERO).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(active?.let { platformOf(it.url) }?.uppercase() ?: "NO CHANNEL", color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.background(Color.White.copy(alpha = 0.12f), AppleShapes.capsule)
                    .padding(horizontal = 10.dp, vertical = 5.dp))
            Spacer(Modifier.weight(1f))
            LivePill(state)
        }
        Spacer(Modifier.height(18.dp))
        Text(active?.name ?: "No channel yet", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("%02d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60),
            color = if (live) Color.White else Color.White.copy(alpha = 0.35f), fontSize = 52.sp,
            fontWeight = FontWeight.Light, letterSpacing = (-1).sp, style = Tnum)
        Text(active?.let { "${it.width}×${it.height} · ${it.fps} fps · ${it.videoBitrate / 1000} kbps" }
            ?: "Add YouTube, Twitch, Kick, Facebook or any RTMP server",
            color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))

        // Primary action: ember gradient to go live, solid red to end, plain to add a channel.
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 1f, stiffness = 700f), label = "go")
        val shape = look.shape(Part.SMALL)
        val outline = look.outlineButton && active != null && !busy
        Box(
            Modifier.fillMaxWidth().height(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .then(when {
                    active == null -> Modifier.background(Color.White.copy(alpha = 0.14f), shape)
                    busy -> Modifier.background(AppleRed, shape)
                    outline -> Modifier.background(Accent.copy(alpha = 0.10f), shape).border(1.dp, Accent, shape)
                    else -> Modifier.shadow(14.dp, shape, spotColor = Accent).background(EmberBrush, shape)
                })
                .clickable(src, indication = null) { if (active == null) onManage() else onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Text(when {
                active == null -> "Add a Channel"
                state == StreamService.State.CONNECTING -> "Cancel"
                live -> "End Stream"
                else -> "Go Live"
            }.let { if (look.titleCaps) it.uppercase() else it }, color = if (active != null && !busy) look.onBrush else Color.White,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = if (look.titleCaps) 2.sp else (-0.2).sp,
                fontFamily = look.titleFont)
        }
    }
}

@Composable
fun LivePill(state: StreamService.State) {
    val (text, color) = when (state) {
        StreamService.State.LIVE -> "LIVE" to AppleRed
        StreamService.State.CONNECTING -> "CONNECTING" to Color(0xFFFF9500)
        StreamService.State.ERROR -> "FAILED" to AppleRed
        else -> "OFFLINE" to Color.White.copy(alpha = 0.45f)
    }
    // The dot pulses only while live, and the animation exists only then: offline it would tick every frame for nothing.
    val a = if (state == StreamService.State.LIVE)
        rememberInfiniteTransition(label = "pulse").animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    else null
    Row(Modifier.background(color.copy(alpha = 0.18f), AppleShapes.capsule).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = a?.value ?: 1f }
            .background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun StatTile(icon: Int, tint: Color, value: String, caption: String, modifier: Modifier) = Column(
    modifier.themed(Part.TILE).padding(14.dp),
) {
    Box(Modifier.size(30.dp).background(tint, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, style = Tnum,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(caption, fontSize = 13.sp, color = Ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Break screen: covers the stream while it keeps running (audio continues). Plain black with no name or
 * branding, or the user's own image / GIF fitted without stretching. The image is copied into the app.
 */
@Composable
private fun BreakCard(prefs: Prefs, onPrefs: (Prefs) -> Unit, live: Boolean) {
    val ctx = LocalContext.current
    val onBreak by StreamService.onBreak.collectAsState()
    fun setImage(uri: String) {
        val old = prefs.breakImageUri
        if (old.isNotBlank() && old != uri) android.net.Uri.parse(old).path?.let { java.io.File(it).delete() }
        onPrefs(prefs.copy(breakImageUri = uri))
        if (onBreak) StreamService.setBreak(true)          // swap it on air straight away
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyOverlay(ctx, uri, "break")?.let(::setImage)
    }
    val thumb = remember(prefs.breakImageUri) {
        prefs.breakImageUri.takeIf { it.isNotBlank() }?.let { u ->
            runCatching {
                ctx.contentResolver.openInputStream(android.net.Uri.parse(u))?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    Column(
        Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().themed(Part.TILE).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(if (onBreak) AppleRed else Color(0xFF2C2C2E), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_pause), null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Break screen", fontWeight = FontWeight.SemiBold)
                Text(when {
                    onBreak -> "On air: viewers see your break screen"
                    live -> "Cover the stream without ending it"
                    else -> "Ready for when you go live"
                }, fontSize = 13.sp, color = Ios.secondaryLabel)
            }
            Text(if (onBreak) "End" else "Start", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(if (onBreak) AppleRed else if (live) Accent else Ios.secondaryLabel.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .iosTap(live) { StreamService.setBreak(!onBreak) }
                    .padding(horizontal = 18.dp, vertical = 7.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // What viewers will see, at stream shape.
            Box(Modifier.width(104.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(8.dp)).background(Color.Black)
                .border(0.5.dp, Ios.separator, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                thumb?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Chips(listOf(false, true), prefs.breakImageUri.isNotBlank(), { if (it) "My image" else "Black" },
                    Modifier.fillMaxWidth()) { custom -> if (custom) pick.launch("image/*") else setImage("") }
                if (prefs.breakImageUri.isNotBlank()) Text("Change image", color = Accent, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp).iosTap { pick.launch("image/*") })
            }
        }
    }
}

/** Five bubble slots on top, every available control below: tap a slot, then a control to put there. */
@Composable
private fun BubbleSlotsEditor(prefs: Prefs, onPrefs: (Prefs) -> Unit) {
    val slots = dev.livepipe.BubbleAction.slots(prefs.bubbleSlots)
    var sel by remember { mutableStateOf(0) }
    InsetCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp)) {
            slots.forEachIndexed { i, a ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ActionChip(a, selected = i == sel, used = true) { sel = i } }
            }
        }
        Hairline()
        Text("Put in slot ${sel + 1}", fontSize = 13.sp, color = Ios.secondaryLabel,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        dev.livepipe.BubbleAction.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                row.forEach { a ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ActionChip(a, selected = a == slots[sel], used = a in slots) {
                            onPrefs(prefs.copy(bubbleSlots = dev.livepipe.BubbleAction.assign(slots, sel, a).joinToString(",") { it.id }))
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionChip(a: dev.livepipe.BubbleAction, selected: Boolean, used: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).iosTap(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp)
                .background(if (selected) EmberBrush else SolidColor(if (used) Color(0xFF3A3A3C) else Color(0xFF1C1C1E)), CircleShape)
                .border(if (selected) 2.dp else 0.5.dp, if (selected) Color.White.copy(alpha = 0.6f) else Ios.separator, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(a.icon), a.label, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Text(a.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (used || selected) MaterialTheme.colorScheme.onSurface else Ios.secondaryLabel,
            modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
fun QuickToggle(icon: Int, label: String, on: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) tint else MaterialTheme.colorScheme.surface, label = "qt")
    val fg = if (on) Color.White else MaterialTheme.colorScheme.onSurface
    Column(modifier.aspectRatio(1f).themed(Part.TILE, tint = if (on) bg else null).iosTap(onClick = onClick).padding(14.dp)) {
        Box(Modifier.size(34.dp).background(if (on) Color.White.copy(alpha = 0.22f) else Ios.segmentTrack, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(if (on) "On" else "Off", color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

/* ---------- Channels tab ---------- */

fun sceneSummary(d: Destination) = listOf(
    if (d.screenFit) "Fit" else "Fill",
    "zoom ${(d.screenZoom * 100).toInt()}%",
    if (d.overlayUri.isBlank()) "no overlay" else "overlay",
    if (d.facecam) "facecam" else null,
).filterNotNull().joinToString(" · ")

@Composable
fun DestinationsScreen(
    destinations: List<Destination>,
    activeId: String?,
    save: (List<Destination>, String?) -> Unit,
    prefs: Prefs,
    onPrefs: (Prefs) -> Unit,
) {
    var editing by remember { mutableStateOf<Destination?>(null) }
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }

    if (adding || editing != null) {
        DestinationEditor(
            existing = editing,
            taken = destinations.filter { it.id != editing?.id }.map { it.name },
            prefs = prefs, onPrefs = onPrefs,
            onCancel = { adding = false; editing = null },
            onSave = { d ->
                val list = if (editing != null) destinations.map { if (it.id == d.id) d else it }
                else destinations + d
                save(list, activeId ?: d.id)
                StreamService.updateScene(d)          // if this channel is live, its layers move on air
                adding = false; editing = null
            },
            onDelete = { d ->
                deleteOverlays(ctx, d.id)
                val list = destinations.filterNot { it.id == d.id }
                save(list, if (activeId == d.id) list.firstOrNull()?.id else activeId)
                editing = null
            },
        )
        return
    }

    // Display order: pinned first, each group in saved order. Positions (1..N) refer to this order.
    val ordered = destinations.filter { it.pinned } + destinations.filterNot { it.pinned }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var moving by remember { mutableStateOf<Destination?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = !selecting)) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.Bottom) {
            LargeTitle("Channels", Modifier.weight(1f))
            if (destinations.isNotEmpty()) Text(if (selecting) "Done" else "Select", color = Accent,
                fontWeight = if (selecting) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(bottom = 12.dp, end = 14.dp).iosTap { selecting = !selecting; selected = emptySet() })
            if (!selecting) Icon(Icons.Filled.Add, "Add channel", tint = Accent,
                modifier = Modifier.padding(bottom = 8.dp).size(30.dp).iosTap { adding = true })
        }
        if (destinations.isEmpty()) {
            Footer("No channels yet. Tap + to add YouTube, Twitch, Kick, Facebook or a custom RTMP URL. " +
                "Make as many profiles as you like, e.g. one YouTube channel per game.")
        } else {
            SectionHeader(if (selecting) "Tap, or drag down the list to select" else "Tap to make active · tap the number to move")
            val rowH = 64.dp
            val stepPx = with(LocalDensity.current) { rowH.toPx() + 0.5.dp.toPx() }
            InsetCard {
                Column(Modifier.pointerInput(selecting, ordered) {
                    if (!selecting) return@pointerInput
                    // Photos-style sweep: the first row touched decides select vs deselect for the whole drag.
                    var add = true
                    fun mark(y: Float) {
                        val id = ordered[(y / stepPx).toInt().coerceIn(0, ordered.lastIndex)].id
                        selected = if (add) selected + id else selected - id
                    }
                    detectDragGestures(onDragStart = { o ->
                        add = ordered[(o.y / stepPx).toInt().coerceIn(0, ordered.lastIndex)].id !in selected
                        mark(o.y)
                    }) { change, _ -> mark(change.position.y) }
                }) {
                    ordered.forEachIndexed { i, d ->
                        if (i > 0) Hairline()
                        val isSel = d.id in selected
                        Row(Modifier.fillMaxWidth().height(rowH)
                            .iosTap { if (selecting) selected = if (isSel) selected - d.id else selected + d.id else save(destinations, d.id) }
                            .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (selecting) {
                                Box(Modifier.size(24.dp).background(if (isSel) Accent else Color.Transparent, CircleShape)
                                    .border(1.5.dp, if (isSel) Accent else Ios.separator, CircleShape), contentAlignment = Alignment.Center) {
                                    if (isSel) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                // Position badge: tap to open the number wheel and move this channel anywhere.
                                Box(Modifier.size(width = 36.dp, height = 26.dp).background(Ios.segmentTrack, RoundedCornerShape(7.dp))
                                    .iosTap { moving = d }, contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (d.pinned) Text("★ ", color = Accent, fontSize = 14.sp)
                                    Text(d.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(platformOf(d.url) + " · ${d.height}p · ${d.fps} fps · ${d.videoBitrate / 1000} kbps",
                                    fontSize = 13.sp, color = Ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (!selecting) {
                                if (d.id == activeId) Text("✓", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp))
                                Text("Edit", color = Accent, modifier = Modifier.iosTap { editing = d }.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (selecting) 110.dp else 32.dp))
    }

    // Selection actions, iOS-style toolbar along the bottom.
    if (selecting) Row(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        val any = selected.isNotEmpty()
        val tint = if (any) Accent else Ios.secondaryLabel
        Text("Pin", color = tint, modifier = Modifier.iosTap(any) {
            save(destinations.map { if (it.id in selected) it.copy(pinned = true) else it }, activeId); selected = emptySet() })
        Text("Unpin", color = tint, modifier = Modifier.iosTap(any) {
            save(destinations.map { if (it.id in selected) it.copy(pinned = false) else it }, activeId); selected = emptySet() })
        Text(if (any) "Delete (${selected.size})" else "Delete", color = if (any) AppleRed else Ios.secondaryLabel,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.iosTap(any) { confirmDelete = true })
    }

    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${selected.size} channel${if (selected.size == 1) "" else "s"}?") },
        text = { Text("Their settings, scenes and overlay copies are removed from the app. This can't be undone.") },
        confirmButton = { Text("Delete", color = AppleRed, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp).iosTap {
            selected.forEach { deleteOverlays(ctx, it) }
            val left = destinations.filterNot { it.id in selected }
            save(left, if (activeId in selected) left.firstOrNull()?.id else activeId)
            selected = emptySet(); confirmDelete = false; selecting = left.isNotEmpty()
        }) },
        dismissButton = { Text("Cancel", color = Accent, modifier = Modifier.padding(12.dp).iosTap { confirmDelete = false }) },
    )

    moving?.let { m ->
        PositionWheel(m.name, ordered.indexOf(m) + 1, ordered.size, onCancel = { moving = null }) { pos ->
            // Re-insert at the chosen spot. Landing among the pinned channels pins it; below them unpins it.
            val rest = ordered.filterNot { it.id == m.id }
            val pinnedBefore = rest.count { it.pinned }
            val moved = m.copy(pinned = pos - 1 < pinnedBefore || (m.pinned && pos - 1 == pinnedBefore))
            save(rest.toMutableList().apply { add(pos - 1, moved) }, activeId)
            moving = null
        }
    }
    }
}

/** iOS-style number wheel: scroll the digits; the one in the middle is the new position. */
@Composable
private fun PositionWheel(name: String, current: Int, count: Int, onCancel: () -> Unit, onMove: (Int) -> Unit) {
    val itemH = 44.dp
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = current - 1)
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state)
    // Content padding of two rows means the first visible item sits in the middle slot.
    val centre by remember { androidx.compose.runtime.derivedStateOf {
        val half = (state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0) / 2
        state.firstVisibleItemIndex + (if (state.firstVisibleItemScrollOffset > half) 1 else 0) + 1
    } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).iosTap { onCancel() }, contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .pointerInput(Unit) { detectTapGestures { } }.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", color = Accent, modifier = Modifier.iosTap { onCancel() })
                Text("Move “$name”", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Text("Move", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.iosTap { onMove(centre.coerceIn(1, count)) })
            }
            Box(Modifier.fillMaxWidth().height(itemH * 5), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(itemH).background(Ios.segmentTrack, RoundedCornerShape(10.dp)))
                androidx.compose.foundation.lazy.LazyColumn(state = state, flingBehavior = fling,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemH * 2),
                    horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                    items(count) { i ->
                        val n = i + 1
                        Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("$n", fontSize = if (n == centre) 24.sp else 19.sp,
                                fontWeight = if (n == centre) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (n == centre) MaterialTheme.colorScheme.onSurface else Ios.secondaryLabel)
                        }
                    }
                }
            }
            Footer("Scroll to the new number. Moving above the last pinned channel pins it.")
        }
    }
}

@Composable
fun DestinationEditor(
    existing: Destination?,
    prefs: Prefs,
    onPrefs: (Prefs) -> Unit,
    onCancel: () -> Unit,
    onSave: (Destination) -> Unit,
    onDelete: (Destination) -> Unit,
    taken: List<String> = emptyList(),               // names of the other channels (exact, case-sensitive)
) {
    // First free name for a platform: "YouTube", then "YouTube 2", "YouTube 3"…
    fun freeName(base: String) = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base $it" }.first { it !in taken }
    // A new channel starts on YouTube with its complete server URL; editing keeps the channel's own.
    var platform by remember {
        mutableStateOf(if (existing == null) PLATFORMS[0]
            else PLATFORMS.firstOrNull { it.url.isNotBlank() && it.url == existing.url } ?: PLATFORMS.last())
    }
    var name by remember { mutableStateOf(existing?.name ?: freeName(PLATFORMS[0].name)) }
    // The name follows the platform until the user types their own.
    var autoName by remember { mutableStateOf(existing == null) }
    var url by remember { mutableStateOf(existing?.url ?: PLATFORMS[0].url) }
    var key by remember { mutableStateOf(existing?.key ?: "") }
    var res by remember {
        mutableStateOf(RESOLUTIONS.firstOrNull { it.w == existing?.width && it.h == existing.height } ?: RESOLUTIONS[2])
    }
    var fps by remember { mutableStateOf(existing?.fps ?: 30) }
    var vBitrate by remember { mutableStateOf((existing?.videoBitrate ?: 4_500_000).toFloat()) }
    var aBitrate by remember { mutableStateOf(existing?.audioBitrate ?: 128_000) }
    var sample by remember { mutableStateOf(existing?.sampleRate ?: 48_000) }
    var stereo by remember { mutableStateOf(existing?.stereo ?: true) }
    var hevc by remember { mutableStateOf(existing?.hevc ?: false) }
    var keyframe by remember { mutableStateOf(existing?.keyframeSeconds ?: 2) }
    var portrait by remember { mutableStateOf(existing?.portrait ?: false) }
    var delay by remember { mutableStateOf(existing?.delaySeconds ?: 0) }
    var delayUnitMin by remember { mutableStateOf((existing?.delaySeconds ?: 0) >= 60) }
    // Scene layout (screen/overlay/facecam) is edited in SceneEditor; the form only carries it through.
    var scene by remember { mutableStateOf(existing ?: Destination(UUID.randomUUID().toString(), "", "", "")) }
    val ctx = LocalContext.current
    val caps = remember { DeviceCaps(ctx) }
    var showScene by remember { mutableStateOf(false) }
    if (showScene) {
        SceneEditor(scene.copy(width = res.w, height = res.h, portrait = portrait), prefs, onPrefs,
            onCancel = { showScene = false }, onDone = { scene = it; showScene = false })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val duplicate = name.trim() in taken
        val valid = name.isNotBlank() && !duplicate && url.startsWith("rtmp") && url.trim().length > "rtmps://".length
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Cancel", color = Accent, modifier = Modifier.iosTap { onCancel() })
            Text(if (existing == null) "New Channel" else "Edit Channel", fontWeight = FontWeight.SemiBold)
            Text("Save", color = if (valid) Accent else Ios.secondaryLabel, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.iosTap(enabled = valid) {
                    onSave(scene.copy(
                        id = scene.id, name = name.trim(), url = url.trim(), key = key.trim(),
                        width = res.w, height = res.h, fps = fps, videoBitrate = vBitrate.toInt(), audioBitrate = aBitrate,
                        sampleRate = sample, stereo = stereo, hevc = hevc, keyframeSeconds = keyframe, portrait = portrait,
                        delaySeconds = delay))
                })
        }
        SectionHeader("Platform")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLATFORMS.forEach { p ->
                val on = p == platform
                Surface(color = if (on) Accent else MaterialTheme.colorScheme.surfaceVariant, shape = AppleShapes.capsule,
                    modifier = Modifier.iosTap {
                        platform = p
                        if (autoName || name.isBlank()) { name = freeName(p.name); autoName = true }
                        // Kick and custom servers are per-account: clear the old platform's URL so it isn't kept by mistake.
                        url = p.url.ifBlank { "rtmps://" }
                    }) {
                    Text(p.name, fontSize = 15.sp, color = if (on) Color.White else Accent, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
        Footer("Picks the server URL. Paste your stream key below — get it from the platform's dashboard (an open-source app can't generate keys for you).")

        SectionHeader("Destination")
        InsetCard {
            FieldRow("Name", name, { name = it; autoName = false }, "My channel")
            Hairline()
            FieldRow("Server", url, { url = it }, "rtmps://…", keyboard = KeyboardType.Uri)
            Hairline()
            FieldRow("Stream key", key, { key = it }, "Paste from dashboard", secure = true)
        }
        if (duplicate) Footer("You already have a channel named \"${name.trim()}\". Names must differ, even by one letter or its case.")
        // Only offer what this phone can actually encode; out-of-range picks snap to the nearest valid one.
        val codecs = if (caps.hevc) listOf(false, true) else listOf(false)
        val resList = RESOLUTIONS.filter { caps.supports(it, 30, hevc, portrait) }.ifEmpty { RESOLUTIONS.take(1) }
        val fpsList = FPS.filter { caps.supports(res, it, hevc, portrait) }.ifEmpty { FPS.take(1) }
        val maxRate = caps.maxBitrate(hevc).coerceIn(1_000_000, 12_000_000)
        LaunchedEffect(resList, fpsList, codecs, maxRate) {
            if (hevc !in codecs) hevc = false
            if (res !in resList) res = resList.lastOrNull { it.h <= res.h } ?: resList.first()
            if (fps !in fpsList) fps = fpsList.last()
            vBitrate = vBitrate.coerceAtMost(maxRate.toFloat())
        }
        SectionHeader("Resolution")
        Chips(resList, res, { it.toString() }) { res = it }
        SectionHeader("Frame rate")
        Chips(fpsList, fps, { "$it fps" }) { fps = it }
        SectionHeader("Orientation")
        Chips(listOf(false, true), portrait, { if (it) "Portrait" else "Landscape" }) { portrait = it }
        SectionHeader("Codec")
        Chips(codecs, hevc, { if (it) "HEVC (H.265)" else "H.264" }) { hevc = it }
        SectionHeader("Keyframe interval")
        Chips(listOf(1, 2, 4), keyframe, { "${it}s" }) { keyframe = it }
        SectionHeader("Video bitrate · ${vBitrate.toInt() / 1000} kbps · CBR")
        // Snaps 800k..device max to 100-kbps stops.
        IosSlider(vBitrate, { vBitrate = (it / 100_000f).toInt() * 100_000f },
            valueRange = 800_000f..maxRate.toFloat(), steps = ((maxRate - 800_000) / 100_000 - 1).coerceAtLeast(0))
        Footer("Options are limited to what this phone can stream: up to ${resList.last()} at " +
            "${FPS.filter { caps.supports(resList.last(), it, hevc, portrait) }.maxOrNull() ?: 30} fps" +
            (if (caps.hevc) "" else ", H.264 only") + ".")
        SectionHeader("Audio bitrate")
        Chips(AUDIO_BITRATES, aBitrate, { "${it / 1000}k" }) { aBitrate = it }
        SectionHeader("Sample rate")
        Chips(SAMPLE_RATES, sample, { "${it / 1000}kHz" }) { sample = it }
        Spacer(Modifier.height(12.dp))
        InsetCard { RowItem("Stereo") { IosSwitch(stereo, { stereo = it }) } }
        SectionHeader("Scene & audio")
        InsetCard {
            Row(Modifier.fillMaxWidth().iosTap { showScene = true }.heightIn(min = 52.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Scene & Audio Mixer")
                    Text(sceneSummary(scene), fontSize = 13.sp, color = Ios.secondaryLabel)
                }
                Text("›", color = Ios.secondaryLabel, fontSize = 22.sp)
            }
        }
        Footer("Arrange your screen, overlay and facecam on the real stream frame, and set mic/game volume.")
        // Stream-delay meter: slider on the left reads the value; the unit chip on the right switches
        // the scale to seconds (0–59) or minutes (0–10), so any value 0s–10m is reachable.
        SectionHeader("Stream delay · ${if (delay >= 60) "${delay / 60}m ${delay % 60}s" else "${delay}s"}")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (delayUnitMin) {
                IosSlider(((delay / 60).coerceIn(0, 10)).toFloat(), { delay = it.toInt() * 60 },
                    valueRange = 0f..10f, steps = 9, modifier = Modifier.weight(1f))
            } else {
                IosSlider((delay.coerceIn(0, 59)).toFloat(), { delay = it.toInt() },
                    valueRange = 0f..59f, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.size(12.dp))
            Chips(listOf(false, true), delayUnitMin, { if (it) "min" else "sec" }, Modifier.width(112.dp)) {
                delayUnitMin = it
                delay = if (it) ((delay + 59) / 60) * 60 else delay.coerceAtMost(59)
            }
        }
        Footer("OBS-style delay for this channel: holds the stream before sending (stops stream-sniping). Longer delays use more memory.")

        if (existing != null) {
            Spacer(Modifier.height(24.dp))
            InsetCard {
                RowItem("Delete channel") {
                    Text("Delete", color = AppleRed, modifier = Modifier.iosTap { onDelete(existing) })
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/* ---------- Settings tab ---------- */

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onPickImage: () -> Unit,
    micDevices: List<Pair<Int, String>>,
    onPrefs: (Prefs) -> Unit,
    onPreviewBubble: (Boolean) -> Unit,
    onPickLogo: () -> Unit = {},
    onPinIcon: () -> Unit = {},
    onResetLogo: () -> Unit = {},
) {
    var previewing by remember { mutableStateOf(dev.livepipe.BubbleService.previewing) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("Settings")

        SectionHeader("App icon")
        InsetCard {
            RowItem("Your logo") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(prefs.logoUri, 34.dp, onClear = onResetLogo)
                    Spacer(Modifier.size(12.dp))
                    Text(if (prefs.logoUri.isBlank()) "Choose" else "Change", color = Accent, modifier = Modifier.iosTap { onPickLogo() })
                }
            }
            if (prefs.logoUri.isNotBlank()) {
                Hairline()
                RowItem("Delete my logo") { Text("Delete", color = AppleRed, modifier = Modifier.iosTap { onResetLogo() }) }
            }
            Hairline()
            RowItem("Home Screen icon with your logo") { Text("Add", color = Accent, modifier = Modifier.iosTap { onPinIcon() }) }
        }
        Footer("Your logo replaces the oni across the app. \"Add\" puts an Angra Streaming icon with your picture on your home " +
            "screen (your launcher asks to confirm). Deleting your logo brings the oni back and retires that home-screen icon. " +
            "Android doesn't let apps swap their own launcher icon for a photo, so keep yours and remove the original if you like.")

        SectionHeader("Theme · ${paletteOf(prefs.theme).name}")
        ThemePicker(prefs.theme) { onPrefs(prefs.copy(theme = it)) }
        Spacer(Modifier.height(14.dp))
        InsetCard {
            RowItem("Live theme") { IosSwitch(prefs.liveTheme, { onPrefs(prefs.copy(liveTheme = it)) }) }
        }
        Footer("Brings the theme to life. Turn it off and the theme stops on that exact frame. It also pauses whenever " +
            "you leave the app or go live, so it never runs in the background. Uses a little more battery while you're in the app.")

        SectionHeader("Floating bubble")
        // One grouped card: toggle, what the bubble shows, its image.
        InsetCard {
            RowItem("Show bubble while live") {
                IosSwitch(prefs.bubbleEnabled, { onPrefs(prefs.copy(bubbleEnabled = it)) })
            }
            Hairline()
            RowItem("Preview bubble now") {
                IosSwitch(previewing, { previewing = it; onPreviewBubble(it) })
            }
            Hairline()
            RowItem("Hide to edge bar when idle") {
                IosSwitch(prefs.bubbleEdgeHide, { onPrefs(prefs.copy(bubbleEdgeHide = it)) })
            }
            Hairline()
            RowItem("Hide bubble from stream") {
                IosSwitch(prefs.bubbleSecure, { onPrefs(prefs.copy(bubbleSecure = it)) })
            }
            Hairline()
            RowItem("Bubble shows") {
                Chips(listOf(0, 1, 2), if (prefs.bubbleShowsFps) 2 else if (prefs.bubbleShowsTimer) 1 else 0,
                    { listOf("Icon", "Timer", "FPS")[it] }, Modifier.width(186.dp)) {
                    onPrefs(prefs.copy(bubbleShowsTimer = it == 1, bubbleShowsFps = it == 2))
                }
            }
            Hairline()
            RowItem("Custom image") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BubblePreview(prefs.bubbleImageUri)
                    Text(if (prefs.bubbleImageUri.isBlank()) "Choose" else "Change", color = Accent,
                        modifier = Modifier.iosTap { onPickImage() })
                }
            }
        }
        var alphaNow by remember(prefs.bubbleTransparency) { mutableFloatStateOf(prefs.bubbleTransparency) }
        SectionHeader("Bubble transparency · ${(alphaNow * 100).toInt()}%")
        IosSlider(prefs.bubbleTransparency, { onPrefs(prefs.copy(bubbleTransparency = it)) },
            valueRange = 0f..Prefs.BUBBLE_MAX_TRANSPARENCY,
            onLive = { alphaNow = it; dev.livepipe.BubbleService.setTransparency(it) })
        Footer("Preview places the bubble before you go live: drag it anywhere, it snaps to the nearest edge. Edge bar: an idle bubble tucks into a slim handle; tap it to pop out. Hide bubble from stream marks the bubble private, so capture can't show its content (most phones show a blank patch instead). For a clean stream on Android 14+, share \"A single app\" when going live and the bubble stays out completely. The system recording indicator always stays visible.")

        SectionHeader("Bubble controls")
        BubbleSlotsEditor(prefs, onPrefs)
        Footer("Tap a slot, then tap what it should do. These five sit around the bubble while you're live, " +
            "so you never have to come back to the app. Already-used controls swap places.")

        SectionHeader("Audio source")
        Chips(listOf(Prefs.AUDIO_MIC, Prefs.AUDIO_GAME, Prefs.AUDIO_MIX), prefs.audioSource,
            { when (it) { Prefs.AUDIO_MIC -> "Mic"; Prefs.AUDIO_GAME -> "Game"; else -> "Both" } }) {
            onPrefs(prefs.copy(audioSource = it))
        }
        Footer("Game audio needs Android 10+; older devices always use the mic.")

        if (prefs.audioSource != Prefs.AUDIO_GAME) {
            SectionHeader("Microphone")
            // A saved mic that's no longer connected falls back to Default (what the stream will use).
            CheckList(micDevices.map { it.first }, prefs.micDeviceId.takeIf { id -> micDevices.any { it.first == id } } ?: -1,
                { id -> micDevices.firstOrNull { it.first == id }?.second ?: "Phone microphone" },
                emptyHint = if (micDevices.size <= 1) "No external mic connected" else null) {
                onPrefs(prefs.copy(micDeviceId = it))
            }
            Footer("Connect Bluetooth, wired or USB mics and they appear here instantly.")
        }

        SectionHeader("Charging")
        ChargingSection(prefs.charging) { onPrefs(prefs.copy(charging = it)) }

        SectionHeader("Network")
        InsetCard {
            RowItem("Adaptive bitrate") {
                IosSwitch(prefs.adaptiveBitrate, { onPrefs(prefs.copy(adaptiveBitrate = it)) })
            }
        }
        Footer("Drops quality automatically when upload can't keep up, instead of dropping the stream.")

        SectionHeader("Recording")
        InsetCard {
            RowItem("Record while streaming") {
                IosSwitch(prefs.recordWhileStreaming, { onPrefs(prefs.copy(recordWhileStreaming = it)) })
            }
        }
        Footer("Recording uses the active channel's resolution and bitrate (single encoder keeps CPU low).")
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Charging control. Shows what this phone allows (root: charger IC limit; Samsung: One UI fast-charge switches;
 * otherwise nothing), the mode picker when it can act, and live measurements so the effect can be checked.
 */
@Composable
fun ChargingSection(mode: Int, onMode: (Int) -> Unit) {
    val ctx = LocalContext.current
    var r by remember { mutableStateOf(dev.livepipe.Charging.read(ctx)) }
    var engine by remember { mutableStateOf(dev.livepipe.Charging.engine(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            r = dev.livepipe.Charging.read(ctx); engine = dev.livepipe.Charging.engine(ctx)   // picks up an adb grant
        }
    }
    val status by dev.livepipe.Charging.status.collectAsState()
    val canAct = engine == E.ROOT || engine == E.SAMSUNG
    val target = dev.livepipe.Charging.targetMa(mode, r.capacityMah)

    if (canAct) Chips(listOf(0, 1, 2), mode, { dev.livepipe.Charging.names[it] }) { onMode(it) }
    InsetCard {
        RowItem("Control") {
            Text(when (engine) {
                E.ROOT -> "Charger IC (root)"; E.SAMSUNG -> "One UI switches"
                E.SAMSUNG_NEEDS_GRANT -> "Needs one-time setup"; E.NONE -> "Not available"
            }, color = if (canAct) Accent else Ios.secondaryLabel)
        }
        if (engine == E.ROOT) {
            Hairline()
            RowItem("Limit") {
                Text(if (target == null) "Phone's maximum" else "$target mA · ${if (mode == 0) "0.5C" else "1C"} of ${r.capacityMah} mAh",
                    color = Ios.secondaryLabel)
            }
        }
        Hairline()
        RowItem(when {
            r.charging -> "Charging · %.1f W".format(r.watts)
            r.plugged -> "On power · not charging"
            else -> "On battery · ${r.level}%"
        }) { Text(if (r.charging) "${r.mA} mA" else "", color = Ios.secondaryLabel) }
        // The proof: does the measured current stay under the limit the IC was given?
        if (engine == E.ROOT && target != null && r.charging) {
            Hairline()
            val holding = r.mA <= target * 1.15
            RowItem(if (holding) "Charger IC is holding the limit" else "Measured above the limit") {
                Text(if (holding) "✓" else "!", color = if (holding) AppleGreen else AppleRed, fontWeight = FontWeight.Bold)
            }
        }
        Hairline()
        RowItem("Battery %d%% · %.1f°C".format(r.level, r.tempC)) {
            Text(when {
                r.thermal >= 3 -> "Hot"; dev.livepipe.Charging.warm(r.thermal) -> "Warm"; else -> "Cool"
            }, color = if (dev.livepipe.Charging.warm(r.thermal)) AppleRed else Ios.secondaryLabel)
        }
        if (engine == E.SAMSUNG_NEEDS_GRANT) {
            Hairline()
            RowItem("Copy setup command") {
                Text("Copy", color = Accent, modifier = Modifier.iosTap {
                    ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
                        android.content.ClipData.newPlainText("adb", dev.livepipe.Charging.GRANT_COMMAND))
                })
            }
        }
        Hairline()
        RowItem("Phone battery settings") {
            Text("Open", color = Accent, modifier = Modifier.iosTap {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_POWER_USAGE_SUMMARY)) }
            })
        }
    }
    if (status.isNotBlank() && canAct) Footer(status)
    Footer(when (engine) {
        E.ROOT -> "Normal charges at 0.5C, the standard rate in Li-ion cell datasheets (half the battery's capacity per " +
            "hour): coolest and gentlest. Medium charges at 1C. Fast hands control back to the phone's own maximum. The " +
            "limit is written into the charger chip's kernel controls, never above the phone's stock value, and re-applied " +
            "after every plug-in. If the reading stays above the limit, this phone's fast-charge path bypasses the kernel limit."
        E.SAMSUNG -> "Normal turns off Fast and Super fast charging (plain 5 V, about 10 W). Medium keeps Fast charging " +
            "(about 15 W). Fast turns both on (25–45 W with a Super fast charger). Takes effect the next time you plug in."
        E.SAMSUNG_NEEDS_GRANT -> "Samsung lets the app switch its own fast-charging modes after a one-time permission. " +
            "Connect the phone to a computer with USB debugging on and run:\n${dev.livepipe.Charging.GRANT_COMMAND}"
        E.NONE -> "This phone keeps charging control inside its manufacturer firmware, out of reach of apps. Charging modes " +
            "work on rooted phones (Magisk, KernelSU, APatch), where the app sets the charger chip's current directly, " +
            "and on Samsung phones. Charge limits like 80% and optimized charging are in your phone's battery settings."
    })
}

/** Stream setup from the Stream tab: the active channel's quality, delay and audio, without opening the editor. */
@Composable
private fun StreamSetup(d: Destination, prefs: Prefs, busy: Boolean, onPrefs: (Prefs) -> Unit, onSave: (Destination) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val caps = remember { DeviceCaps(ctx) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().themed().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Stream setup · ${d.name}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Done", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.iosTap { onClose() })
            }
            if (busy) Footer("You're live: resolution and frame rate are locked until the stream ends. Bitrate and delay apply next time.")
            val resList = RESOLUTIONS.filter { caps.supports(it, 30, d.hevc, d.portrait) }.ifEmpty { RESOLUTIONS.take(1) }
            val res = resList.firstOrNull { it.w == d.width && it.h == d.height } ?: resList.last()
            SectionHeader("Resolution")
            Chips(resList, res, { it.toString() }) { if (!busy) onSave(d.copy(width = it.w, height = it.h)) }
            val fpsList = FPS.filter { caps.supports(res, it, d.hevc, d.portrait) }.ifEmpty { FPS.take(1) }
            SectionHeader("Frame rate")
            Chips(fpsList, d.fps, { "$it fps" }) { if (!busy) onSave(d.copy(fps = it)) }
            var kbps by remember(d.videoBitrate) { mutableFloatStateOf(d.videoBitrate / 1000f) }
            SectionHeader("Video bitrate · ${kbps.roundToInt()} kbps")
            IosSlider(d.videoBitrate / 1000f, { onSave(d.copy(videoBitrate = it.roundToInt() * 1000)) }, valueRange = 1000f..12000f,
                onLive = { kbps = it })
            SectionHeader("Stream delay")
            Chips(listOf(0, 5, 10, 30, 60), d.delaySeconds, { if (it == 0) "Off" else if (it == 60) "1 min" else "${it}s" }) {
                onSave(d.copy(delaySeconds = it))
            }
            SectionHeader("Audio source")
            Chips(listOf(Prefs.AUDIO_MIC, Prefs.AUDIO_GAME, Prefs.AUDIO_MIX), prefs.audioSource,
                { when (it) { Prefs.AUDIO_MIC -> "Mic"; Prefs.AUDIO_GAME -> "Game"; else -> "Both" } }) { onPrefs(prefs.copy(audioSource = it)) }
        }
    }
}

/** Theme grid in the iOS app-icon picker style: each tile is the theme running live in miniature. */
@Composable
fun ThemePicker(selected: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Palettes.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p -> ThemeTile(p, p.id == selected, Modifier.weight(1f)) { onPick(p.id) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemeTile(p: Palette, on: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val main = LocalScene.current ?: return
    val dark = p.dark ?: androidx.compose.foundation.isSystemInDarkTheme()
    // Previews are still frames: 20 live scenes at once would cost real battery for no benefit.
    val host = remember(main) { SceneHost() }
    Column(modifier.iosTap(onClick = onPick), horizontalAlignment = Alignment.CenterHorizontally) {
        MaterialTheme(colorScheme = schemeFor(p, dark)) {
            CompositionLocalProvider(LocalTheme provides Themed(p, dark), LocalScene provides host) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.62f)
                        .border(if (on) 2.5.dp else 0.5.dp, if (on) p.accent else Ios.separator, RoundedCornerShape(18.dp))
                        .padding(if (on) 4.dp else 0.dp).clip(RoundedCornerShape(14.dp))
                        .onGloballyPositioned {
                            host.origin = it.positionInRoot()
                            host.size = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
                        }
                        .drawBehind { drawScene(p, host.size, dark) }
                        .padding(7.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(24.dp).themed(Part.SMALL))
                        Box(Modifier.fillMaxWidth(0.7f).height(14.dp).themed(Part.SMALL))
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp)
                        .then(if (p.look.outlineButton) Modifier.border(1.dp, p.accent, p.look.shape(Part.SMALL))
                            else Modifier.background(Brush.linearGradient(p.brush), p.look.shape(Part.SMALL))))
                }
            }
        }
        Text(p.name, fontSize = 12.sp, maxLines = 1, color = if (on) Accent else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The app logo: the user's own picture, or the Angra oni. */
@Composable
fun AppLogo(uri: String, size: androidx.compose.ui.unit.Dp, onClear: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val bmp = remember(uri) {
        uri.takeIf { it.isNotBlank() }?.let { u ->
            runCatching { ctx.contentResolver.openInputStream(android.net.Uri.parse(u))?.use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size * 0.27f)
    val m = Modifier.size(size).shadow(10.dp, shape, spotColor = Accent).clip(shape).background(Color.Black)
    Box {
        if (bmp != null) Image(bmp, null, m, contentScale = ContentScale.Crop)
        else Image(painterResource(R.mipmap.ic_launcher_foreground), null, contentScale = ContentScale.Crop,
            modifier = m.graphicsLayer { scaleX = 1.45f; scaleY = 1.45f })
        // A small close button on a custom logo: one tap removes it and the oni comes back.
        if (bmp != null && onClear != null) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(20.dp)
                .background(Color(0xE6000000), CircleShape).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                .iosTap { onClear() },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Small round preview of the cropped bubble image (empty when using the default icon). */
@Composable
fun BubblePreview(uri: String) {
    if (uri.isBlank()) return
    val ctx = LocalContext.current
    val bmp = remember(uri) {
        runCatching { ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
            android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    } ?: return
    Image(bmp, null, Modifier.padding(end = 12.dp).size(32.dp).clip(CircleShape))
}
