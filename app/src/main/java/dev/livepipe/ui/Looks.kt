package dev.livepipe.ui

import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import android.graphics.BlurMaskFilter
import android.graphics.DiscretePathEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Theme language. A theme is not a palette: it is a world (backdrop scene), a material for surfaces, an edge,
 * a corner geometry and a typographic voice. Every card, tile, hero, button, segmented control and the tab bar
 * render through [themed], so a theme reaches every corner of the app.
 *
 * Lightness: each scene's still artwork is recorded once into a cached layer; per tick (24 fps) only the small
 * moving parts redraw, and the heavy looks (aurora, glass wallpaper, caustics, marble) are GPU shaders. Glass
 * surfaces reference one shared blurred copy of the backdrop instead of redrawing it, and surfaces whose edge
 * doesn't move never redraw at all.
 */

enum class Scene { WA, MATTE, WHITE, EMBER, TRON, CYBER, WASHI, NIGHT, OUTRUN, AURORA, GLASS, OCEAN, SAKURA, RAIN, VAPOR, SNOW, DUNES }
enum class Edge { NONE, HAIRLINE, PULSE, GLITCH, BRUSH, BLADE, NEON, CHROMA, SPECULAR }
enum class Fill { SOLID, GLASS, PAPER }
enum class Corner { ROUND, CUT, BLADE }
enum class Part { CARD, HERO, TILE, SMALL, BAR }

class Look(
    val scene: Scene,
    val edge: Edge,
    val fill: Fill = Fill.SOLID,
    val corner: Corner = Corner.ROUND,
    val radius: Dp = 20.dp,
    val edgeColors: List<Color> = listOf(Color.White.copy(alpha = 0.08f)),
    val hero: List<Color> = listOf(Color(0xFF1C1C1E), Color(0xFF2B1510)),
    val font: FontFamily = FontFamily.Default,
    val titleFont: FontFamily = font,
    val titleCaps: Boolean = false,
    val titleTracking: Float = -0.6f,
    val titleWeight: FontWeight = FontWeight.Bold,
    val titleBrush: List<Color>? = null,
    val seal: Color? = null,                 // hanko seal before large titles (ninja themes)
    val onBrush: Color = Color.White,        // text on the primary button
    val outlineButton: Boolean = false,      // primary button drawn as a lit outline (Tron)
    val shadow: Color = Color.Transparent,   // soft float shadow under surfaces (white, sakura)
    val floatingBar: Boolean = false,        // iOS 26 floating glass tab bar that bends the content under it
    val frost: Dp = 26.dp,                   // glass blur; Liquid Glass is the clear variant (little diffusion)
    val titleScale: Float = 1f,              // wide display faces are set smaller so titles fit
    val stamp: String? = null,               // Wa: a seal-red hanko stamp in front of the app name
)

/* ---------------- shared scene host ---------------- */

/** The moment a scene shows when Live theme has never run: theme previews, and the backdrop at first launch. */
const val STILL = 12f

/**
 * Scene time for Live theme. Ticks at ~10 fps only while [live] is on and the app is resumed and focused (not in the
 * background, not behind a dialog or the other half of split screen); otherwise it holds the exact frame it stopped
 * on, so nothing redraws and the theme costs nothing.
 */
@Composable
fun rememberSceneClock(live: Boolean): State<Float> {
    val t = rememberSaveable { mutableFloatStateOf(STILL) }
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val run = live && resumed && LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(run) {
        if (!run) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            kotlinx.coroutines.delay(100)
            withFrameNanos { now -> t.floatValue += (now - last) / 1e9f; last = now }
        }
    }
    return t
}

class SceneHost(val time: State<Float> = mutableFloatStateOf(STILL)) {
    var size by mutableStateOf(Size.Zero)      // the backdrop's size
    var origin by mutableStateOf(Offset.Zero)  // the backdrop's position in the root
    /** The backdrop blurred (once per tick when live); every glass surface shows its slice of this one layer. */
    var frosted: GraphicsLayer? = null
    /** The whole screen (backdrop + content), for the floating glass tab bar to refract. */
    var screen: GraphicsLayer? = null
}
val LocalScene = staticCompositionLocalOf<SceneHost?> { null }

/* ---------------- shapes ---------------- */

/** Corner geometry. ROUND is Apple's continuous ("squircle") corner, not a circular arc. */
class LookShape(private val corner: Corner, private val r: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (r >= 999.dp) return RoundedCornerShape(50).createOutline(size, layoutDirection, density)
        val w = size.width; val h = size.height
        val p = with(density) { r.toPx() }.coerceAtMost(min(w, h) / 2)
        return Outline.Generic(Path().apply {
            when (corner) {
                Corner.ROUND -> {
                    val e = min(p * 1.28f, min(w, h) / 2); val c = e * 0.36f
                    moveTo(e, 0f); lineTo(w - e, 0f); cubicTo(w - c, 0f, w, c, w, e)
                    lineTo(w, h - e); cubicTo(w, h - c, w - c, h, w - e, h)
                    lineTo(e, h); cubicTo(c, h, 0f, h - c, 0f, h - e)
                    lineTo(0f, e); cubicTo(0f, c, c, 0f, e, 0f)
                }
                // Cyberpunk panel: chamfered top-right and bottom-left, hard elsewhere.
                Corner.CUT -> { moveTo(0f, 0f); lineTo(w - p, 0f); lineTo(w, p); lineTo(w, h); lineTo(p, h); lineTo(0f, h - p) }
                // Katana: one long blade cut at the top-left.
                Corner.BLADE -> { val a = p * 1.7f; moveTo(a, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(0f, h); lineTo(0f, a) }
            }
            close()
        })
    }
}

fun Look.radiusFor(role: Part): Dp = when (role) {
    Part.HERO -> radius + 8.dp
    Part.SMALL -> radius * 0.62f
    Part.BAR -> if (corner == Corner.ROUND) 999.dp else radius
    else -> radius
}
fun Look.shape(role: Part): Shape = LookShape(corner, radiusFor(role))

/* ---------------- textures (generated once) ---------------- */

private object Textures {
    private fun noise(w: Int, h: Int, seed: Long, f: (java.util.Random, Int, Int) -> Int): ImageBitmap {
        val r = java.util.Random(seed)
        val px = IntArray(w * h) { i -> f(r, i % w, i / w) }
        return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    /** Film grain: random white specks. */
    val grain by lazy { noise(192, 192, 7) { r, _, _ -> (r.nextInt(70) shl 24) or 0xFFFFFF } }
    /** Washi paper: soft fibres and specks. */
    val paper by lazy { noise(256, 256, 23) { r, _, _ -> val a = r.nextInt(100); if (a > 93) (40 shl 24) or 0x8B7B60 else (a / 6 shl 24) or 0xFFFFFF } }

    fun brush(img: ImageBitmap) = ShaderBrush(ImageShader(img, TileMode.Repeated, TileMode.Repeated))
}

/* ---------------- native paints (glow, brush) ---------------- */

private val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    style = android.graphics.Paint.Style.STROKE; strokeJoin = android.graphics.Paint.Join.ROUND; strokeCap = android.graphics.Paint.Cap.ROUND
}
private val blurs = HashMap<Int, BlurMaskFilter>()
private fun blur(px: Float) = blurs.getOrPut(px.toInt().coerceAtLeast(1)) { BlurMaskFilter(px.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL) }

/** Stroke with a real gaussian bloom (light leaking off a lit line). */
private fun DrawScope.glow(path: Path, color: Color, width: Float, bloom: Float, shader: Shader? = null) = drawIntoCanvas { c ->
    glowPaint.shader = shader; glowPaint.color = color.toArgb(); glowPaint.strokeWidth = width
    glowPaint.maskFilter = if (bloom > 0f) blur(bloom) else null; glowPaint.pathEffect = null
    glowPaint.style = android.graphics.Paint.Style.STROKE
    c.nativeCanvas.drawPath(path.asAndroidPath(), glowPaint)
}

private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

private fun hash(i: Int): Float { val x = sin(i * 12.9898f + 78.233f) * 43758.547f; return x - floor(x) }
private fun frac(x: Float) = x - floor(x)

/* ---------------- surfaces ---------------- */

/**
 * Paints this element as a themed surface: the theme's material (solid, real lensed glass, paper), its corner
 * geometry and its edge (light pulse, glitch, brush, blade, gold, hologram…). [tint] fills it with a solid colour.
 * Put it before inner padding, after outer padding.
 */
fun Modifier.themed(role: Part = Part.CARD, selected: Boolean = false, tint: Color? = null): Modifier = composed {
    val th = LocalTheme.current
    val look = th.p.look
    val host = LocalScene.current
    val shape = remember(look, role) { look.shape(role) }
    val pos = remember { mutableStateOf(Offset.Zero) }
    val glass = look.fill == Fill.GLASS && tint == null && host != null
    val layer = if (glass) rememberGraphicsLayer() else null
    val lens = remember(glass) { if (glass && Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(LENS) }.getOrNull() else null }
    // Only edges that move read the clock; when Live theme is off the clock never changes, so nothing redraws.
    val animated = look.edge == Edge.PULSE || look.edge == Edge.GLITCH || look.edge == Edge.BLADE ||
        (look.edge == Edge.NEON && look.edgeColors.size > 1)
    val accent = th.p.accent
    val float = look.shadow.alpha > 0f && tint == null && role != Part.SMALL
    this
        .then(when {
            float -> Modifier.shadow(if (role == Part.HERO) 26.dp else 14.dp, shape, clip = false, ambientColor = look.shadow, spotColor = look.shadow)
            role == Part.HERO -> Modifier.shadow(24.dp, shape, clip = false, spotColor = accent.copy(alpha = 0.7f), ambientColor = Color.Black)
            else -> Modifier
        })
        .then(if (glass) Modifier.onGloballyPositioned { pos.value = it.positionInRoot() } else Modifier)
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val pm = PathMeasure().apply { setPath(path, false) }
            val rPx = look.radiusFor(role).toPx()
            if (layer != null && lens != null && Build.VERSION.SDK_INT >= 33) {
                // The blur is shared (host.frosted); each surface only adds its own lens at the rim, on the GPU.
                lens.setFloatUniform("size", size.width, size.height)
                lens.setFloatUniform("radius", rPx.coerceAtMost(min(size.width, size.height) / 2))
                lens.setFloatUniform("bend", min(20.dp.toPx(), min(size.width, size.height) / 3))
                layer.renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(lens, "content").asComposeRenderEffect()
            }
            // Plain Density: recording with the node's own draw scope as density loops forever.
            val d = Density(density, fontScale)
            val px = IntSize(size.width.toInt(), size.height.toInt())
            onDrawWithContent {
                when {
                    tint != null -> drawPath(path, tint)
                    layer != null && host != null -> {
                        // Real glass: the shared frosted backdrop, exactly the slice behind this surface, lens-bent at the
                        // rim. It references the backdrop's layer, so the glass stays live without redrawing here.
                        val frosted = host.frosted
                        if (frosted != null) layer.record(d, layoutDirection, px) {
                            translate(host.origin.x - pos.value.x, host.origin.y - pos.value.y) { drawLayer(frosted) }
                        }
                        clipPath(path) {
                            if (frosted != null) drawLayer(layer)
                            val glassTint = if (role == Part.HERO) Color.Black.copy(alpha = 0.42f) else th.p.card
                            drawRect(if (frosted != null) glassTint else glassTint.copy(alpha = max(glassTint.alpha, 0.85f)))
                            // Thickness: bright inner top, faint shaded bottom, like light through a slab of glass.
                            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (th.dark) 0.10f else 0.32f), Color.Transparent), 0f, size.height * 0.4f))
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.06f)), size.height * 0.6f, size.height))
                        }
                    }
                    else -> drawFill(look, th.p, path, role, th.dark)
                }
                drawContent()
                drawEdge(look, path, pm, rPx, if (animated) host?.time?.value ?: STILL else STILL, role)
                if (selected) {
                    if (look.edge == Edge.PULSE || look.edge == Edge.NEON || look.edge == Edge.GLITCH || look.edge == Edge.CHROMA)
                        glow(path, accent.copy(alpha = 0.8f), 2.dp.toPx(), 6.dp.toPx())
                    drawPath(path, accent, style = Stroke(2.dp.toPx()))
                }
            }
        }
}

private fun DrawScope.drawFill(look: Look, p: Palette, path: Path, role: Part, dark: Boolean) {
    if (role == Part.HERO) { drawPath(path, Brush.linearGradient(look.hero, Offset.Zero, Offset(size.width, size.height))); return }
    drawPath(path, if (p.dark == null && !dark) Color.White else p.card)   // Original follows the system: white cards in light mode
    if (look.fill == Fill.PAPER) clipPath(path) { drawRect(Textures.brush(Textures.paper), alpha = 0.55f) }
}

// Liquid Glass lens: near the rim the backdrop is pulled inward (the edge magnifies, like a thick glass
// lens) with a slight chromatic split, the way light bends through a curved edge. Interior stays flat.
private const val LENS = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bend;
half4 main(float2 p) {
    float2 h = size * 0.5;
    float2 q = abs(p - h) - (h - radius);
    float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float e = clamp(1.0 + sd / bend, 0.0, 1.0);
    float2 dir = normalize(p - h + float2(0.0001, 0.0001));
    float k = e * e * e * bend;
    half r = content.eval(p - dir * k * 1.08).r;
    half g = content.eval(p - dir * k).g;
    half b = content.eval(p - dir * k * 0.92).b;
    return half4(r, g, b, 1.0);
}
"""

/**
 * Liquid Glass capsule over live content: the recorded [screen] slice behind it, blurred and lens-bent at the rim
 * (with a slight chromatic split), a pale tint, a bright inner top and a specular rim. Because it
 * references the screen layer, scrolling content visibly flows and bends through the glass with no extra drawing.
 */
fun Modifier.liquidGlass(screen: GraphicsLayer?): Modifier = composed {
    val host = LocalScene.current
    val dark = LocalTheme.current.dark
    val bar = rememberGraphicsLayer()
    val lens = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(LENS) }.getOrNull() else null }
    val pos = remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(50)
    this.onGloballyPositioned { pos.value = it.positionInRoot() }
        .shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            if (Build.VERSION.SDK_INT >= 31) {
                val r = 14.dp.toPx()
                val blurFx = android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                bar.renderEffect = if (lens != null && Build.VERSION.SDK_INT >= 33) {
                    lens.setFloatUniform("size", size.width, size.height)
                    lens.setFloatUniform("radius", size.height / 2)
                    lens.setFloatUniform("bend", size.height * 0.45f)
                    android.graphics.RenderEffect.createChainEffect(
                        android.graphics.RenderEffect.createRuntimeShaderEffect(lens, "content"), blurFx).asComposeRenderEffect()
                } else blurFx.asComposeRenderEffect()
            }
            val d = Density(density, fontScale)
            val px = IntSize(size.width.toInt(), size.height.toInt())
            onDrawWithContent {
                if (screen != null) bar.record(d, layoutDirection, px) { translate(-pos.value.x, -pos.value.y) { drawLayer(screen) } }
                clipPath(path) {
                    if (screen != null) drawLayer(bar)
                    drawRect(if (dark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = if (screen != null) 0.28f else 0.8f))
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.4f), Color.Transparent), 0f, size.height * 0.5f))
                }
                val lx = -0.35f; val ly = -1f
                val n = sqrt(lx * lx + ly * ly).coerceAtLeast(0.01f)
                val m = size.width / 2
                drawPath(path, Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.5f)),
                    Offset(size.width / 2 + lx / n * m, size.height / 2 + ly / n * m), Offset(size.width / 2 - lx / n * m, size.height / 2 - ly / n * m)),
                    style = Stroke(1.2.dp.toPx()))
                drawContent()
            }
        }
}

/* ---------------- edges ---------------- */

private fun DrawScope.drawEdge(look: Look, path: Path, pm: PathMeasure, rPx: Float, t: Float, role: Part) {
    val c0 = look.edgeColors.getOrElse(0) { Color.White }
    val c1 = look.edgeColors.getOrElse(1) { c0 }
    val w = size.width; val h = size.height
    when (look.edge) {
        Edge.NONE -> Unit
        Edge.HAIRLINE -> drawPath(path, c0, style = Stroke(0.75.dp.toPx()))
        Edge.PULSE -> {
            // Tron: a lit line with bloom, and light pulses travelling around it (GMUNK's "travelling linework").
            glow(path, c0.copy(alpha = 0.55f), 2.dp.toPx(), 7.dp.toPx())
            drawPath(path, c0.copy(alpha = 0.95f), style = Stroke(1.dp.toPx()))
            val len = pm.length
            if (len > 0f) for (k in 0..1) {
                val head = frac(t * (if (role == Part.HERO) 0.07f else 0.11f) + k * 0.5f + hash(w.toInt()) * 0.3f) * len
                for (s in 0..2) {                                // comet: bright head, fading tail
                    val a = head - len * 0.05f * (s + 1); val b = head - len * 0.05f * s
                    val seg = Path()
                    if (a < 0f) { pm.getSegment(len + a, len, seg, true); pm.getSegment(0f, max(b, 0f), seg, true) }
                    else pm.getSegment(a, b, seg, true)
                    val alpha = 1f - s * 0.33f
                    glow(seg, Color(0xFFDFFBFF).copy(alpha = alpha), 2.5.dp.toPx(), 9.dp.toPx())
                    drawPath(seg, Color.White.copy(alpha = alpha), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Edge.GLITCH -> {
            // Cyberpunk: yellow frame, red ticks on the chamfers, and an RGB-split glitch every few seconds.
            if (frac(t / 3.7f) * 3.7f < 0.16f) {
                val dx = sin(t * 90f) * 4.dp.toPx()
                translate(dx, 0f) { drawPath(path, Color(0xFFFF003C).copy(alpha = 0.8f), style = Stroke(1.dp.toPx())) }
                translate(-dx, 0f) { drawPath(path, Color(0xFF00F0FF).copy(alpha = 0.8f), style = Stroke(1.dp.toPx())) }
            }
            drawPath(path, c0.copy(alpha = 0.9f), style = Stroke(1.dp.toPx()))
            val p = min(rPx, min(w, h) / 2)
            if (look.corner == Corner.CUT) {
                drawLine(Color(0xFFFF003C), Offset(w - p, 0f), Offset(w, p), 3.dp.toPx())
                drawLine(Color(0xFFFF003C), Offset(0f, h - p), Offset(p, h), 3.dp.toPx())
            }
            drawLine(c0, Offset(0f, h * 0.2f), Offset(0f, h * 0.2f + 14.dp.toPx()), 3.dp.toPx())
        }
        Edge.BRUSH -> drawIntoCanvas { c ->
            // Sumi-e: several jittered passes of ink, one dry and broken, so the frame reads hand-brushed.
            for (k in 0..2) {
                glowPaint.shader = null; glowPaint.maskFilter = null; glowPaint.style = android.graphics.Paint.Style.STROKE
                glowPaint.color = c0.copy(alpha = 0.32f + k * 0.16f).toArgb()
                glowPaint.strokeWidth = (2.2f - k * 0.55f).dp.toPx()
                glowPaint.pathEffect = if (k == 0) android.graphics.ComposePathEffect(
                    android.graphics.DashPathEffect(floatArrayOf(40f, 6f, 18f, 4f), hash(k) * 30f),
                    DiscretePathEffect(9f, 1.6f)) else DiscretePathEffect(7f + k * 3f, 1.1f)
                c.nativeCanvas.save(); c.nativeCanvas.translate(hash(k + 3) * 1.6f - 0.8f, hash(k + 9) * 1.6f - 0.8f)
                c.nativeCanvas.drawPath(path.asAndroidPath(), glowPaint)
                c.nativeCanvas.restore()
            }
            glowPaint.pathEffect = null
        }
        Edge.BLADE -> {
            // Katana: a crimson cutting edge along the blade cut and top, and a glint that sweeps across.
            drawPath(path, Color.White.copy(alpha = 0.06f), style = Stroke(1.dp.toPx()))
            val a = min(rPx * 1.7f, min(w, h) / 2)
            val edge = Path().apply { moveTo(0f, a); lineTo(a, 0f); lineTo(w * 0.62f, 0f) }
            glow(edge, c0.copy(alpha = 0.7f), 1.5.dp.toPx(), 5.dp.toPx())
            drawPath(edge, c0, style = Stroke(1.2.dp.toPx()))
            val g = frac((t + hash(w.toInt()) * 5f) / 7f) * 7f
            if (g < 1.2f) clipPath(path) {
                val x = -w * 0.4f + (g / 1.2f) * w * 1.8f
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent),
                    Offset(x, 0f), Offset(x + w * 0.22f, h)))
            }
        }
        Edge.NEON -> {
            // Neon tube (Matrix phosphor, blacklight thread): bloom + core; two-tone tubes flicker now and then.
            val twoTone = c1 != c0
            val flick = if (twoTone) 0.8f + 0.2f * sin(t * 13f + w) * (if (frac(t / 4.3f) < 0.04f) 1f else 0.15f) else 1f
            val sh = if (twoTone) android.graphics.LinearGradient(0f, 0f, w, h, c0.toArgb(), c1.toArgb(), Shader.TileMode.CLAMP) else null
            glow(path, c0.copy(alpha = 0.6f * flick), 2.dp.toPx(), 7.dp.toPx(), sh)
            glow(path, c0.copy(alpha = flick), 1.dp.toPx(), 0f, sh)
        }
        Edge.CHROMA -> {
            // Vaporwave VHS: the frame doubled in pink and cyan, slightly misregistered.
            translate(-1.5.dp.toPx(), 0f) { drawPath(path, c0.copy(alpha = 0.85f), style = Stroke(1.2.dp.toPx())) }
            translate(1.5.dp.toPx(), 0f) { drawPath(path, c1.copy(alpha = 0.85f), style = Stroke(1.2.dp.toPx())) }
        }
        Edge.SPECULAR -> {
            // Liquid Glass rim: brightest where it faces the light, from the upper left.
            val lx = -0.35f; val ly = -1f
            val n = sqrt(lx * lx + ly * ly).coerceAtLeast(0.01f)
            val dd = Offset(lx / n, ly / n); val cx = w / 2; val cy = h / 2; val m = max(w, h) / 2
            drawPath(path, Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.45f)),
                Offset(cx + dd.x * m, cy + dd.y * m), Offset(cx - dd.x * m, cy - dd.y * m)), style = Stroke(1.6.dp.toPx()))
            // The far rim catches a second, fainter light, as thick glass does.
            drawPath(path, Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f)),
                Offset(cx, cy), Offset(cx - dd.x * m, cy - dd.y * m)), style = Stroke(0.8.dp.toPx()))
        }
    }
}

/* ---------------- GPU scenes (AGSL, Android 13+) ---------------- */

private const val FBM = """
float fbm(float2 p) { float v = 0.0; float a = 0.5; for (int i = 0; i < 4; i++) { v += a * vn(p); p *= 2.02; a *= 0.5; } return v; }
"""

private const val NOISE = """
float h1(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float vn(float2 p) {
    float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h1(i), h1(i + float2(1.0, 0.0)), u.x), mix(h1(i + float2(0.0, 1.0)), h1(i + float2(1.0, 1.0)), u.x), u.y);
}
"""

// Aurora: curtains whose lower hem folds and drifts, vertical ray striations, green (O, ~100 km) at the hem, red
// (O, higher) in the crowns, a violet nitrogen fringe at the very edge; stars and a dark ridge.
private const val AURORA = """
uniform float2 res;
uniform float t;
""" + NOISE + """
half4 main(float2 fc) {
    float2 uv = fc / res;
    float ar = res.x / res.y;
    float3 col = mix(float3(0.005, 0.015, 0.04), float3(0.02, 0.07, 0.11), uv.y);
    float s = h1(floor(fc / 2.5));
    col += float3(step(0.9975, s) * (0.55 + 0.45 * sin(t * 1.7 + s * 80.0)));
    for (int L = 0; L < 3; L++) {
        float fl = float(L);
        float x = uv.x * ar * 1.4 + fl * 2.9;
        float base = 0.30 + fl * 0.07 + 0.07 * sin(x * 1.1 + t * 0.11 + fl) + 0.05 * (vn(float2(x * 1.7 + t * 0.05, fl * 3.0)) - 0.5);
        float d = base - uv.y;
        if (d > -0.01 && d < 0.5) {
            float rays = vn(float2(x * 55.0 + t * 0.35, fl * 7.0 + t * 0.2));
            rays = 0.35 + 0.95 * rays * rays * rays;
            float body = exp(-max(d, 0.0) * (6.0 + fl * 2.0));
            float edge = smoothstep(-0.01, 0.012, d);
            float pulse = 0.55 + 0.45 * sin(x * 0.6 + t * 0.17 + fl * 2.0);
            float a = body * edge * rays * pulse * (1.0 - fl * 0.28);
            float3 c = mix(float3(0.18, 1.0, 0.55), float3(0.85, 0.2, 0.42), smoothstep(0.10, 0.36, d));
            c = mix(float3(0.5, 0.35, 1.0), c, smoothstep(-0.005, 0.02, d));
            col += c * a * 0.9;
        }
    }
    float ridge = 0.84 + 0.035 * sin(uv.x * 9.0) + 0.03 * vn(float2(uv.x * 14.0, 1.0));
    if (uv.y > ridge) col = float3(0.0, 0.006, 0.012) + col * 0.06;
    return half4(half3(col), 1.0);
}
"""

// Liquid Glass wallpaper: a calm, ambient field of soft colour that drifts very slowly, with a fine grain so the
// frosted glass over it reads as matte, not clear.
private const val GLASS_BG = """
uniform float2 res;
uniform float t;
""" + NOISE + """
half4 main(float2 fc) {
    float2 uv = fc / res;
    float ar = res.x / res.y;
    float2 q = float2(uv.x * ar, uv.y);
    float3 col = float3(0.92, 0.93, 0.96);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float2 c = float2(ar * (0.5 + 0.42 * sin(t * (0.031 + fi * 0.007) + fi * 1.9)), 0.5 + 0.42 * cos(t * (0.026 + fi * 0.006) + fi * 2.6));
        float3 k = fi < 0.5 ? float3(0.47, 0.62, 1.0) : (fi < 1.5 ? float3(0.74, 0.58, 1.0) : (fi < 2.5 ? float3(1.0, 0.70, 0.62) : (fi < 3.5 ? float3(0.52, 0.86, 0.92) : float3(1.0, 0.82, 0.92))));
        float2 d = q - c;
        col = mix(col, k, exp(-dot(d, d) / 0.06) * 0.8);
    }
    col += (h1(fc) - 0.5) * 0.02;
    return half4(half3(col), 1.0);
}
"""

// Deep Ocean: sunbeams and the rippling surface far above, marine snow drifting down; a humpback passes in the blue
// haze, a manta glides overhead, a trident-bearing sea warrior swims by, bioluminescent jellyfish pulse upward, a
// school of silver fish mills in a living ring, kelp sways from the sea floor.
private const val OCEAN = """
uniform float2 res;
uniform float t;
""" + NOISE + FBM + """
float sdBox(float2 p, float2 b) { float2 d = abs(p) - b; return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0); }
float sdSeg(float2 p, float2 a, float2 b) { float2 pa = p - a; float2 ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float2 rot(float2 v, float2 d) { return float2(dot(v, d), dot(v, float2(-d.y, d.x))); }
float3 water(float y) { return mix(float3(0.06, 0.36, 0.52), float3(0.0, 0.025, 0.06), smoothstep(0.0, 1.0, y)); }
float fishd(float2 q) {
    float b = (length(q / float2(0.5, 0.19)) - 1.0) * 0.19;
    float tl = max(abs(q.y) - (-0.42 - q.x) * 0.77, max(q.x + 0.42, -0.68 - q.x));
    return min(b, tl);
}
half4 main(float2 fc) {
    float2 uv = fc / res;
    float ar = res.x / res.y;
    float p = (uv.x - 0.5) * ar;
    float2 sp = float2(p, uv.y);
    float3 col = water(uv.y);
    float rays = pow(vn(float2((p + uv.y * 0.35) * 16.0, t * 0.12)), 3.0) * (1.0 - smoothstep(0.0, 0.85, uv.y));
    col += float3(0.5, 0.85, 1.0) * rays * 0.28;
    float wt = fract(t * 0.008 + 0.3);
    float2 wp = float2(mix(-0.6, 0.6, wt), 0.74);
    float ws = 0.32;
    float2 wq = (sp - wp) / ws; wq.y = -wq.y;
    if (abs(wq.x) < 0.8 && abs(wq.y) < 0.45) {
        float hh = clamp(wq.x + 0.5, 0.0, 1.0);
        float rad = mix(0.015, 0.12, smoothstep(0.0, 0.55, hh)) * (1.0 - 0.35 * smoothstep(0.75, 1.0, hh));
        float d = sdSeg(wq, float2(-0.5, 0.0), float2(0.5, -0.02)) - rad;
        d = min(d, sdSeg(wq, float2(-0.5, 0.0), float2(-0.62, 0.1)) - 0.015);
        d = min(d, sdSeg(wq, float2(-0.5, 0.0), float2(-0.62, -0.1)) - 0.015);
        d = min(d, sdSeg(wq, float2(0.22, -0.07), float2(-0.02, -0.26)) - 0.02);
        col = mix(col, mix(float3(0.01, 0.05, 0.09), water(uv.y), 0.55), clamp(0.5 - d * ws * res.y, 0.0, 1.0));
    }
    float mt = fract(t * 0.018);
    float2 mp = float2(mix(0.42, -0.42, mt), mix(0.62, 0.28, mt));
    float2 mdir = normalize(float2(-0.8, -0.32));
    float ms = 0.075;
    float2 mq = rot(sp - mp, mdir) / ms;
    if (abs(mq.x) < 2.0 && abs(mq.y) < 1.3) {
        float span = 1.0 - 0.18 * (0.5 + 0.5 * sin(t * 1.3));
        float d = (abs(mq.x + 0.35 * abs(mq.y) - 0.1) / 0.42 + abs(mq.y) / span - 1.0) * 0.4;
        d = min(d, sdSeg(mq, float2(-0.2, 0.0), float2(-1.6, 0.0)) - 0.02);
        d = min(d, length(mq - float2(0.5, 0.18)) - 0.07);
        d = min(d, length(mq - float2(0.5, -0.18)) - 0.07);
        float pd = d * ms * res.y;
        col = mix(col, mix(float3(0.02, 0.06, 0.1), water(uv.y), 0.35), clamp(0.5 - pd, 0.0, 1.0));
        col += float3(0.3, 0.6, 0.75) * exp(-abs(pd) * 0.35) * 0.15;
    }
    float at = fract(t * 0.025 + 0.6);
    float2 ap = float2(mix(0.45, -0.45, at), 0.4 + 0.02 * sin(t * 0.4));
    float as = 0.09;
    float2 aq = (ap - sp) / as;
    if (abs(aq.x) < 1.4 && abs(aq.y) < 0.6) {
        float kick = 0.06 * sin(t * 4.0);
        float d = sdSeg(aq, float2(-0.3, 0.0), float2(0.25, 0.02)) - 0.07;
        d = min(d, length(aq - float2(0.36, 0.04)) - 0.065);
        d = min(d, sdSeg(aq, float2(-0.3, 0.0), float2(-0.75, 0.05 + kick)) - 0.035);
        d = min(d, sdSeg(aq, float2(-0.3, -0.02), float2(-0.75, -0.04 - kick)) - 0.035);
        d = min(d, sdSeg(aq, float2(0.2, 0.03), float2(0.55, 0.08)) - 0.03);
        float tri = sdSeg(aq, float2(-0.15, 0.08), float2(0.95, 0.08));
        tri = min(tri, sdSeg(aq, float2(0.95, -0.02), float2(0.95, 0.18)));
        tri = min(tri, sdSeg(aq, float2(0.95, -0.02), float2(1.12, -0.02)));
        tri = min(tri, sdSeg(aq, float2(0.95, 0.08), float2(1.15, 0.08)));
        tri = min(tri, sdSeg(aq, float2(0.95, 0.18), float2(1.12, 0.18)));
        tri -= 0.012;
        float pd = d * as * res.y; float pt = tri * as * res.y;
        col = mix(col, mix(float3(0.02, 0.05, 0.06), water(uv.y), 0.3), clamp(0.5 - pd, 0.0, 1.0));
        col += float3(0.3, 0.8, 0.6) * exp(-abs(pd) * 0.5) * 0.15;
        col = mix(col, float3(0.9, 0.7, 0.3), clamp(0.5 - pt, 0.0, 1.0) * 0.9);
        col += float3(1.0, 0.8, 0.4) * exp(-max(pt, 0.0) * 0.3) * 0.25;
    }
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float2 jp = float2(-0.14 + fi * 0.14 + 0.02 * sin(t * 0.3 + fi), 1.15 - fract(t * 0.012 + fi * 0.33) * 1.4);
        float js = 0.035 + fi * 0.008;
        float2 jq = (sp - jp) / js; jq.y = -jq.y;
        if (abs(jq.x) > 1.2 || jq.y > 0.8 || jq.y < -3.2) continue;
        float pulse = 1.0 + 0.1 * sin(t * 2.2 + fi * 2.0);
        float bell = max(length(jq * float2(1.0 / pulse, 1.25)) - 0.55, -jq.y - 0.05);
        float3 jc = fi < 0.5 ? float3(1.0, 0.45, 0.8) : (fi < 1.5 ? float3(0.5, 0.8, 1.0) : float3(0.8, 0.55, 1.0));
        float px = js * res.y;
        col += jc * (exp(-abs(bell) * px * 0.5) * 0.9 + (bell < 0.0 ? 0.18 : 0.0));
        for (int k = 0; k < 4; k++) {
            float x0 = -0.33 + float(k) * 0.22;
            float yy = clamp(jq.y, -3.0, -0.05);
            float td = length(float2(jq.x - x0 - 0.08 * sin(yy * 5.0 + t * 2.5 + float(k)), jq.y - yy));
            col += jc * exp(-td * px * 0.8) * 0.6 * smoothstep(-3.0, -0.2, jq.y);
        }
        col += jc * exp(-length(jq) * 1.5) * 0.08;
    }
    float2 sc = float2(0.04 * sin(t * 0.07), 0.52 + 0.04 * sin(t * 0.05));
    for (int i = 0; i < 30; i++) {
        float fi = float(i);
        float a = t * 0.45 + fi * 0.73;
        float r = 0.06 + 0.07 * h1(float2(fi, 1.0));
        float2 pos = sc + float2(cos(a) * r * 1.7, sin(a) * r * 0.55 + (h1(float2(fi, 2.0)) - 0.5) * 0.05);
        float s = 0.016 * (0.85 + 0.3 * sin(a));
        float2 dd = sp - pos;
        if (dot(dd, dd) > s * s) continue;
        float2 q = rot(dd, normalize(float2(-sin(a) * 1.7, cos(a) * 0.55))) / s;
        q.y += 0.04 * sin(q.x * 6.0 + t * 12.0 + fi);
        float d = fishd(q) * s * res.y;
        float lit = 0.55 + 0.45 * smoothstep(-0.15, 0.15, q.y);
        float3 f3 = mix(float3(0.35, 0.5, 0.6), float3(0.85, 0.95, 1.0), lit) * (0.75 + 0.25 * sin(a));
        col = mix(col, f3, clamp(0.5 - d, 0.0, 1.0));
    }
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float x0 = -0.21 + fi * 0.085 + (h1(float2(fi, 3.0)) - 0.5) * 0.03;
        float top = 0.62 + 0.18 * h1(float2(fi, 4.0));
        if (uv.y < top) continue;
        float sway = 0.025 * sin(t * 0.6 + uv.y * 7.0 + fi) * (1.0 - (uv.y - top) / (1.05 - top));
        float w = 0.006 + 0.004 * sin(uv.y * 40.0 + fi);
        float d = (abs(p - x0 - sway) - w) * res.y;
        col = mix(col, float3(0.01, 0.07, 0.05), clamp(0.5 - d, 0.0, 1.0) * 0.95);
        float by = 1.0 - fract(t * 0.08 + fi * 0.21) * 0.45;
        col += float3(0.7, 0.9, 1.0) * exp(-abs(length(sp - float2(x0 + 0.01 * sin(t * 2.0 + fi), by)) - 0.004) * res.y * 0.5) * 0.4;
    }
    float floorY = 0.95 + 0.02 * vn(float2(p * 30.0, 1.0));
    col = mix(col, float3(0.01, 0.03, 0.04), smoothstep(floorY, floorY + 0.004, uv.y));
    float2 sg = float2(fc.x, fc.y - t * 9.0) / 46.0;
    float2 cid = floor(sg); float2 cf = fract(sg) - 0.5;
    float2 off = float2(h1(cid), h1(cid + 3.1)) - 0.5;
    col += float3(0.7, 0.85, 0.9) * step(0.7, h1(cid + 7.0)) * exp(-length(cf - off * 0.7) * 28.0) * 0.35;
    float2 cq = float2(p * 14.0, uv.y * 30.0);
    float cs = abs(sin(cq.x + sin(cq.y * 0.7 + t) + t * 0.6) + sin(cq.x * 0.6 - cq.y * 0.4 - t * 0.8));
    col += float3(0.6, 0.95, 1.0) * pow(max(0.0, 1.0 - cs * 0.7), 6.0) * smoothstep(0.18, 0.0, uv.y) * 0.5;
    float2 vc = uv - 0.5;
    col *= 1.0 - dot(vc, vc) * 0.7;
    return half4(half3(col), 1.0);
}
"""

// Tron Legacy, the chase across the Grid, seen side-on. Sam's cyan light cycle and Quorra's white Light Runner race
// with energy pulsing down their flanks; Clu's orange cycles hunt them, every rider laying a light wall that cuts at a
// hard 90 degrees when they dodge. A Recognizer fires bolts that land just behind them, more Recognizers hold formation
// on the horizon, light jets dogfight in the storm, the Portal's beam stands over Tron City, pulses race the grid.
private const val TRON = """
uniform float2 res;
uniform float t;
""" + NOISE + FBM + """
float sdBox(float2 p, float2 b) { float2 d = abs(p) - b; return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0); }
float sdSeg(float2 p, float2 a, float2 b) { float2 pa = p - a; float2 ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float dep(float k, float s) { return 1.4 + floor(h1(float2(k, s)) * 3.0) * 0.5; }
float zAt(float x, float P, float s) { float k = floor(x / P); return mix(dep(k - 1.0, s), dep(k, s), smoothstep(0.0, 0.06, fract(x / P))); }
float psOf(float fi) { return fi < 0.5 ? 0.06 + 0.012 * sin(t * 0.9) : (fi < 1.5 ? -0.05 + 0.012 * sin(t * 0.7 + 1.0) : -0.13 - fi * 0.02 + 0.02 * sin(t * 0.6 + fi)); }
float4 cycle(float2 q, float spx, float3 c) {
    float2 w1 = q - float2(-0.34, 0.16); float2 w2 = q - float2(0.34, 0.16);
    float shell = sdBox(q - float2(0.0, 0.2), float2(0.36, 0.02)) - 0.12;
    float rider = min(length(q - float2(0.17, 0.37)) - 0.055, sdSeg(q, float2(-0.2, 0.32), float2(0.12, 0.36)) - 0.04);
    float body = min(min(shell, rider), min(length(w1), length(w2)) - 0.16);
    float rims = min(abs(length(w1) - 0.14), abs(length(w2) - 0.14));
    float hubs = max(min(length(w1), length(w2)) - 0.035, 0.0);
    float flank = max(max(abs(q.y - 0.24) - 0.004, abs(q.x) - 0.4), 0.0);
    float flow = 0.5 + 0.5 * sin(q.x * 28.0 + t * 22.0);
    float3 g = c * (exp(-rims * spx * 0.8) * 1.7 + exp(-hubs * spx * 0.8) * 1.2 + exp(-abs(shell) * spx * 1.2) * 0.8 + exp(-flank * spx * 1.4) * flow * 1.6);
    g += c * exp(-max(body, 0.0) * spx * 0.05) * 0.12;
    return float4(g, body < 0.0 ? 1.0 : 0.0);
}
float4 runner(float2 q, float spx, float3 c) {
    float hull = sdBox(q - float2(0.0, 0.13), float2(0.44, 0.03)) - 0.06;
    float cab = sdBox(q - float2(0.06, 0.23), float2(0.16, 0.015)) - 0.04;
    float d = min(hull, cab);
    float rims = 1000.0;
    for (int i = 0; i < 4; i++) {
        float l = length(q - float2(-0.36 + float(i) * 0.24, 0.075));
        rims = min(rims, abs(l - 0.065)); d = min(d, l - 0.075);
    }
    float stripe = max(max(abs(q.y - 0.15) - 0.003, abs(q.x) - 0.46), 0.0);
    float flow = 0.5 + 0.5 * sin(q.x * 24.0 + t * 20.0);
    float3 g = c * (exp(-rims * spx * 0.9) * 1.5 + exp(-abs(hull) * spx * 1.1) * 0.9 + exp(-abs(cab) * spx) * 0.7 + exp(-stripe * spx * 1.3) * flow * 1.5);
    g += c * exp(-max(d, 0.0) * spx * 0.05) * 0.12;
    return float4(g, d < 0.0 ? 1.0 : 0.0);
}
float recog(float2 q) {
    float d = sdBox(q - float2(0.0, 0.12), float2(1.0, 0.1));
    d = min(d, sdBox(q - float2(0.0, -0.01), float2(0.38, 0.05)));
    d = min(d, sdSeg(q, float2(0.86, 0.2), float2(0.72, 0.98)) - 0.09);
    d = min(d, sdSeg(q, float2(-0.86, 0.2), float2(-0.72, 0.98)) - 0.09);
    return d;
}
float3 drawRecog(float3 col, float2 sp, float2 c, float s, float3 o) {
    float2 q = (sp - c) / s;
    if (abs(q.x) > 1.8 || q.y < -0.8 || q.y > 1.7) return col;
    float d = recog(q) * s * res.y;
    if (d < 0.0) col = float3(0.018, 0.004, 0.002) + o * exp(-abs(d + 5.0) * 0.6) * 0.5;
    return col + o * (exp(-abs(d) * 0.6) * 1.2 + exp(-abs(d) * 0.06) * 0.12);
}
float3 jet(float3 col, float2 sp, float2 c, float s, float3 k) {
    float2 q = (sp - c) / s;
    if (q.x > 1.2 || q.x < -14.0 || abs(q.y) > 1.5) return col;
    float d = min(sdSeg(q, float2(-0.6, 0.0), float2(0.6, 0.0)), min(sdSeg(q, float2(0.1, 0.0), float2(-0.4, 0.45)), sdSeg(q, float2(0.1, 0.0), float2(-0.4, -0.45))));
    float px = s * res.y;
    col += k * exp(-d * px * 0.7) * 1.6;
    if (q.x < -0.6) col += k * exp(-abs(q.y) * px * 1.2) * exp((q.x + 0.6) * 0.3) * 0.9;
    return col;
}
float3 wall(float3 col, float2 sp, float Z, float u0, float u1, float hy, float camH, float3 c) {
    if (Z < 0.3 || sp.x < min(u0, u1) || sp.x > max(u0, u1)) return col;
    float fy = hy + camH / Z;
    float ty = hy + (camH - 0.05) / Z;
    float inside = step(ty, sp.y) * step(sp.y, fy);
    return col + c * (inside * (0.16 + 0.34 * smoothstep(fy, ty, sp.y)) + exp(-abs(sp.y - ty) * res.y * 0.5) * 1.4);
}
half4 main(float2 fc) {
    float2 uv = fc / res;
    float ar = res.x / res.y;
    float hy = 0.56;
    float p = (uv.x - 0.5) * ar;
    float2 sp = float2(p, uv.y);
    float camH = 0.24;
    float v = 1.3;
    float camX = t * v;
    float3 cyan = float3(0.38, 0.88, 1.0);
    float3 white = float3(0.85, 0.97, 1.0);
    float3 orange = float3(1.0, 0.42, 0.06);
    float portal = 0.1;
    float3 col = mix(float3(0.002, 0.006, 0.011), float3(0.016, 0.05, 0.075), smoothstep(0.0, hy, uv.y));
    if (uv.y < hy) {
        float cl = fbm(float2(p * 3.0 + t * 0.02, uv.y * 6.0));
        float near = exp(-abs(p - portal) * 3.0);
        float flash = step(0.94, h1(float2(floor(t * 2.0), 3.0))) * (1.0 - fract(t * 2.0));
        col += float3(0.05, 0.16, 0.22) * smoothstep(0.42, 0.85, cl) * (0.35 + near * 1.3 + flash * 1.6) * smoothstep(hy, 0.04, uv.y);
        float bx = abs(p - portal);
        col += float3(0.75, 0.97, 1.0) * exp(-bx * res.y * 0.35) * smoothstep(0.02, hy, uv.y) * 0.9;
        col += cyan * exp(-bx * 40.0) * smoothstep(0.1, hy, uv.y) * 0.12;
        float gx = (p + camX * 0.015) * 46.0 + 50.0;
        float id = floor(gx);
        float th = 0.008 + 0.07 * pow(h1(float2(id, 1.0)), 2.4);
        float top = hy - th;
        if (uv.y > top) {
            float3 tw = float3(0.003, 0.009, 0.013);
            float f = fract(gx);
            tw += cyan * (exp(-min(f, 1.0 - f) / 46.0 * res.y * 0.9) * 0.55 + exp(-(uv.y - top) * res.y * 0.9) * 0.8);
            float row = floor((hy - uv.y) * 150.0);
            tw += cyan * 0.22 * step(0.55, fract((hy - uv.y) * 150.0)) * step(0.8, h1(float2(id, row))) * step(0.15, f) * step(f, 0.85);
            col = mix(tw, col, 0.18);
        }
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            col = drawRecog(col, sp, float2(-0.19 + fi * 0.07, 0.43 - fi * 0.015 + 0.004 * sin(t * 0.9 + fi * 2.0)), 0.016, orange * 0.75);
        }
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float2 jc = float2(fract(t * 0.05 + 0.3) * 0.95 - 0.47 - fi * 0.065, 0.1 + 0.03 * sin(t * 0.7 + fi * 0.9) + fi * 0.018);
            col = jet(col, sp, jc, 0.022, i == 0 ? cyan : orange);
            if (i > 0) {
                float bx2 = jc.x + 0.02 + fract(t * 2.0 + fi * 0.3) * 0.09;
                col += orange * exp(-sdSeg(sp, float2(bx2, jc.y), float2(bx2 + 0.012, jc.y)) * res.y * 0.5) * 1.4;
            }
        }
    }
    float2 rc = float2(-0.06 + 0.05 * sin(t * 0.35), 0.21 + 0.008 * sin(t * 0.8));
    if (uv.y > rc.y + 0.02 && uv.y < hy) {
        float w = 0.012 + (uv.y - rc.y) * 0.22;
        col += orange * 0.05 * (1.0 - smoothstep(w * 0.6, w, abs(p - rc.x))) * (1.0 - (uv.y - rc.y) / (hy - rc.y));
    }
    col = drawRecog(col, sp, rc, 0.05, orange);
    if (uv.y > hy) {
        float dy = uv.y - hy;
        float z = camH / dy;
        float x = camX + p * z;
        float g = 0.25;
        float gx = x / g; float gz = z / g;
        float pxx = z / res.y / g;
        float pxz = z * z / (camH * res.y) / g;
        float lx = abs(fract(gx + 0.5) - 0.5) / pxx;
        float lz = abs(fract(gz + 0.5) - 0.5) / pxz;
        float fade = exp(-z * 0.12);
        float sharp = 1.0 - smoothstep(0.25, 0.9, max(pxx, pxz));
        col = float3(0.002, 0.006, 0.009);
        col += cyan * (exp(-lx * 0.9) + exp(-lz * 0.9)) * 0.3 * fade * sharp;
        col += cyan * 0.05 * (1.0 - sharp) * fade;
        float zid = floor(gz + 0.5);
        float pp = fract(gx * 0.05 - t * 0.5 + h1(float2(zid, 7.0)));
        col += cyan * 1.6 * exp(-abs(pp - 0.5) * 45.0) * step(0.6, h1(float2(zid, 8.0))) * exp(-lz * 0.6) * fade;
        float xid = floor(gx + 0.5);
        float qq = fract(gz * 0.08 + t * 0.4 + h1(float2(xid, 5.0)));
        col += cyan * 1.2 * exp(-abs(qq - 0.5) * 40.0) * step(0.85, h1(float2(xid, 6.0))) * exp(-lx * 0.6) * fade * sharp;
        col += float3(0.6, 0.95, 1.0) * exp(-abs(p - portal) * 140.0) * exp(-dy * 9.0) * 0.4;
        col += orange * 0.06 * exp(-abs(p - rc.x) * 25.0) * exp(-dy * 18.0);
        col += cyan * 0.07 * exp(-dy * 14.0);
    }
    for (int j = 0; j < 4; j++) {
        int i = 3 - j;
        float fi = float(i);
        float s = fi * 7.3 + 2.0;
        float P = 2.3 + fi * 0.41;
        float ph = fi * 1.37;
        float tt = t + ph;
        float Z = zAt(tt, P, s);
        float k = floor(tt / P);
        float Zk = dep(k, s); float Zp = dep(k - 1.0, s);
        float ps = psOf(fi);
        float L = i == 1 ? 0.42 : 0.3;
        float3 c = i == 0 ? cyan : (i == 1 ? white : orange);
        float tk = k * P - ph;
        float Xk = v * tk + ps * Zk;
        float Xk1 = v * (tk - P) + ps * Zp;
        float tail = camX + ps * Z - L * 0.5;
        col = wall(col, sp, Zk, (Xk - camX) / Zk, (tail - camX) / Zk, hy, camH, c);
        col = wall(col, sp, Zp, (Xk1 - camX) / Zp, (Xk - camX) / Zp, hy, camH, c);
        float rel = Xk - camX;
        if (p * rel > 0.0) {
            float zz = rel / p;
            if (zz >= min(Zp, Zk) && zz <= max(Zp, Zk)) col = wall(col, sp, zz, p - 1.0, p + 1.0, hy, camH, c);
        }
        float gy = hy + camH / Z;
        float Ls = L / Z;
        float2 q = float2(p - ps, gy - uv.y) / Ls;
        if (abs(q.x) < 0.75 && q.y > -0.45 && q.y < 0.75) {
            float spx = Ls * res.y;
            float4 a = i == 1 ? runner(q, spx, c) : cycle(q, spx, c);
            col = mix(col, float3(0.004, 0.008, 0.012), a.w) + a.rgb;
            if (q.y < 0.0) {
                float4 r = i == 1 ? runner(float2(q.x, -q.y), spx, c) : cycle(float2(q.x, -q.y), spx, c);
                col += r.rgb * 0.22 * exp(q.y * 5.0);
            }
        }
    }
    float n = floor(t / 1.3); float f = fract(t / 1.3);
    float ti = mod(n, 2.0);
    float tz = zAt(t + ti * 1.37, 2.3 + ti * 0.41, ti * 7.3 + 2.0);
    float2 T = float2(psOf(ti) - 0.07 - 0.05 * h1(float2(n, 9.0)), hy + camH / tz);
    float2 m = rc + float2(0.0, 0.05);
    if (f < 0.3) {
        float2 b = mix(m, T, f / 0.3);
        float2 dir = normalize(T - m);
        float d = sdSeg(sp, b - dir * 0.05, b) * res.y;
        col += orange * (exp(-d * 0.5) * 2.0 + exp(-d * 0.06) * 0.2);
    } else if (f < 0.75) {
        float r = (f - 0.3) * 0.25;
        float d = abs(length((sp - T) * float2(1.0, 3.0)) - r) * res.y;
        col += orange * exp(-d * 0.4) * (0.75 - f) * 4.0;
        col += float3(1.0, 0.8, 0.5) * exp(-length(sp - T) * res.y * 0.05) * max(0.0, 0.45 - f) * 3.0;
    }
    float2 vc = uv - 0.5;
    col *= 1.0 - dot(vc, vc) * 0.9;
    col += (h1(fc + fract(t) * 71.0) - 0.5) * 0.018;
    return half4(half3(col), 1.0);
}
"""

// Cyberpunk 2077: Night City through a rain-streaked window. Out-of-focus neon bokeh, a skyline of lit
// windows, flickering vertical signs in magenta, cyan and CDPR yellow, rain running down the glass, scanlines.
private const val CYBER = """
uniform float2 res;
uniform float t;
""" + NOISE + """
half4 main(float2 fc) {
    float2 uv = fc / res;
    float ar = res.x / res.y;
    float3 col = mix(float3(0.035, 0.012, 0.05), float3(0.008, 0.004, 0.016), uv.y);
    float bx = floor(uv.x * 18.0);
    float top = 0.30 + 0.32 * h1(float2(bx, 4.0));
    if (uv.y > top) {
        col = float3(0.010, 0.008, 0.018);
        float2 w = fract(float2(uv.x * 90.0, uv.y * 90.0));
        float lit = step(0.72, h1(floor(float2(uv.x * 90.0, uv.y * 90.0))));
        float win = step(0.25, w.x) * step(w.x, 0.75) * step(0.3, w.y) * step(w.y, 0.8);
        float3 wcol = h1(float2(bx, 9.0)) > 0.5 ? float3(1.0, 0.85, 0.3) : float3(0.3, 0.9, 1.0);
        col += wcol * win * lit * 0.22 * smoothstep(1.0, 0.4, uv.y);
    }
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float2 s = float2(0.08 + 0.84 * h1(float2(fi, 11.0)), 0.36 + 0.4 * h1(float2(fi, 13.0)));
        float m = mod(fi, 3.0);
        float3 c = m < 1.0 ? float3(1.0, 0.1, 0.55) : (m < 2.0 ? float3(0.0, 0.94, 1.0) : float3(0.99, 0.93, 0.04));
        float2 d = abs((uv - s) * float2(ar, 1.0)) - float2(0.008, 0.06);
        float sd = max(length(max(d, 0.0)) + min(max(d.x, d.y), 0.0), 0.0);
        float flick = 0.7 + 0.3 * step(0.08, fract(t * 0.7 + fi * 0.37));
        col += c * (exp(-sd * 500.0) * 0.9 + exp(-sd * 25.0) * 0.18) * flick;
    }
    for (int i = 0; i < 22; i++) {
        float fi = float(i);
        float2 p = float2(h1(float2(fi, 3.1)), 0.15 + 0.8 * h1(float2(fi, 7.7)));
        p.x += 0.01 * sin(t * 0.07 + fi);
        float r = 0.035 + 0.07 * h1(float2(fi, 9.0));
        float m = mod(fi, 4.0);
        float3 c = m < 1.0 ? float3(1.0, 0.1, 0.55) : (m < 2.0 ? float3(0.0, 0.9, 1.0) : (m < 3.0 ? float3(0.99, 0.9, 0.1) : float3(1.0, 0.05, 0.25)));
        float d = length((uv - p) * float2(ar, 1.0));
        float disk = 1.0 - smoothstep(r * 0.9, r, d);
        col += c * (disk * 0.07 + smoothstep(r * 0.65, r * 0.95, d) * disk * 0.10);
    }
    float2 rp = float2(uv.x * ar * 46.0, uv.y * 3.0 - t * 1.6);
    float id = floor(rp.x);
    float lane = 1.0 - abs(fract(rp.x) - 0.5) * 2.0;
    float drop = smoothstep(0.85, 1.0, fract(rp.y + h1(float2(id, 2.0)) * 7.0));
    col += float3(0.55, 0.65, 0.9) * smoothstep(0.9, 1.0, lane) * drop * 0.18 * step(0.6, h1(float2(id, 5.0)));
    col *= 0.93 + 0.07 * sin(fc.y * 1.6);
    float2 vc = uv - 0.5;
    col *= 1.0 - dot(vc, vc) * 0.8;
    return half4(half3(col), 1.0);
}
"""

// Synthwave / Outrun: a striped sunset sun with bloom over neon-rimmed mountains, stars, and a magenta grid
// racing into fog with the sun's reflection streaking across it.
private const val OUTRUN = """
uniform float2 res;
uniform float t;
""" + NOISE + """
half4 main(float2 fc) {
    float2 uv = fc / res;
    float ar = res.x / res.y;
    float hy = 0.56;
    float u = (uv.x - 0.5) * ar;
    float3 col = mix(float3(0.03, 0.005, 0.10), float3(0.30, 0.04, 0.32), smoothstep(0.0, hy * 0.85, uv.y));
    col = mix(col, float3(1.0, 0.25, 0.42), smoothstep(hy - 0.18, hy, uv.y));
    float s = h1(floor(fc / 2.0));
    col += float3(step(0.998, s) * step(uv.y, hy * 0.6) * (0.5 + 0.5 * sin(t * 2.0 + s * 40.0)));
    float2 sp = float2(u, uv.y) - float2(0.0, hy - 0.11);
    float R = 0.30;
    float sd = length(sp);
    col += float3(1.0, 0.25, 0.5) * exp(-max(sd - R, 0.0) * 7.0) * 0.5;
    if (sd < R) {
        float3 sun = mix(float3(1.0, 0.86, 0.15), float3(1.0, 0.18, 0.5), smoothstep(-R, R, sp.y));
        float k = (sp.y + R * 0.1) / R;
        float gap = k > 0.0 ? smoothstep(0.0, 1.0, k) * 0.55 : 0.0;
        if (fract(sp.y * 22.0 - t * 0.35) >= gap) col = sun;
    }
    float ridge = hy - (0.03 + 0.07 * abs(sin(uv.x * 9.0)) * vn(float2(uv.x * 7.0, 2.0)) + 0.03 * vn(float2(uv.x * 19.0, 5.0)));
    if (uv.y > ridge && uv.y < hy) col = float3(0.08, 0.01, 0.16) + float3(1.0, 0.2, 0.8) * exp(-(uv.y - ridge) * 260.0) * 0.8;
    if (uv.y > hy) {
        float v = uv.y - hy;
        float z = 0.35 / v;
        float x = u * z;
        float zs = z + t * 6.0;
        float px = z * 0.004 + 0.002;
        float dx = abs(fract(x * 0.5 + 0.5) - 0.5) * 2.0;
        float dz = abs(fract(zs * 0.5 + 0.5) - 0.5) * 2.0;
        col = float3(0.04, 0.0, 0.08) + float3(1.0, 0.2, 0.85) * (exp(-dx / px) + exp(-dz / px)) * 0.55 * exp(-z * 0.04);
        col += float3(1.0, 0.35, 0.55) * exp(-abs(u) * 10.0) * exp(-v * 5.0) * 0.35;
    }
    col += float3(1.0, 0.3, 0.8) * exp(-abs(uv.y - hy) * 220.0) * 0.8;
    float2 vc = uv - 0.5;
    col *= 1.0 - dot(vc, vc) * 0.6;
    return half4(half3(col), 1.0);
}
"""

// The Matrix: green code rain, and every few seconds the code forms one of the film's great moments: bullet-time,
// leaning back under the bullets with ripples trailing them; "he's the One", bullets stopped in the air before a raised
// hand; the corridor and the Agents when he finally sees the code; the red pill and the blue pill.
private const val MATRIX = """
uniform float2 res;
uniform float t;
""" + NOISE + """
float sdBox(float2 p, float2 b) { float2 d = abs(p) - b; return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0); }
float sdSeg(float2 p, float2 a, float2 b) { float2 pa = p - a; float2 ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float cap(float2 q, float2 a, float2 b, float r) { return sdSeg(q, a, b) - r; }
float dodge(float2 q) {
    float2 hip = float2(-0.05, 0.78); float2 sh = float2(-0.72, 1.12);
    float d = cap(q, hip, sh, 0.12);
    d = min(d, length(q - float2(-0.92, 1.2)) - 0.12);
    d = min(d, cap(q, float2(-0.25, 0.0), float2(0.15, 0.42), 0.06));
    d = min(d, cap(q, float2(0.15, 0.42), hip, 0.07));
    d = min(d, cap(q, float2(0.3, 0.0), float2(0.38, 0.45), 0.06));
    d = min(d, cap(q, float2(0.38, 0.45), hip, 0.07));
    d = min(d, cap(q, sh, float2(-0.45, 1.5), 0.05));
    d = min(d, cap(q, float2(-0.45, 1.5), float2(-0.1, 1.62), 0.045));
    d = min(d, cap(q, sh, float2(-0.95, 0.85), 0.05));
    d = min(d, cap(q, float2(-0.95, 0.85), float2(-1.2, 0.95), 0.045));
    float wv = 0.05 * sin(t * 3.0);
    d = min(d, cap(q, float2(-0.2, 0.85), float2(-0.75, 0.3 + wv), 0.07));
    d = min(d, cap(q, float2(-0.35, 0.9), float2(-1.05, 0.45 - wv), 0.06));
    return d;
}
float stand(float2 q, float coat) {
    float d = cap(q, float2(0.0, 0.95), float2(0.0, 1.55), 0.13);
    d = min(d, length(q - float2(0.0, 1.76)) - 0.12);
    d = min(d, cap(q, float2(-0.12, 0.0), float2(-0.08, 0.95), 0.07));
    d = min(d, cap(q, float2(0.14, 0.0), float2(0.08, 0.95), 0.07));
    d = min(d, cap(q, float2(-0.05, 1.5), float2(-0.2, 0.95), 0.05));
    d = min(d, cap(q, float2(-0.1, 1.2), float2(-0.38, 0.15), 0.08 * coat));
    d = min(d, cap(q, float2(0.1, 1.2), float2(0.3, 0.15), 0.08 * coat));
    return d;
}
float hand(float2 q) {
    float d = sdBox(q, float2(0.18, 0.2)) - 0.12;
    for (int i = 0; i < 4; i++) d = min(d, cap(q, float2(-0.22 + float(i) * 0.15, 0.25), float2(-0.24 + float(i) * 0.15, 0.7 - abs(float(i) - 1.5) * 0.08), 0.065));
    d = min(d, cap(q, float2(-0.3, -0.05), float2(-0.55, 0.25), 0.07));
    return d;
}
half4 main(float2 fc) {
    float2 uv = fc / res;
    float ar = res.x / res.y;
    float p = (uv.x - 0.5) * ar;
    float2 sp = float2(p, uv.y);
    float3 green = float3(0.0, 1.0, 0.25);
    float cw = res.x / floor(res.x / 22.0);
    float ch = cw * 1.3;
    float cx = floor(fc.x / cw); float ry = floor(fc.y / ch);
    float rows = res.y / ch;
    float speed = 5.0 + 9.0 * h1(float2(cx, 1.0));
    float len = 8.0 + 22.0 * h1(float2(cx, 2.0));
    float k = mod(t * speed + h1(float2(cx, 3.0)) * 200.0, rows + len + 10.0) - ry;
    float b = (k >= 0.0 && k < len) ? pow(1.0 - k / len, 1.4) : 0.0;
    float2 f = fract(fc / float2(cw, ch));
    float2 gi = floor(f * float2(5.0, 7.0));
    float gid = floor(h1(float2(cx, ry)) * 50.0 + t * (0.5 + 2.0 * h1(float2(ry, cx))));
    float hb = mod(gi.y, 3.0) < 0.5 ? step(0.45, h1(float2(gid, gi.y))) : 0.0;
    float vb = mod(gi.x, 2.0) < 0.5 ? step(0.6, h1(float2(gid + 5.0, gi.x + floor(gi.y / 3.5) * 9.0))) : 0.0;
    float bit = max(hb, vb) * step(0.12, f.x) * step(f.x, 0.88) * step(0.08, f.y) * step(f.y, 0.92);
    float3 rc = k < 1.0 ? float3(0.85, 1.0, 0.88) : (k < 3.0 ? float3(0.55, 1.0, 0.62) : green);
    float slot = floor(t / 9.0);
    float id = mod(slot, 4.0);
    float lt = t - slot * 9.0;
    float fa = smoothstep(0.0, 1.2, lt) * smoothstep(9.0, 7.8, lt);
    float sd = 1000.0;
    float3 extra = float3(0.0);
    float3 pill = float3(0.0); float pa = 0.0;
    if (id < 0.5) {
        float s = 0.15;
        float2 q = float2(sp.x - 0.07, 0.68 - sp.y) / s;
        sd = dodge(q) * s * res.y;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float bx = 2.6 - fract(lt * 0.11 + fi * 0.27) * 5.2;
            float by = 1.28 + fi * 0.16;
            float bd = max((sdSeg(q, float2(bx, by), float2(bx + 0.1, by)) - 0.025) * s * res.y, 0.0);
            extra += float3(0.8, 1.0, 0.85) * exp(-bd * 0.5) * 1.2;
            for (int m = 1; m < 5; m++) {
                float fm = float(m);
                float rd = abs(length(float2((q.x - bx - 0.12 - fm * 0.16) * 3.0, q.y - by)) - (0.04 + fm * 0.03)) * s * res.y;
                extra += green * exp(-rd * 0.6) * (0.7 - fm * 0.13);
            }
        }
    } else if (id < 1.5) {
        float s = 0.15;
        float2 q = float2(sp.x + 0.06, 0.68 - sp.y) / s;
        float d = stand(q, 1.0);
        d = min(d, cap(q, float2(0.05, 1.5), float2(0.42, 1.45), 0.05));
        d = min(d, cap(q, float2(0.42, 1.45), float2(0.66, 1.55), 0.045));
        d = min(d, length(q - float2(0.7, 1.58)) - 0.07);
        sd = d * s * res.y;
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            float2 bp = float2(1.0 + fi * 0.11, 1.25 + fi * 0.1 + 0.03 * sin(t * 2.0 + fi));
            bp.y -= pow(max(lt - 5.5, 0.0), 2.0) * 0.9;
            float bd = max((sdSeg(q, bp, bp + float2(0.1, 0.0)) - 0.025) * s * res.y, 0.0);
            extra += float3(0.8, 1.0, 0.85) * exp(-bd * 0.5) * 1.2;
            float rp = fract(lt * 0.7 + fi * 0.3);
            float rd = abs(length((q - bp) * float2(3.0, 1.0)) - 0.08 - 0.12 * rp) * s * res.y;
            extra += green * exp(-rd * 0.6) * 0.5 * (1.0 - rp);
        }
    } else if (id < 2.5) {
        float2 vp = float2(0.0, 0.47);
        float ld = 1000.0;
        for (int j = 0; j < 5; j++) {
            float sc = pow(0.62, float(j) + fract(lt * 0.15));
            ld = min(ld, abs(sdBox(sp - vp, float2(0.3, 0.42) * sc)));
        }
        ld = min(ld, sdSeg(sp, vp, float2(-0.5 * ar, 0.0)));
        ld = min(ld, sdSeg(sp, vp, float2(0.5 * ar, 0.0)));
        ld = min(ld, sdSeg(sp, vp, float2(-0.5 * ar, 1.0)));
        ld = min(ld, sdSeg(sp, vp, float2(0.5 * ar, 1.0)));
        extra += green * exp(-ld * res.y * 0.25) * 0.7;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float s = 0.045;
            float2 q = float2(sp.x - (-0.07 + fi * 0.07), 0.57 - abs(fi - 1.0) * 0.008 - sp.y) / s;
            sd = min(sd, stand(q, 0.0) * s * res.y);
        }
    } else {
        float s = 0.11;
        float2 q1 = float2(sp.x + 0.1, 0.62 - sp.y) / s;
        float2 q2 = float2(-(sp.x - 0.1), 0.62 - sp.y) / s;
        sd = min(hand(q1), hand(q2)) * s * res.y;
        float d1 = (sdSeg(q1, float2(-0.12, 0.02), float2(0.12, 0.02)) - 0.08) * s * res.y;
        float d2 = (sdSeg(q2, float2(-0.12, 0.02), float2(0.12, 0.02)) - 0.08) * s * res.y;
        float hl = 0.75 + 0.25 * smoothstep(0.0, -1.0, (0.62 - sp.y) / s - 0.06);
        if (d1 < d2) { pill = float3(1.0, 0.12, 0.08) * hl; pa = clamp(0.5 - d1, 0.0, 1.0); extra += float3(1.0, 0.1, 0.05) * exp(-max(d1, 0.0) * 0.08) * 0.35; }
        else { pill = float3(0.15, 0.4, 1.0) * hl; pa = clamp(0.5 - d2, 0.0, 1.0); extra += float3(0.1, 0.3, 1.0) * exp(-max(d2, 0.0) * 0.08) * 0.35; }
    }
    float inside = clamp(0.5 - sd, 0.0, 1.0);
    float3 col = float3(0.0, 0.012, 0.004);
    col += rc * bit * b * (1.0 - 0.5 * fa);
    col += float3(0.7, 1.0, 0.75) * bit * inside * fa * (0.55 + 0.45 * b);
    col += green * exp(-abs(sd) * 0.18) * 0.35 * fa;
    col += extra * fa;
    col = mix(col, pill, pa * fa);
    col *= 0.92 + 0.08 * step(0.5, fract(fc.y / 3.0));
    float2 vc = uv - 0.5;
    col *= 1.0 - dot(vc, vc) * 0.9;
    return half4(half3(col), 1.0);
}
"""

private object Gpu {
    val ok = Build.VERSION.SDK_INT >= 33
    // A shader the phone's GPU driver rejects falls back to the theme's gradient instead of crashing.
    private fun mk(src: String) = if (ok) runCatching { RuntimeShader(src) }
        .onFailure { android.util.Log.w("Looks", "shader rejected", it) }.getOrNull() else null
    val aurora by lazy { mk(AURORA) }
    val glass by lazy { mk(GLASS_BG) }
    val ocean by lazy { mk(OCEAN) }
    val matrix by lazy { mk(MATRIX) }
    val tron by lazy { mk(TRON) }
    val cyber by lazy { mk(CYBER) }
    val outrun by lazy { mk(OUTRUN) }
}

/** Fills the scene with a GPU shader; false when the phone can't (Android 12 and older). */
private fun DrawScope.shader(sh: RuntimeShader?, W: Float, H: Float, t: Float): Boolean {
    if (sh == null || Build.VERSION.SDK_INT < 33) return false
    sh.setFloatUniform("res", W, H)
    sh.setFloatUniform("t", t)
    drawRect(ShaderBrush(sh), Offset.Zero, Size(W, H))
    return true
}

/* ---------------- backdrop ---------------- */

/** The theme's world, full screen behind everything: still art cached once, moving parts redrawn only while Live theme runs. */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier) {
    val host = LocalScene.current ?: return
    val th = LocalTheme.current
    val glass = th.p.look.fill == Fill.GLASS
    val still = rememberGraphicsLayer()
    val sharp = rememberGraphicsLayer()
    val frosted = rememberGraphicsLayer()
    SideEffect { host.frosted = if (glass) frosted else null }
    val moves = th.p.look.scene != Scene.MATTE && th.p.look.scene != Scene.WHITE
    // Own layer: a live tick re-records only the backdrop, never the screen above it.
    Box(modifier.fillMaxSize().graphicsLayer()
        .onGloballyPositioned { host.origin = it.positionInRoot(); host.size = Size(it.size.width.toFloat(), it.size.height.toFloat()) }
        .drawWithCache {
            val d = Density(density, fontScale)
            val px = IntSize(size.width.toInt(), size.height.toInt())
            val full = size
            still.record(d, layoutDirection, px) { drawSceneStill(th.p, full, th.dark) }
            if (glass && Build.VERSION.SDK_INT >= 31) {
                val r = th.p.look.frost.toPx()
                frosted.renderEffect = android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP).asComposeRenderEffect()
            }
            onDrawBehind {
                val t = if (moves) host.time.value else STILL        // read = redraw per tick, only while the clock runs
                sharp.record(d, layoutDirection, px) {
                    drawLayer(still)
                    if (moves) drawSceneMoving(th.p, full, t, th.dark)
                }
                drawLayer(sharp)
                if (glass) frosted.record(d, layoutDirection, px) { drawLayer(sharp) }
            }
        })
}

/** Draws a whole scene, frozen at [STILL], as if this canvas were [full]; backdrop and theme previews. */
fun DrawScope.drawScene(p: Palette, full: Size, dark: Boolean) {
    drawSceneStill(p, full, dark)
    drawSceneMoving(p, full, STILL, dark)
}

private fun DrawScope.drawSceneStill(p: Palette, full: Size, dark: Boolean) {
    val W = full.width; val H = full.height
    if (W <= 0f || H <= 0f) return
    when (p.look.scene) {
        Scene.WA -> waStill(W, H, dark)
        Scene.MATTE -> matte(W, H)
        Scene.WHITE -> white(W, H)
        Scene.EMBER -> emberStill(W, H, dark)
        Scene.TRON -> if (Gpu.tron == null) full(W, H, v(0f to Color(0xFF010407), 1f to Color(0xFF04131C), H = H))
        Scene.CYBER -> if (Gpu.cyber == null) full(W, H, v(0f to Color(0xFF14081E), 1f to Color(0xFF05030A), H = H))
        Scene.WASHI -> washiStill(W, H)
        Scene.NIGHT -> Unit
        Scene.OUTRUN -> if (Gpu.outrun == null) full(W, H, v(0f to Color(0xFF0D0221), 0.55f to Color(0xFFFF3864), 1f to Color(0xFF120326), H = H))
        Scene.AURORA -> if (Gpu.aurora == null) full(W, H, v(0f to Color(0xFF01040C), 1f to Color(0xFF0B1F2E), H = H))
        Scene.GLASS -> if (Gpu.glass == null) full(W, H, Brush.linearGradient(listOf(Color(0xFFBFD3FF), Color(0xFFE6C9FF), Color(0xFFFFD6C2)), Offset.Zero, Offset(W, H)))
        Scene.OCEAN -> if (Gpu.ocean == null) full(W, H, v(0f to Color(0xFF0C5A7A), 1f to Color(0xFF021421), H = H))
        Scene.SAKURA -> sakuraStill(W, H)
        Scene.RAIN -> if (Gpu.matrix == null) full(W, H, Color(0xFF000400))
        Scene.VAPOR -> vaporStill(W, H)
        Scene.SNOW -> snowStill(W, H)
        Scene.DUNES -> dunesStill(W, H)
    }
}

private fun DrawScope.drawSceneMoving(p: Palette, full: Size, t: Float, dark: Boolean) {
    val W = full.width; val H = full.height
    if (W <= 0f || H <= 0f) return
    when (p.look.scene) {
        Scene.EMBER -> emberMoving(W, H, t, dark)
        Scene.TRON -> shader(Gpu.tron, W, H, t)
        Scene.CYBER -> shader(Gpu.cyber, W, H, t)
        Scene.WASHI -> washiMoving(W, H, t)
        Scene.NIGHT -> night(W, H, t)
        Scene.OUTRUN -> shader(Gpu.outrun, W, H, t)
        Scene.AURORA -> shader(Gpu.aurora, W, H, t)
        Scene.GLASS -> shader(Gpu.glass, W, H, t)
        Scene.OCEAN -> shader(Gpu.ocean, W, H, t)
        Scene.WA -> waMoving(W, H, t, dark)
        Scene.SAKURA -> sakuraMoving(W, H, t)
        Scene.RAIN -> shader(Gpu.matrix, W, H, t)
        Scene.VAPOR -> vaporMoving(W, H, t)
        Scene.SNOW -> snowMoving(W, H, t)
        Scene.DUNES -> dunesMoving(W, H, t)
        else -> Unit
    }
}

private fun DrawScope.full(W: Float, H: Float, brush: Brush, alpha: Float = 1f, blend: BlendMode = BlendMode.SrcOver) =
    drawRect(brush, Offset.Zero, Size(W, H), alpha, blendMode = blend)
private fun DrawScope.full(W: Float, H: Float, color: Color) = drawRect(color, Offset.Zero, Size(W, H))
private fun v(vararg stops: Pair<Float, Color>, H: Float) = Brush.verticalGradient(*stops, startY = 0f, endY = H)

/** The Wa season's colour: sakura in spring, young bamboo in summer, maple in autumn, snow-blue in winter. */
private fun waTint(season: Int) = when (season) { 0 -> Color(0xFFF4A7B9); 1 -> Color(0xFF4F9E75); 2 -> Color(0xFFC8553D); else -> Color(0xFF8FA6B8) }

/** Wa: unbleached paper (kinari) by day, sumi ink by night, washi fibres, an indigo wash from above and the season's colour below. */
private fun DrawScope.waStill(W: Float, H: Float, dark: Boolean) {
    full(W, H, if (dark) Color(0xFF161513) else Color(0xFFFBFAF5))
    full(W, H, Textures.brush(Textures.paper), if (dark) 0.05f else 0.55f)
    full(W, H, Brush.radialGradient(listOf(Color(0xFF165E83).copy(alpha = if (dark) 0.22f else 0.10f), Color.Transparent), Offset(W * 0.85f, -H * 0.05f), W * 0.95f))
    full(W, H, Brush.radialGradient(listOf(waTint(seasonOf(java.time.LocalDate.now())).copy(alpha = if (dark) 0.12f else 0.10f), Color.Transparent),
        Offset(W * 0.1f, H * 1.02f), W * 0.9f))
}

/** Wa with Live theme on: the season drifts past — petals in spring, fireflies in summer, maple leaves in autumn, snow in winter. */
private fun DrawScope.waMoving(W: Float, H: Float, t: Float, dark: Boolean) {
    val season = seasonOf(java.time.LocalDate.now())
    when (season) {
        0 -> sakuraMoving(W, H, t, waTint(0), count = 12)
        2 -> sakuraMoving(W, H, t, waTint(2), count = 10)
        3 -> snowMoving(W, H, t, if (dark) Color(0xFFECEFF4) else Color(0xFF9FB3C2), count = 30)
        else -> for (i in 0 until 14) {                     // fireflies: slow wander, soft pulse
            val x = (hash(i) + 0.08f * sin(t * 0.3f + i)) * W
            val y = H * (0.45f + 0.5f * hash(i + 5)) + sin(t * 0.4f + i * 1.7f) * 18.dp.toPx()
            val glow = 0.35f + 0.65f * abs(sin(t * 0.9f + i))
            drawCircle(Color(0xFFD9F27A).copy(alpha = 0.18f * glow), 7.dp.toPx(), Offset(x, y))
            drawCircle(Color(0xFFEFFFB0).copy(alpha = 0.9f * glow), 1.6.dp.toPx(), Offset(x, y))
        }
    }
}

/** Matte black: anodised, light-absorbing, a breath of grain and a vignette. No glow at all. */
private fun DrawScope.matte(W: Float, H: Float) {
    full(W, H, Color.Black)
    full(W, H, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.045f), Color.Transparent), 0f, H * 0.35f))
    full(W, H, Textures.brush(Textures.grain), 0.6f)
    full(W, H, Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), Offset(W / 2, H * 0.4f), H * 0.85f))
}

/** Apple white: paper-white with a soft overhead light; depth comes from the floating cards' shadows. */
private fun DrawScope.white(W: Float, H: Float) {
    full(W, H, v(0f to Color.White, 1f to Color(0xFFF2F2F7), H = H))
    full(W, H, Brush.radialGradient(listOf(Color.White, Color.Transparent), Offset(W * 0.5f, -H * 0.05f), W))
}

/** Original: the oni's flame. Ember glows (still), and sparks rising and flickering out. */
private fun DrawScope.emberStill(W: Float, H: Float, dark: Boolean) {
    full(W, H, if (dark) Color.Black else Color(0xFFF2F2F7))
    full(W, H, Brush.radialGradient(listOf(Color(0xFFFF3B30).copy(alpha = if (dark) 0.42f else 0.2f), Color.Transparent), Offset(W * 0.9f, 0f), W * 1.05f))
    full(W, H, Brush.radialGradient(listOf(Color(0xFFFF8A00).copy(alpha = if (dark) 0.22f else 0.12f), Color.Transparent), Offset(0f, H * 0.25f), W * 0.8f))
}
private fun DrawScope.emberMoving(W: Float, H: Float, t: Float, dark: Boolean) {
    for (i in 0 until 30) {
        val sp = (16f + hash(i) * 38f) * density
        val y = H + 20f - ((t * sp + hash(i + 50) * H * 1.3f) % (H * 1.3f))
        val x = hash(i + 100) * W + sin(t * (0.6f + hash(i + 7)) + i) * 14.dp.toPx()
        val a = (0.35f + 0.65f * hash(i + 3)) * (0.6f + 0.4f * sin(t * 7f + i)) * (y / H).coerceIn(0f, 1f) * (if (dark) 1f else 0.6f)
        val r = (1.2f + hash(i + 9) * 2.2f).dp.toPx()
        val c = lerp(Color(0xFFFFC46B), Color(0xFFFF3B30), hash(i + 11))
        drawCircle(c.copy(alpha = a * 0.25f), r * 3f, Offset(x, y))
        drawCircle(Color(0xFFFFF1D0).copy(alpha = a), r * 0.55f, Offset(x, y))
    }
}

/** Perspective floor grid; [phase] 0..1 scrolls it toward the viewer. */
private fun DrawScope.floorGrid(W: Float, H: Float, hy: Float, phase: Float, vColor: Color, hColor: Color, lines: Int = 14) {
    val vp = Offset(W / 2, hy - H * 0.02f)
    for (k in -lines..lines) {
        val xb = W / 2 + k * W * 0.13f
        val s = (hy - vp.y) / (H - vp.y)
        drawLine(vColor.copy(alpha = vColor.alpha * (1f - abs(k) / (lines + 1f))), Offset(vp.x + (xb - vp.x) * s, hy), Offset(xb, H), 1.dp.toPx())
    }
    for (n in 0..20) {
        val d = n + 1 - phase
        if (d < 1f) continue
        val y = hy + (H - hy) / d
        drawLine(hColor.copy(alpha = hColor.alpha * (1f / d).pow(0.45f)), Offset(0f, y), Offset(W, y), 1.dp.toPx())
    }
}

/** White Ninja: washi paper, a vermilion hinomaru, sumi-e ink mountains (still); mist and cranes drifting. */
private fun DrawScope.washiStill(W: Float, H: Float) {
    val ink = Color(0xFF1A1A1A); val shu = Color(0xFFC8102E)
    full(W, H, Color(0xFFF6F3EC))
    full(W, H, Textures.brush(Textures.paper))
    val sc = Offset(W * 0.76f, H * 0.17f); val sr = W * 0.2f
    drawCircle(Brush.radialGradient(0f to shu, 0.93f to shu.copy(alpha = 0.95f), 1f to Color.Transparent, center = sc, radius = sr), sr, sc)
    for (L in 0..2) {
        val base = H * (0.70f + L * 0.08f); val amp = H * (0.09f - L * 0.015f)
        val ridge = Path().apply {
            moveTo(0f, H)
            var x = 0f
            while (x <= W + 8f) {
                val f = x / W
                val n = (0.55f * sin(f * 5.1f + L * 2.1f) + 0.3f * sin(f * 11.3f + L) + 0.15f * sin(f * 23f + L * 4f) + 1f) / 2f
                lineTo(x, base - amp * n)
                x += 6.dp.toPx()
            }
            lineTo(W, H); close()
        }
        drawPath(ridge, Brush.verticalGradient(listOf(ink.copy(alpha = 0.62f - L * 0.14f), ink.copy(alpha = 0.04f)), base - amp, base + H * 0.12f))
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFF6F3EC)), H * 0.86f, H), Offset(0f, H * 0.86f), Size(W, H * 0.14f))
}
private fun DrawScope.washiMoving(W: Float, H: Float, t: Float) {
    val ink = Color(0xFF1A1A1A)
    val mx = sin(t * 0.08f) * W * 0.12f
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent), mx, mx + W),
        Offset(0f, H * 0.74f), Size(W, H * 0.06f))
    for (b in 0..2) {
        val bx = frac(t * 0.012f + b * 0.31f) * W * 1.3f - W * 0.15f; val by = H * (0.32f + b * 0.05f) + sin(t * 0.5f + b) * 6f
        val s = (7 + b * 2).dp.toPx(); val flap = sin(t * 2.2f + b) * s * 0.3f
        val wing = Path().apply { moveTo(bx - s, by - flap); quadraticTo(bx - s * 0.4f, by - s * 0.3f, bx, by); quadraticTo(bx + s * 0.4f, by - s * 0.3f, bx + s, by - flap) }
        drawPath(wing, ink.copy(alpha = 0.55f), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Black Ninja: a blood moon behind passing cloud, drifting smoke, swaying bamboo in silhouette. */
private fun DrawScope.night(W: Float, H: Float, t: Float) {
    full(W, H, v(0f to Color(0xFF050505), 1f to Color(0xFF0D0507), H = H))
    val mc = Offset(W * 0.72f, H * 0.15f); val mr = W * 0.17f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB3001B).copy(alpha = 0.35f), Color.Transparent), mc, mr * 2.4f), mr * 2.4f, mc)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFA3111F), Color(0xFF5A040C)), mc, mr), mr, mc)
    for (k in 0..2) drawCircle(Color(0xFF3E0208).copy(alpha = 0.45f), mr * (0.16f + k * 0.07f), Offset(mc.x + mr * (hash(k) - 0.5f), mc.y + mr * (hash(k + 4) - 0.5f)))
    val cx = frac(t * 0.018f) * W * 1.8f - W * 0.5f            // cloud crossing the moon
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF050505).copy(alpha = 0.9f), Color.Transparent), cx, cx + W * 0.7f),
        Offset(0f, mc.y + mr * 0.05f), Size(W, mr * 0.32f))
    for (i in 0 until 7) {                                      // smoke
        val sx = frac(hash(i) + t * 0.006f * (0.5f + hash(i + 1))) * W * 1.4f - W * 0.2f
        val sy = H * (0.45f + hash(i + 2) * 0.5f) + sin(t * 0.2f + i) * 20.dp.toPx()
        val r = W * (0.25f + hash(i + 3) * 0.3f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3A3A).copy(alpha = 0.16f), Color.Transparent), Offset(sx, sy), r), r, Offset(sx, sy))
    }
    for (b in 0..2) {                                           // bamboo
        val x = W * (0.04f + b * 0.08f); val sway = sin(t * 0.45f + b * 1.3f) * 5.dp.toPx(); val sw = (9 - b * 2).dp.toPx()
        val stalk = Path().apply { moveTo(x - sw / 2, H); quadraticTo(x - sw / 2, H * 0.5f, x - sw / 2 + sway, -10f); lineTo(x + sw / 2 + sway, -10f); quadraticTo(x + sw / 2, H * 0.5f, x + sw / 2, H); close() }
        drawPath(stalk, Color(0xFF090909))
        var ny = H - 40.dp.toPx(); while (ny > 0) { val k = 1f - ny / H; drawLine(Color(0xFF1C1C1C), Offset(x - sw / 2 + sway * k, ny), Offset(x + sw / 2 + sway * k, ny), 1.5.dp.toPx()); ny -= 74.dp.toPx() }
        for (l in 0..2) {
            val at = Offset(x + sway, H * (0.12f + b * 0.07f + l * 0.05f))
            rotate(-30f + l * 25f + sin(t * 0.7f + l + b) * 4f, at) { drawOval(Color(0xFF090909), at, Size(34.dp.toPx(), 6.dp.toPx())) }
        }
    }
}

private val petal = Path().apply {
    moveTo(0f, -1f); cubicTo(0.75f, -0.85f, 0.85f, 0.45f, 0f, 1f); cubicTo(-0.85f, 0.45f, -0.75f, -0.85f, 0f, -1f); close()
}

/** Sakura: a blossoming branch (still), and hanafubuki, petals tumbling on the breeze. */
private fun DrawScope.sakuraStill(W: Float, H: Float) {
    full(W, H, v(0f to Color(0xFFFFF7F9), 1f to Color(0xFFFCE4EC), H = H))
    full(W, H, Brush.radialGradient(listOf(Color.White, Color.Transparent), Offset(W * 0.1f, H * 0.05f), W * 0.8f))
    val bark = Color(0xFF4A2C2A)
    val branch = Path().apply { moveTo(-10f, H * 0.14f); cubicTo(W * 0.15f, H * 0.08f, W * 0.3f, H * 0.12f, W * 0.52f, H * 0.04f) }
    drawPath(branch, bark.copy(alpha = 0.85f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    val twig = Path().apply { moveTo(W * 0.22f, H * 0.1f); quadraticTo(W * 0.3f, H * 0.17f, W * 0.42f, H * 0.18f) }
    drawPath(twig, bark.copy(alpha = 0.8f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    listOf(0.12f to 0.1f, 0.2f to 0.085f, 0.33f to 0.1f, 0.45f to 0.055f, 0.3f to 0.155f, 0.4f to 0.175f, 0.52f to 0.04f).forEachIndexed { i, (fx, fy) ->
        val c = Offset(W * fx, H * fy); val s = (6 + hash(i) * 4).dp.toPx()
        for (k in 0 until 5) rotate(k * 72f + i * 17f, c) {
            withTransform({ translate(c.x, c.y - s * 0.8f); scale(s * 0.55f, s * 0.85f, Offset.Zero) }) { drawPath(petal, Color(0xFFFFC9D6)) }
        }
        drawCircle(Color(0xFFE0457B), s * 0.22f, c)
    }
}
private fun DrawScope.sakuraMoving(W: Float, H: Float, t: Float, color: Color = Color(0xFFFFB7C9), count: Int = 26) {
    for (i in 0 until count) {
        val x = frac(hash(i) + t * 0.015f * (0.5f + hash(i + 3))) * (W + 40f) - 20f + sin(t * 0.8f + i) * 30f
        val y = frac(hash(i + 9) + t * 0.02f * (0.6f + hash(i + 5))) * (H + 40f) - 20f
        val s = (5 + hash(i + 2) * 5).dp.toPx(); val flip = 0.35f + 0.65f * abs(sin(t * 2f + i))
        withTransform({ translate(x, y); rotate(t * 60f * (hash(i + 4) - 0.5f) + i * 40f, Offset.Zero); scale(s * 0.6f, s * flip, Offset.Zero) }) {
            drawPath(petal, color, alpha = 0.85f)
        }
    }
}

/** Vaporwave: pastel dusk, a palm, katakana and scanlines (still); the VHS-sliced sun, the cyan grid, the tracking band. */
private fun DrawScope.vaporStill(W: Float, H: Float) {
    val pink = Color(0xFFFF71CE); val hy = H * 0.6f
    full(W, H, v(0f to Color(0xFF1B0F3B), 0.35f to Color(0xFF6B2D8C), (hy / H) to pink, 1f to Color(0xFF2B1055), H = H))
    drawRect(v(0f to Color(0xFF2B1055), 1f to Color(0xFF3A1A66), H = H), Offset(0f, hy), Size(W, H - hy))
    val palm = Path().apply {
        moveTo(W * 0.1f, hy + 2f); quadraticTo(W * 0.14f, hy - H * 0.12f, W * 0.08f, hy - H * 0.26f)
        lineTo(W * 0.095f, hy - H * 0.26f); quadraticTo(W * 0.16f, hy - H * 0.12f, W * 0.125f, hy + 2f); close()
    }
    drawPath(palm, Color(0xFF1B0F3B))
    val crown = Offset(W * 0.088f, hy - H * 0.262f)
    for (f in 0 until 7) rotate(-150f + f * 42f, crown) {
        drawPath(Path().apply {
            moveTo(crown.x, crown.y); quadraticTo(crown.x + W * 0.09f, crown.y - H * 0.03f, crown.x + W * 0.17f, crown.y + H * 0.02f)
            quadraticTo(crown.x + W * 0.09f, crown.y + H * 0.005f, crown.x, crown.y)
        }, Color(0xFF1B0F3B))
    }
    textPaint.textSize = 30.sp.toPx(); textPaint.typeface = android.graphics.Typeface.SERIF; textPaint.textAlign = android.graphics.Paint.Align.CENTER
    textPaint.color = Color.White.copy(alpha = 0.16f).toArgb(); textPaint.clearShadowLayer()
    drawIntoCanvas { cv -> "ヴェイパーウェイヴ".forEachIndexed { i, ch -> cv.nativeCanvas.drawText(ch.toString(), W * 0.9f, H * 0.12f + i * 34.sp.toPx(), textPaint) } }
    var sy = 0f; while (sy < H) { drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, sy), Offset(W, sy), 1f); sy += 3.dp.toPx() }
}
private fun DrawScope.vaporMoving(W: Float, H: Float, t: Float) {
    val pink = Color(0xFFFF71CE); val cyan = Color(0xFF01CDFE); val hy = H * 0.6f
    val sc = Offset(W / 2, hy - W * 0.05f); val sr = W * 0.26f
    val sunBrush = Brush.verticalGradient(listOf(Color(0xFFFFFB96), pink), sc.y - sr, sc.y + sr)
    for (k in 0 until 10) {
        val top = sc.y - sr + k * sr * 0.2f
        clipRect(0f, top, W, min(top + sr * 0.2f, hy)) {
            translate(sin(t * 1.7f + k * 1.3f) * (if (k % 3 == 0) 5f else 1.2f) * density, 0f) { drawCircle(sunBrush, sr, sc) }
        }
    }
    floorGrid(W, H, hy, frac(t * 0.4f), cyan.copy(alpha = 0.7f), cyan.copy(alpha = 0.8f), 10)
    val by = (t * 60.dp.toPx()) % (H + 80.dp.toPx()) - 40.dp.toPx()
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent), by, by + 26.dp.toPx()), Offset(0f, by), Size(W, 26.dp.toPx()))
}

/** Nord: polar night, a faint aurora in Nord's own aurora hues and pines (still); slow snowfall. */
private fun DrawScope.snowStill(W: Float, H: Float) {
    full(W, H, v(0f to Color(0xFF2E3440), 1f to Color(0xFF242933), H = H))
    val hues = listOf(Color(0xFFA3BE8C), Color(0xFF88C0D0), Color(0xFFB48EAD))
    for (i in 0 until 34) {
        val f = i / 33f; val x = W * f + sin(f * 7f) * W * 0.05f; val top = H * (0.04f + 0.03f * sin(f * 5f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, hues[i % 3].copy(alpha = 0.14f), Color.Transparent), top, top + H * 0.22f),
            Offset(x, top), Size(W / 30f, H * 0.22f), blendMode = BlendMode.Plus)
    }
    for (L in 0..1) {
        val pines = Path(); var x = -20f; var k = L * 20
        while (x < W + 20f) {
            val ph = H * (0.08f + hash(k) * 0.07f) * (1f - L * 0.2f); val pw = ph * 0.45f; val base = H * (0.9f + L * 0.06f)
            pines.moveTo(x - pw, base); pines.lineTo(x, base - ph); pines.lineTo(x + pw, base); pines.close()
            x += pw * (1.1f + hash(k + 3)); k++
        }
        val tone = if (L == 0) Color(0xFF3B4252) else Color(0xFF2B303B)
        drawPath(pines, tone)
        drawRect(tone, Offset(0f, H * (0.9f + L * 0.06f)), Size(W, H))
    }
}
private fun DrawScope.snowMoving(W: Float, H: Float, t: Float, color: Color = Color(0xFFECEFF4), count: Int = 60) {
    for (i in 0 until count) {
        val y = frac(hash(i) + t * (0.02f + hash(i + 7) * 0.025f)) * H
        val x = hash(i + 1) * W + sin(t * 0.6f + i) * 12.dp.toPx()
        drawCircle(color.copy(alpha = 0.45f + hash(i + 2) * 0.45f), (1f + hash(i + 3) * 1.6f).dp.toPx(), Offset(x, y))
    }
}

/** Dune: Arrakis. Haze-bleached sky, a pale sun and second moon, layered dunes (still); blowing sand. */
private fun DrawScope.dunesStill(W: Float, H: Float) {
    full(W, H, v(0f to Color(0xFFF6E3C3), 0.45f to Color(0xFFEFC48B), 0.62f to Color(0xFFE39A55), 1f to Color(0xFF9A5428), H = H))
    val sun = Offset(W * 0.3f, H * 0.2f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6E0).copy(alpha = 0.6f), Color.Transparent), sun, W * 0.4f), W * 0.4f, sun)
    drawCircle(Color(0xFFFFF6E0), W * 0.1f, sun)
    drawCircle(Color(0xFFFFF6E0).copy(alpha = 0.55f), W * 0.028f, Offset(W * 0.8f, H * 0.1f))
    val tones = listOf(Color(0xFFE7B27A), Color(0xFFD8945A), Color(0xFFBF7440), Color(0xFF9A5428))
    tones.forEachIndexed { L, c ->
        val base = H * (0.5f + L * 0.12f)
        val crest = Path(); val dune = Path().apply { moveTo(0f, H) }
        var x = 0f
        while (x <= W + 6f) {
            val y = base - H * 0.05f * sin(x / W * 3.2f + L * 1.7f) - H * 0.025f * sin(x / W * 7.1f + L)
            dune.lineTo(x, y); if (x == 0f) crest.moveTo(x, y) else crest.lineTo(x, y)
            x += 6.dp.toPx()
        }
        dune.lineTo(W, H); dune.close()
        drawPath(dune, Brush.verticalGradient(listOf(lerp(c, Color(0xFFFFE2B8), 0.25f), c), base - H * 0.08f, base + H * 0.12f))
        drawPath(crest, Color(0xFFFFE9C9).copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
    }
}
private fun DrawScope.dunesMoving(W: Float, H: Float, t: Float) {
    for (i in 0 until 50) {
        val x = frac(hash(i) + t * (0.04f + hash(i + 1) * 0.08f)) * W; val y = H * (0.45f + hash(i + 2) * 0.55f)
        drawLine(Color(0xFFFFF1D6).copy(alpha = 0.28f), Offset(x, y), Offset(x + (4 + hash(i + 3) * 8).dp.toPx(), y), 1.dp.toPx())
    }
}

/* ---------------- typography ---------------- */

/** A title in the theme's voice: font, case, tracking, chrome/gold/light fill. */
@Composable
fun ThemedTitle(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val look = LocalTheme.current.p.look
    val size = fontSize * look.titleScale
    val base = LocalTextStyle.current
    val style = base.copy(color = base.color.takeOrElse { LocalContentColor.current }, fontFamily = look.titleFont,
        fontWeight = look.titleWeight, letterSpacing = look.titleTracking.sp)
    // Wide display faces (Tron, Dune, Synthwave…) shrink to fit the line instead of running off the screen.
    BasicText(
        if (look.titleCaps) text.uppercase() else text, modifier,
        style = look.titleBrush?.let { style.copy(brush = Brush.linearGradient(it)) } ?: style,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = size * 0.45f, maxFontSize = size, stepSize = 0.5.sp),
    )
}

/** Hanko: a square seal-red stamp, slightly askew as if pressed by hand; the Wa theme sets it before the app name. */
@Composable
fun Stamp(text: String, size: Dp = 50.dp) = Box(
    Modifier.size(size).graphicsLayer { rotationZ = -4f }.background(Color(0xFFC85554), RoundedCornerShape(size / 5))
        .border(1.5.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(size / 5)),
    contentAlignment = Alignment.Center,
) { Text(text, color = Color.White, fontSize = (size.value * 0.5f).sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }

/** Hanko: the vermilion (or crimson) seal a ninja theme stamps beside its titles. 忍 = shinobi. */
@Composable
fun Seal(color: Color, filled: Boolean) = Box(
    Modifier.size(30.dp).then(if (filled) Modifier.background(color, RoundedCornerShape(5.dp)) else Modifier.border(1.5.dp, color, RoundedCornerShape(5.dp))),
    contentAlignment = Alignment.Center,
) { Text("忍", color = if (filled) Color.White else color, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
