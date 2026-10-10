package dev.livepipe.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.livepipe.Destination
import dev.livepipe.Prefs
import dev.livepipe.R
import dev.livepipe.Scene
import dev.livepipe.StreamService
import dev.livepipe.canvasAspect
import dev.livepipe.phoneAspect
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val SCREEN = 0
private const val OVERLAY = 1
private const val FACECAM = 2
private const val ALERTS = 3

/**
 * OBS-style scene editor. The canvas is the real stream shape (e.g. 16:9 for YouTube), not the phone's.
 * Tap a layer to select it, drag to move, pinch to zoom/resize. Geometry comes from [Scene], the same
 * math the stream uses, so what you arrange here is what viewers get. Mixer changes apply live.
 */
@Composable
fun SceneEditor(
    draft: Destination, prefs: Prefs, onPrefs: (Prefs) -> Unit,
    onCancel: () -> Unit, onDone: (Destination) -> Unit,
    alerts: Boolean = false,                       // open with the Super Chat alert layer selected
) {
    val ctx = LocalContext.current
    var d by remember { mutableStateOf(draft) }
    var layer by remember { mutableIntStateOf(if (alerts) ALERTS else SCREEN) }
    var fcScale by remember { mutableFloatStateOf(draft.facecamScale.toFloat()) }   // float so pinches accumulate
    val sourceAspect = remember(d.portrait) { phoneAspect(ctx, d.portrait) }
    val canvasAspect = d.canvasAspect()
    val overlayBmp = remember(d.overlayUri) {
        if (d.overlayUri.isBlank()) null else runCatching {
            val u = Uri.parse(d.overlayUri)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            o.inSampleSize = maxOf(1, maxOf(o.outWidth, o.outHeight) / 1024); o.inJustDecodeBounds = false
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }?.asImageBitmap()
        }.getOrNull()
    }
    val overlayAspect = overlayBmp?.let { it.width.toFloat() / it.height } ?: 1f
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Copy into the app's own storage: the channel keeps working even if the original is moved or deleted.
        if (uri != null) copyOverlay(ctx, uri, d.id)?.let { d = d.copy(overlayUri = it); layer = OVERLAY }
    }
    val view = Scene.screenView(sourceAspect, canvasAspect, d.screenFit, d.screenZoom, d.screenPanX, d.screenPanY)
    // The real Super Chat card in the chosen style, so placement is judged on the actual thing.
    val alertBmp = remember(prefs.alertStyle, prefs.alertShowTotal) {
        dev.livepipe.SuperChatCard.render(dev.livepipe.SuperChatCard.sample(), prefs.alertStyle, prefs.alertShowTotal).asImageBitmap()
    }
    val alertAspect = alertBmp.width.toFloat() / alertBmp.height
    val showAlerts = prefs.superChat || layer == ALERTS

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Cancel", color = Accent, modifier = Modifier.iosTap { onCancel() })
            Text("Scene", fontWeight = FontWeight.SemiBold)
            Text("Done", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.iosTap { onDone(d) })
        }

        // ---- Canvas: the stream frame at its real aspect ----
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val h: Dp = min((maxWidth / canvasAspect).value, 440f).dp
            val w: Dp = h * canvasAspect
            val px = with(LocalDensity.current) { w.toPx() }
            val py = with(LocalDensity.current) { h.toPx() }
            val cur by rememberUpdatedState(d)
            val curLayer by rememberUpdatedState(layer)
            Box(
                Modifier.size(w, h).clip(RoundedCornerShape(10.dp)).background(Color.Black)
                    .border(if (layer == SCREEN) 2.dp else 0.5.dp, if (layer == SCREEN) Accent else Ios.separator, RoundedCornerShape(10.dp))
                    .pointerInput(px, py) {
                        detectTapGestures { p ->
                            val x = p.x / px; val y = p.y / py
                            val c = cur
                            val f = fcScale / 100f
                            val oh = Scene.overlayH(c.overlayW, overlayAspect, canvasAspect)
                            val ah = c.alertW * canvasAspect / alertAspect
                            layer = when {
                                showAlerts && abs(x - c.alertX) < c.alertW / 2 && abs(y - c.alertY) < ah / 2 -> ALERTS
                                c.facecam && abs(x - c.facecamX) < f / 2 && abs(y - c.facecamY) < f / 2 -> FACECAM
                                c.overlayUri.isNotBlank() && abs(x - c.overlayX) < c.overlayW / 2 &&
                                    abs(y - c.overlayY) < oh / 2 -> OVERLAY
                                else -> SCREEN
                            }
                        }
                    }
                    .pointerInput(px, py) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val dx = pan.x / px; val dy = pan.y / py
                            val c = cur
                            d = when (curLayer) {
                                // Overlays may hang past the frame; only what's inside the frame is streamed.
                                OVERLAY -> c.copy(overlayX = (c.overlayX + dx).coerceIn(-0.5f, 1.5f),
                                    overlayY = (c.overlayY + dy).coerceIn(-0.5f, 1.5f),
                                    overlayW = (c.overlayW * zoom).coerceIn(0.05f, 2f))
                                ALERTS -> c.copy(alertX = (c.alertX + dx).coerceIn(0f, 1f), alertY = (c.alertY + dy).coerceIn(0f, 1f),
                                    alertW = (c.alertW * zoom).coerceIn(0.15f, 0.9f))
                                FACECAM -> {
                                    fcScale = (fcScale * zoom).coerceIn(10f, 60f)
                                    c.copy(facecamX = (c.facecamX + dx).coerceIn(0f, 1f), facecamY = (c.facecamY + dy).coerceIn(0f, 1f),
                                        facecamScale = fcScale.roundToInt())
                                }
                                else -> {
                                    // Zoom, then move the window opposite the drag so the content follows the finger.
                                    val z = (c.screenZoom * zoom).coerceIn(1f, Scene.MAX_ZOOM)
                                    val v = Scene.screenView(sourceAspect, canvasAspect, c.screenFit, z, c.screenPanX, c.screenPanY)
                                    c.copy(screenZoom = z, screenPanX = Scene.panFrom(v.cx - dx / v.z, v.rx),
                                        screenPanY = Scene.panFrom(v.cy - dy / v.z, v.ry))
                                }
                            }
                        }
                    },
            ) {
                // Screen layer: stylised stand-in for the gameplay (the real frame only exists once live).
                val r = view.contentRect()
                LayerBox(r[0], r[1], r[2], r[3], w, h, selected = false) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                        listOf(Color(0xFF1B2A4A), Color(0xFF3B1F5C), Color(0xFF7A2A3A)))), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.ic_video), null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                            Text("Your screen", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (overlayBmp != null) {
                    val oh = Scene.overlayH(d.overlayW, overlayAspect, canvasAspect)
                    LayerBox(d.overlayX - d.overlayW / 2, d.overlayY - oh / 2, d.overlayW, oh, w, h, layer == OVERLAY) {
                        OverlayPreview(d.overlayUri, overlayBmp)
                    }
                }
                if (showAlerts) {
                    val ah = d.alertW * canvasAspect / alertAspect
                    LayerBox(d.alertX - d.alertW / 2, d.alertY - ah / 2, d.alertW, ah, w, h, layer == ALERTS) {
                        androidx.compose.foundation.Image(alertBmp, "Super Chat", contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize())
                    }
                }
                if (d.facecam) {
                    val f = fcScale / 100f
                    LayerBox(d.facecamX - f / 2, d.facecamY - f / 2, f, f, w, h, layer == FACECAM) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_video), null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Footer("${d.width}×${d.height}${if (d.portrait) " portrait" else ""} — exactly what your viewers see. " +
            "Tap a layer, drag to move, pinch to resize.")

        // ---- Layer controls ----
        SectionHeader("Layer")
        Chips(listOf(SCREEN, OVERLAY, FACECAM, ALERTS), layer, { listOf("Screen", "Overlay", "Facecam", "Alerts")[it] }) { layer = it }
        Spacer(Modifier.height(10.dp))
        when (layer) {
            SCREEN -> {
                Chips(listOf(false, true), d.screenFit, { if (it) "Fit whole screen" else "Fill (no borders)" }) {
                    d = d.copy(screenFit = it, screenPanX = 0f, screenPanY = 0f)
                }
                SectionHeader("Zoom · ${(d.screenZoom * 100).roundToInt()}%")
                IosSlider(d.screenZoom, { d = d.copy(screenZoom = it) }, valueRange = 1f..Scene.MAX_ZOOM)
                InsetCard {
                    RowItem("Reset position and zoom") {
                        Text("Reset", color = Accent, modifier = Modifier.iosTap { d = d.copy(screenZoom = 1f, screenPanX = 0f, screenPanY = 0f) })
                    }
                }
            }
            OVERLAY -> {
                InsetCard {
                    RowItem(if (d.overlayUri.isBlank()) "No overlay" else "Overlay image") {
                        Row {
                            Text(if (d.overlayUri.isBlank()) "Choose" else "Change", color = Accent,
                                modifier = Modifier.iosTap { pick.launch(arrayOf("image/*")) })
                            if (d.overlayUri.isNotBlank()) {
                                Spacer(Modifier.width(16.dp))
                                Text("Remove", color = AppleRed, modifier = Modifier.iosTap { d = d.copy(overlayUri = "") })
                            }
                        }
                    }
                }
                if (d.overlayUri.isNotBlank()) {
                    SectionHeader("Size · ${(d.overlayW * 100).roundToInt()}% of width")
                    IosSlider(d.overlayW, { d = d.copy(overlayW = it) }, valueRange = 0.05f..2f)
                    InsetCard {
                        RowItem("Full-frame overlay") {
                            Text("Fill canvas", color = Accent, modifier = Modifier.iosTap {
                                d = d.copy(overlayX = 0.5f, overlayY = 0.5f, overlayW = 1f)
                            })
                        }
                    }
                }
                Footer("PNG, JPG, WebP or animated GIF, kept at its own shape (never stretched). Drag it past the edges if you like — only what's inside the frame is streamed. The image is copied into the app.")
            }
            ALERTS -> {
                SectionHeader("Size · ${(d.alertW * 100).roundToInt()}% of width")
                IosSlider(d.alertW, { d = d.copy(alertW = it) }, valueRange = 0.15f..0.9f)
                Footer("Where Super Chats appear on stream. Style, timing and the YouTube connection are in Super Chat on the Stream tab.")
            }
            else -> {
                InsetCard { RowItem("Show facecam") { IosSwitch(d.facecam, { d = d.copy(facecam = it); layer = FACECAM }) } }
                if (d.facecam) {
                    SectionHeader("Size · ${fcScale.roundToInt()}%")
                    IosSlider(fcScale, { fcScale = it; d = d.copy(facecamScale = it.roundToInt()) }, valueRange = 10f..60f)
                }
                Footer("Front camera, composited on top of everything. Uses more battery.")
            }
        }

        // ---- Audio mixer ----
        SectionHeader("Audio mixer")
        InsetCard {
            MixerRow(R.drawable.ic_mic, "Microphone", prefs.micVolume, prefs.audioSource != Prefs.AUDIO_GAME) {
                val p = prefs.copy(micVolume = it); onPrefs(p); StreamService.updateVolumes(p)
            }
            Hairline()
            MixerRow(R.drawable.ic_audio, "Game & system", prefs.gameVolume, prefs.audioSource != Prefs.AUDIO_MIC) {
                val p = prefs.copy(gameVolume = it); onPrefs(p); StreamService.updateVolumes(p)
            }
        }
        Footer("Changes apply instantly, even while live. Audio source (Mic / Game / Both) is in Settings.")
        Spacer(Modifier.height(32.dp))
    }
}

/** A layer placed by canvas fractions; [selected] draws the accent outline. */
@Composable
private fun LayerBox(
    l: Float, t: Float, w: Float, h: Float, cw: Dp, ch: Dp, selected: Boolean, content: @Composable () -> Unit,
) {
    // Layers may be bigger than the canvas (a zoomed screen) or hang off its edge, so measure them at their
    // true size and place them freely; a normal Box child would be squeezed to the canvas bounds.
    Box(
        Modifier.layout { m, _ ->
            val p = m.measure(Constraints.fixed((cw * w).roundToPx().coerceAtLeast(1), (ch * h).roundToPx().coerceAtLeast(1)))
            layout(0, 0) { p.place((cw * l).roundToPx(), (ch * t).roundToPx()) }
        },
    ) {
        Box(Modifier.fillMaxSize().then(if (selected) Modifier.border(2.dp, Accent) else Modifier)) { content() }
    }
}

@Composable
private fun MixerRow(icon: Int, label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(if (enabled) Accent else Ios.switchOff, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f))
            Text(if (!enabled) "Off" else if (value == 0f) "Muted" else "${(value * 100).roundToInt()}%",
                color = Ios.secondaryLabel, fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
            // Quick mute: tap toggles between silent and 100%.
            Box(Modifier.size(28.dp).background(Ios.segmentTrack, CircleShape).iosTap(enabled) { onChange(if (value == 0f) 1f else 0f) },
                contentAlignment = Alignment.Center) {
                Text(if (value == 0f) "🔇" else "🔊", fontSize = 13.sp)
            }
        }
        Box(Modifier.alpha(if (enabled) 1f else 0.4f)) {
            IosSlider(value, { if (enabled) onChange(it) }, valueRange = 0f..1.5f)
        }
    }
}

/** Overlay files live in the app (files/overlays/<channelId>_<time>.<ext>), never at the picked location. */
fun copyOverlay(ctx: android.content.Context, src: Uri, channelId: String): String? = runCatching {
    val ext = when (ctx.contentResolver.getType(src)) {
        "image/gif" -> "gif"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg"
    }
    val dir = java.io.File(ctx.filesDir, "overlays").apply { mkdirs() }
    val out = java.io.File(dir, "${channelId}_${System.currentTimeMillis()}.$ext")
    ctx.contentResolver.openInputStream(src)?.use { i -> out.outputStream().use { i.copyTo(it) } } ?: return null
    Uri.fromFile(out).toString()
}.getOrNull()

/** Removes a channel's overlay copies (on delete). */
fun deleteOverlays(ctx: android.content.Context, channelId: String) {
    java.io.File(ctx.filesDir, "overlays").listFiles { f -> f.name.startsWith("${channelId}_") }?.forEach { it.delete() }
}

/** Overlay preview; animated GIF/WebP play here just like on stream (API 28+), others show still. */
@Composable
private fun OverlayPreview(uri: String, still: androidx.compose.ui.graphics.ImageBitmap) {
    val ctx = LocalContext.current
    val animated = remember(uri) {
        if (android.os.Build.VERSION.SDK_INT < 28) null else runCatching {
            android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(ctx.contentResolver, Uri.parse(uri)))
                .takeIf { it is android.graphics.drawable.AnimatedImageDrawable }
        }.getOrNull()
    }
    if (animated == null) Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    else androidx.compose.ui.viewinterop.AndroidView({ c ->
        android.widget.ImageView(c).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_XY        // the box already has the image's shape
            setImageDrawable(animated); (animated as android.graphics.drawable.AnimatedImageDrawable).start()
        }
    }, Modifier.fillMaxSize())
}
