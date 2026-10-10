package dev.livepipe.ui

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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
fun LargeTitle(text: String, modifier: Modifier = Modifier) = Text(
    text, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
    modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
)

@Composable
fun InsetCard(content: @Composable ColumnScope.() -> Unit) = Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = AppleShapes.card,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
) { Column(content = content) }

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
    BoxWithConstraints(modifier.height(34.dp).background(Ios.segmentTrack, RoundedCornerShape(9.dp)).padding(2.dp)) {
        val w = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(w * idx.coerceAtLeast(0), spring(dampingRatio = 1f, stiffness = 500f), label = "seg")
        if (idx >= 0) Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .shadow(1.dp, RoundedCornerShape(7.dp)).background(Ios.segmentThumb, RoundedCornerShape(7.dp))
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
) = Slider(
    value, onValueChange, modifier.padding(horizontal = 16.dp), valueRange = valueRange, steps = steps,
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

/** Platform presets — prefill the ingest URL; key is always pasted by the user. */
data class Platform(val name: String, val url: String)
val PLATFORMS = listOf(
    Platform("YouTube", "rtmps://a.rtmp.youtube.com/live2"),
    Platform("Twitch", "rtmp://live.twitch.tv/app"),
    Platform("Facebook", "rtmps://live-api-s.facebook.com:443/rtmp/"),
    Platform("Kick", ""),      // Kick ingest URL is per-account; paste the full URL + key
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
) {
    var tab by remember { mutableStateOf(0) }
    var sceneFor by remember { mutableStateOf<Destination?>(null) }   // scene editor opened from the Stream tab
    var destinations by remember { mutableStateOf(initialDestinations) }
    var activeId by remember { mutableStateOf(initialActiveId) }

    fun saveDest(list: List<Destination>, active: String?) {
        destinations = list; activeId = active; onPersistDestinations(list, active)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // iOS tab bar: translucent bar, hairline top edge, tinted icon+label, no Material pill.
            Column {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.separator))
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 0.dp, modifier = Modifier.height(64.dp),
                ) {
                    val c = NavigationBarItemDefaults.colors(
                        selectedIconColor = Accent, selectedTextColor = Accent,
                        unselectedIconColor = Ios.secondaryLabel, unselectedTextColor = Ios.secondaryLabel,
                        indicatorColor = Color.Transparent,
                    )
                    listOf(Icons.Filled.PlayArrow to "Stream", Icons.Filled.List to "Channels",
                        Icons.Filled.Settings to "Settings").forEachIndexed { i, (icon, name) ->
                        NavigationBarItem(tab == i, { tab = i }, colors = c,
                            icon = { Icon(icon, null) }, label = { Text(name) })
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            EmberBackdrop()
            val editingScene = sceneFor
            if (editingScene != null) {
                SceneEditor(editingScene, prefs, onPrefs, onCancel = { sceneFor = null }, onDone = { d ->
                    saveDest(destinations.map { if (it.id == d.id) d else it }, activeId)
                    StreamService.updateScene(d)          // live: layers move on air
                    sceneFor = null
                })
            } else when (tab) {
                0 -> StreamScreen(destinations, activeId, { saveDest(destinations, it) }, { tab = 1 },
                    prefs, onToggleStream, onPrefs,
                    onScene = { sceneFor = destinations.firstOrNull { it.id == activeId } ?: destinations.firstOrNull() })
                1 -> DestinationsScreen(destinations, activeId, ::saveDest, prefs, onPrefs)
                else -> SettingsScreen(prefs, onPickBubbleImage, micDevices, onPrefs, onPreviewBubble)
            }
        }
    }
}

/* ---------- Stream tab ---------- */

/** Ember glow from the oni icon's flame, behind every large title: the app's signature. */
@Composable
fun EmberBackdrop() {
    val dark = isSystemInDarkTheme()
    Box(Modifier.fillMaxWidth().height(520.dp).drawBehind {
        drawRect(Brush.radialGradient(
            listOf(Color(0xFFFF3B30).copy(alpha = if (dark) 0.42f else 0.20f), Color.Transparent),
            center = Offset(size.width * 0.9f, 0f), radius = size.width * 1.05f))
        drawRect(Brush.radialGradient(
            listOf(Color(0xFFFF8A00).copy(alpha = if (dark) 0.22f else 0.12f), Color.Transparent),
            center = Offset(0f, size.height * 0.25f), radius = size.width * 0.8f))
    })
}

fun platformOf(url: String) = when {
    "youtube" in url -> "YouTube"; "twitch" in url -> "Twitch"
    "facebook" in url -> "Facebook"; "kick" in url -> "Kick"; else -> "RTMP"
}

private val Tnum = TextStyle(fontFeatureSettings = "tnum")   // fixed-width digits: timers don't jitter

@Composable
fun StreamScreen(
    destinations: List<Destination>, activeId: String?, onSelect: (String) -> Unit, onManage: () -> Unit,
    prefs: Prefs, onToggle: () -> Unit, onPrefs: (Prefs) -> Unit, onScene: () -> Unit,
) {
    val active = destinations.firstOrNull { it.id == activeId } ?: destinations.firstOrNull()
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
            Image(painterResource(R.mipmap.ic_launcher_foreground), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).shadow(10.dp, RoundedCornerShape(14.dp), spotColor = Accent)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black)
                    .graphicsLayer { scaleX = 1.45f; scaleY = 1.45f })
            Spacer(Modifier.size(14.dp))
            Column {
                Text("Angra Streaming", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                Text(when (state) {
                    StreamService.State.LIVE -> "You're live. Make it count."
                    StreamService.State.CONNECTING -> "Reaching the server…"
                    StreamService.State.ERROR -> "Couldn't connect. Check the URL and key."
                    else -> if (active == null) "Add a channel to get started" else "Ready when you are"
                }, color = Ios.secondaryLabel, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        StudioCard(active, state, secs, onToggle, onManage)

        // At-a-glance tiles, colour-coded like iOS Settings icons.
        SectionHeader("Stream")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(R.drawable.ic_signal, AppleGreen, if (live) "${bps / 1000}" else "—",
                    if (live) "kbps upload" else "Upload", Modifier.weight(1f))
                StatTile(R.drawable.ic_video, Color(0xFF0A84FF),
                    active?.let { "${it.height}p${it.fps}" } ?: "—",
                    active?.let { (if (it.hevc) "HEVC" else "H.264") + " · ${it.videoBitrate / 1000} kbps" } ?: "Quality",
                    Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(R.drawable.ic_timer, Color(0xFFFF9500),
                    active?.delaySeconds?.let { if (it == 0) "Off" else if (it >= 60) "${it / 60}m ${it % 60}s" else "${it}s" } ?: "—",
                    "Stream delay", Modifier.weight(1f))
                StatTile(R.drawable.ic_audio, Color(0xFFBF5AF2),
                    when (prefs.audioSource) { Prefs.AUDIO_MIC -> "Mic"; Prefs.AUDIO_GAME -> "Game"; else -> "Mic + Game" },
                    if (active?.stereo != false) "Stereo · ${(active?.audioBitrate ?: 128_000) / 1000}k" else "Mono",
                    Modifier.weight(1f))
            }
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
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
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
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .border(if (on) 2.dp else 0.5.dp, if (on) Accent else Ios.separator, RoundedCornerShape(18.dp))
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
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = if (live) AppleRed else Accent, ambientColor = Color.Black)
            .background(Brush.linearGradient(listOf(Color(0xFF1C1C1E), Color(0xFF2B1510))), RoundedCornerShape(28.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
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
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier.fillMaxWidth().height(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .then(when {
                    active == null -> Modifier.background(Color.White.copy(alpha = 0.14f), shape)
                    busy -> Modifier.background(AppleRed, shape)
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
            }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
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
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(Modifier.background(color.copy(alpha = 0.18f), AppleShapes.capsule).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = if (state == StreamService.State.LIVE) a else 1f }
            .background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun StatTile(icon: Int, tint: Color, value: String, caption: String, modifier: Modifier) = Column(
    modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(14.dp),
) {
    Box(Modifier.size(30.dp).background(tint, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, style = Tnum,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(caption, fontSize = 13.sp, color = Ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun QuickToggle(icon: Int, label: String, on: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) tint else MaterialTheme.colorScheme.surface, label = "qt")
    val fg = if (on) Color.White else MaterialTheme.colorScheme.onSurface
    Column(modifier.aspectRatio(1f).background(bg, RoundedCornerShape(22.dp)).iosTap(onClick = onClick).padding(14.dp)) {
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
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "rtmps://") }
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
    var showScene by remember { mutableStateOf(false) }
    if (showScene) {
        SceneEditor(scene.copy(width = res.w, height = res.h, portrait = portrait), prefs, onPrefs,
            onCancel = { showScene = false }, onDone = { scene = it; showScene = false })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val valid = name.isNotBlank() && url.startsWith("rtmp")
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
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = AppleShapes.capsule,
                    modifier = Modifier.iosTap {
                        if (name.isBlank()) name = p.name
                        if (p.url.isNotBlank()) url = p.url
                    }) {
                    Text(p.name, fontSize = 15.sp, color = Accent, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
        Footer("Picks the server URL. Paste your stream key below — get it from the platform's dashboard (an open-source app can't generate keys for you).")

        SectionHeader("Destination")
        InsetCard {
            FieldRow("Name", name, { name = it }, "My channel")
            Hairline()
            FieldRow("Server", url, { url = it }, "rtmps://…", keyboard = KeyboardType.Uri)
            Hairline()
            FieldRow("Stream key", key, { key = it }, "Paste from dashboard", secure = true)
        }
        SectionHeader("Resolution")
        Chips(RESOLUTIONS, res, { it.toString() }) { res = it }
        SectionHeader("Frame rate")
        Chips(FPS, fps, { "$it fps" }) { fps = it }
        SectionHeader("Orientation")
        Chips(listOf(false, true), portrait, { if (it) "Portrait" else "Landscape" }) { portrait = it }
        SectionHeader("Codec")
        Chips(listOf(false, true), hevc, { if (it) "HEVC (H.265)" else "H.264" }) { hevc = it }
        SectionHeader("Keyframe interval")
        Chips(listOf(1, 2, 4), keyframe, { "${it}s" }) { keyframe = it }
        SectionHeader("Video bitrate · ${vBitrate.toInt() / 1000} kbps · CBR")
        // steps=111 snaps the 800k..12M range to 100-kbps stops.
        IosSlider(vBitrate, { vBitrate = (it / 100_000f).toInt() * 100_000f },
            valueRange = 800_000f..12_000_000f, steps = 111)
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
) {
    var previewing by remember { mutableStateOf(dev.livepipe.BubbleService.previewing) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("Settings")

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
            RowItem("Bubble shows") {
                Chips(listOf(false, true), prefs.bubbleShowsTimer, { if (it) "Timer" else "Icon" }, Modifier.width(140.dp)) {
                    onPrefs(prefs.copy(bubbleShowsTimer = it))
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
        SectionHeader("Bubble transparency · ${(prefs.bubbleTransparency * 100).toInt()}%")
        IosSlider(prefs.bubbleTransparency, { onPrefs(prefs.copy(bubbleTransparency = it)) },
            valueRange = 0f..Prefs.BUBBLE_MAX_TRANSPARENCY)
        Footer("Preview places the bubble before you go live: drag it anywhere, it snaps to the nearest edge. Edge bar: an idle bubble tucks into a slim handle; tap it to pop out. On Android 14+, share \"A single app\" when going live and the bubble stays out of the stream. The system recording indicator always stays visible.")

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
