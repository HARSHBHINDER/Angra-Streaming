package dev.livepipe.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.livepipe.Destination
import dev.livepipe.Prefs
import dev.livepipe.StreamService
import java.util.UUID

/* ---------- reusable Apple-style building blocks ---------- */

@Composable
fun SectionHeader(text: String) = Text(
    text.uppercase(),
    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    fontSize = 13.sp,
    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 6.dp),
)

@Composable
fun InsetCard(content: @Composable ColumnScope.() -> Unit) = Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = AppleShapes.card,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
) { Column(content = content) }

@Composable
fun RowItem(label: String, trailing: @Composable () -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    trailing()
}

/** Horizontal segmented chip picker (iOS segmented-control feel). */
@Composable
fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) = Row(
    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    options.forEach { opt ->
        val on = opt == selected
        Surface(
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = AppleShapes.capsule,
            modifier = Modifier.clickable { onSelect(opt) },
        ) {
            Text(
                label(opt), fontSize = 14.sp,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
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
) {
    var tab by remember { mutableStateOf(0) }
    var destinations by remember { mutableStateOf(initialDestinations) }
    var activeId by remember { mutableStateOf(initialActiveId) }

    fun saveDest(list: List<Destination>, active: String?) {
        destinations = list; activeId = active; onPersistDestinations(list, active)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(tab == 0, { tab = 0 },
                    icon = { Icon(Icons.Filled.PlayArrow, null) }, label = { Text("Stream") })
                NavigationBarItem(tab == 1, { tab = 1 },
                    icon = { Icon(Icons.Filled.List, null) }, label = { Text("Channels") })
                NavigationBarItem(tab == 2, { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> StreamScreen(destinations.firstOrNull { it.id == activeId } ?: destinations.firstOrNull(),
                    prefs, onToggleStream, onPrefs)
                1 -> DestinationsScreen(destinations, activeId, ::saveDest)
                else -> SettingsScreen(prefs, onPickBubbleImage, micDevices, onPrefs)
            }
        }
    }
}

/* ---------- Stream tab ---------- */

@Composable
fun StreamScreen(active: Destination?, prefs: Prefs, onToggle: () -> Unit, onPrefs: (Prefs) -> Unit) {
    val state by StreamService.state.collectAsState()
    val kbps by StreamService.lastBitrate.collectAsState()
    val live = state == StreamService.State.LIVE || state == StreamService.State.CONNECTING
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Angra Streaming", fontSize = 34.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = onToggle,
                enabled = active != null,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = if (live) AppleRed else AppleGreen),
                modifier = Modifier.size(180.dp),
            ) {
                Text(if (live) "STOP" else "GO LIVE", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (state) {
                StreamService.State.LIVE -> "● LIVE"
                StreamService.State.CONNECTING -> "Connecting…"
                StreamService.State.ERROR -> "Connection failed"
                else -> if (active == null) "Add a channel first" else "Ready"
            },
            color = if (state == StreamService.State.LIVE) AppleRed else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            fontSize = 15.sp,
        )

        if (state == StreamService.State.LIVE) {
            SectionHeader("Stream health")
            InsetCard {
                RowItem("Upload bitrate") {
                    Text("${kbps / 1000} kbps", color = AppleGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        SectionHeader("Active channel")
        InsetCard {
            RowItem(active?.name ?: "None") {
                Text(active?.let { "${it.height}p·${it.fps}fps" } ?: "",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }

        SectionHeader("Recording")
        InsetCard {
            RowItem("Record while streaming") {
                Switch(prefs.recordWhileStreaming, { onPrefs(prefs.copy(recordWhileStreaming = it)) })
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/* ---------- Channels tab ---------- */

@Composable
fun DestinationsScreen(
    destinations: List<Destination>,
    activeId: String?,
    save: (List<Destination>, String?) -> Unit,
) {
    var editing by remember { mutableStateOf<Destination?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (adding || editing != null) {
        DestinationEditor(
            existing = editing,
            onCancel = { adding = false; editing = null },
            onSave = { d ->
                val list = if (editing != null) destinations.map { if (it.id == d.id) d else it }
                else destinations + d
                save(list, activeId ?: d.id)
                adding = false; editing = null
            },
            onDelete = { d ->
                val list = destinations.filterNot { it.id == d.id }
                save(list, if (activeId == d.id) list.firstOrNull()?.id else activeId)
                editing = null
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Channels", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape,
                modifier = Modifier.clickable { adding = true }) {
                Icon(Icons.Filled.Add, "Add", tint = Color.White, modifier = Modifier.padding(8.dp))
            }
        }
        if (destinations.isEmpty()) {
            Text("No channels yet. Tap + to add YouTube, Twitch, Kick, Facebook or a custom RTMP URL.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(16.dp))
        }
        destinations.forEach { d ->
            Spacer(Modifier.height(8.dp))
            InsetCard {
                Row(Modifier.fillMaxWidth().clickable { save(destinations, d.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(d.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${d.height}p · ${d.fps}fps · ${d.videoBitrate / 1000}kbps",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (d.id == activeId) Text("Active", color = AppleGreen, fontSize = 14.sp)
                        Spacer(Modifier.size(10.dp))
                        Text("Edit", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { editing = d })
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun DestinationEditor(
    existing: Destination?,
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
    var overlay by remember { mutableStateOf(existing?.overlayUri ?: "") }
    var delay by remember { mutableStateOf(existing?.delaySeconds ?: 0) }
    var delayUnitMin by remember { mutableStateOf((existing?.delaySeconds ?: 0) >= 60) }
    var facecam by remember { mutableStateOf(existing?.facecam ?: false) }
    var facecamScale by remember { mutableStateOf((existing?.facecamScale ?: 30).toFloat()) }
    var facecamCorner by remember { mutableStateOf(existing?.facecamCorner ?: 2) }
    val ctx = LocalContext.current
    // OpenDocument gives a persistable URI the stream service can still read later.
    val pickOverlay = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            overlay = uri.toString()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cancel", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onCancel() })
            Text(if (existing == null) "New channel" else "Edit", fontWeight = FontWeight.SemiBold)
            Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    if (name.isNotBlank() && url.startsWith("rtmp")) onSave(
                        Destination(existing?.id ?: UUID.randomUUID().toString(), name.trim(), url.trim(), key.trim(),
                            res.w, res.h, fps, vBitrate.toInt(), aBitrate, sample, stereo, hevc, keyframe, portrait, overlay, delay,
                            facecam, facecamScale.toInt(), facecamCorner))
                })
        }
        SectionHeader("Platform")
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLATFORMS.forEach { p ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = AppleShapes.capsule,
                    modifier = Modifier.clickable {
                        if (name.isBlank()) name = p.name
                        if (p.url.isNotBlank()) url = p.url
                    }) {
                    Text(p.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        Text("Picks the server URL. Paste your stream key below — get it from the platform's dashboard (an open-source app can't generate keys for you).",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp))

        SectionHeader("Destination")
        InsetCard {
            OutlinedTextField(name, { name = it }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
            OutlinedTextField(url, { url = it }, label = { Text("RTMP/RTMPS URL") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            OutlinedTextField(key, { key = it }, label = { Text("Stream key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(12.dp))
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
        Slider(vBitrate, { vBitrate = (it / 100_000f).toInt() * 100_000f },
            valueRange = 800_000f..12_000_000f, steps = 111,
            modifier = Modifier.padding(horizontal = 16.dp))
        SectionHeader("Audio bitrate")
        Chips(AUDIO_BITRATES, aBitrate, { "${it / 1000}k" }) { aBitrate = it }
        SectionHeader("Sample rate")
        Chips(SAMPLE_RATES, sample, { "${it / 1000}kHz" }) { sample = it }
        InsetCard { RowItem("Stereo") { Switch(stereo, { stereo = it }) } }
        SectionHeader("Overlay")
        InsetCard {
            RowItem(if (overlay.isBlank()) "No overlay" else "Overlay set") {
                Row {
                    Text("Choose", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { pickOverlay.launch(arrayOf("image/*")) })
                    if (overlay.isNotBlank()) {
                        Spacer(Modifier.size(16.dp))
                        Text("Clear", color = AppleRed, modifier = Modifier.clickable { overlay = "" })
                    }
                }
            }
        }
        Text("PNG, JPG, WebP or animated GIF, composited top-right. One overlay per channel.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        // Stream-delay meter: slider on the left reads the value; the unit chip on the right switches
        // the scale to seconds (0–59) or minutes (0–10), so any value 0s–10m is reachable.
        SectionHeader("Stream delay · ${if (delay >= 60) "${delay / 60}m ${delay % 60}s" else "${delay}s"}")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (delayUnitMin) {
                Slider(((delay / 60).coerceIn(0, 10)).toFloat(), { delay = it.toInt() * 60 },
                    valueRange = 0f..10f, steps = 9, modifier = Modifier.weight(1f))
            } else {
                Slider((delay.coerceIn(0, 59)).toFloat(), { delay = it.toInt() },
                    valueRange = 0f..59f, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.size(12.dp))
            Chips(listOf(false, true), delayUnitMin, { if (it) "min" else "sec" }) {
                delayUnitMin = it
                delay = if (it) ((delay + 59) / 60) * 60 else delay.coerceAtMost(59)
            }
        }
        Text("OBS-style delay for this channel: holds the stream before sending (stops stream-sniping). Longer delays use more memory.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        SectionHeader("Facecam (front camera)")
        InsetCard { RowItem("Show facecam") { Switch(facecam, { facecam = it }) } }
        if (facecam) {
            SectionHeader("Facecam size · ${facecamScale.toInt()}%")
            Slider(facecamScale, { facecamScale = it }, valueRange = 10f..60f, steps = 9,
                modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader("Facecam corner")
            Chips(listOf(0, 1, 2, 3), facecamCorner,
                { when (it) { 0 -> "Top-left"; 1 -> "Top-right"; 2 -> "Bottom-left"; else -> "Bottom-right" } }) {
                facecamCorner = it
            }
        }
        Text("Front-camera picture-in-picture at the chosen corner. Uses more battery.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (existing != null) {
            Spacer(Modifier.height(24.dp))
            InsetCard {
                RowItem("Delete channel") {
                    Text("Delete", color = AppleRed, modifier = Modifier.clickable { onDelete(existing) })
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
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

        SectionHeader("Floating bubble")
        InsetCard {
            RowItem("Show bubble while live") {
                Switch(prefs.bubbleEnabled, { onPrefs(prefs.copy(bubbleEnabled = it)) })
            }
        }
        Chips(listOf(false, true), prefs.bubbleShowsTimer, { if (it) "Timer" else "Icon" }) {
            onPrefs(prefs.copy(bubbleShowsTimer = it))
        }
        InsetCard {
            RowItem("Custom bubble image") {
                Text("Choose", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onPickImage() })
            }
        }
        SectionHeader("Bubble transparency · ${(prefs.bubbleTransparency * 100).toInt()}%")
        Slider(prefs.bubbleTransparency, { onPrefs(prefs.copy(bubbleTransparency = it)) },
            valueRange = 0f..Prefs.BUBBLE_MAX_TRANSPARENCY, modifier = Modifier.padding(horizontal = 16.dp))
        Text("The system recording indicator stays visible regardless of this slider.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp))

        SectionHeader("Audio source")
        Chips(listOf(Prefs.AUDIO_MIC, Prefs.AUDIO_GAME, Prefs.AUDIO_MIX), prefs.audioSource,
            { when (it) { Prefs.AUDIO_MIC -> "Mic"; Prefs.AUDIO_GAME -> "Game"; else -> "Both" } }) {
            onPrefs(prefs.copy(audioSource = it))
        }
        Text("Game audio needs Android 10+; older devices always use the mic.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp))

        if (prefs.audioSource != Prefs.AUDIO_GAME) {
            SectionHeader("Microphone")
            Chips(micDevices.map { it.first }, prefs.micDeviceId,
                { id -> micDevices.firstOrNull { it.first == id }?.second ?: "Default" }) {
                onPrefs(prefs.copy(micDeviceId = it))
            }
            Text("Bluetooth, wired and USB mics appear here when connected; Default follows the system.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        SectionHeader("Network")
        InsetCard {
            RowItem("Adaptive bitrate") {
                Switch(prefs.adaptiveBitrate, { onPrefs(prefs.copy(adaptiveBitrate = it)) })
            }
        }
        Text("Drops quality automatically when upload can't keep up, instead of dropping the stream.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        SectionHeader("Recording")
        InsetCard {
            RowItem("Record while streaming") {
                Switch(prefs.recordWhileStreaming, { onPrefs(prefs.copy(recordWhileStreaming = it)) })
            }
        }
        Text("Recording uses the active channel's resolution and bitrate (single encoder keeps CPU low).",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Spacer(Modifier.height(32.dp))
    }
}
