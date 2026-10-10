package dev.livepipe

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Screen-layer geometry shared by the scene editor and the stream, so the preview is exactly what viewers see.
 *
 * The captured screen arrives fitted into the stream canvas (Android letterboxes the phone screen into the
 * stream-sized virtual display): a centred content rect [cw]×[ch] in canvas fractions. We show a window of
 * that frame, 1/[z] wide and tall, centred at ([cx],[cy]), stretched back over the whole canvas.
 * [rx]/[ry] are how far the centre may move from 0.5 (pan range).
 */
data class ScreenView(
    val z: Float, val cx: Float, val cy: Float,
    val cw: Float, val ch: Float, val rx: Float, val ry: Float,
) {
    /** Crop area (left, top, width, height) in percent of the frame, for RootEncoder's CropFilterRender. */
    fun cropPercent(): FloatArray {
        val v = 100f / z
        return floatArrayOf(cx * 100 - v / 2, cy * 100 - v / 2, v, v)
    }

    /** Where the screen content lands on the canvas: left, top, width, height in canvas fractions. */
    fun contentRect(): FloatArray =
        floatArrayOf((0.5f - cw / 2 - cx) * z + 0.5f, (0.5f - ch / 2 - cy) * z + 0.5f, cw * z, ch * z)

    val isIdentity get() = z == 1f && cx == 0.5f && cy == 0.5f
}

object Scene {
    const val MAX_ZOOM = 4f

    /** [sourceAspect] = screen width/height, [canvasAspect] = stream width/height. */
    fun screenView(sourceAspect: Float, canvasAspect: Float, fit: Boolean, zoom: Float, panX: Float, panY: Float): ScreenView {
        val cw = min(1f, sourceAspect / canvasAspect)
        val ch = min(1f, canvasAspect / sourceAspect)
        val base = if (fit) 1f else max(1f / cw, 1f / ch)          // fill = smallest zoom with no borders
        val z = base * zoom.coerceIn(1f, MAX_ZOOM)
        val v = 1f / z
        // Filling keeps the window on the content (borders never return); fitting may roam the whole frame.
        val rx = max(0f, ((if (fit) 1f else cw) - v) / 2)
        val ry = max(0f, ((if (fit) 1f else ch) - v) / 2)
        return ScreenView(z, 0.5f + rx * panX.coerceIn(-1f, 1f), 0.5f + ry * panY.coerceIn(-1f, 1f), cw, ch, rx, ry)
    }

    /** Inverse of the pan mapping: a window centre back to pan -1..1 (0 when there's no room to pan). */
    fun panFrom(centre: Float, range: Float): Float =
        if (range <= 0f) 0f else ((centre - 0.5f) / range).coerceIn(-1f, 1f)

    /** Overlay height as a canvas fraction, for an image of [imageAspect] (w/h) that is [w] canvas-widths wide. */
    fun overlayH(w: Float, imageAspect: Float, canvasAspect: Float) = w * canvasAspect / imageAspect
}

/** Phone screen width/height as it lands in a stream of the given orientation (games run landscape). */
fun phoneAspect(ctx: Context, portraitStream: Boolean): Float {
    val wm = ctx.getSystemService(WindowManager::class.java)
    val (a, b) = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    else Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }.let { it.x to it.y }
    val long = max(a, b).toFloat(); val short = min(a, b).toFloat()
    return if (portraitStream) short / long else long / short
}

/** Stream canvas width/height (portrait channels are rotated 90°). */
fun Destination.canvasAspect(): Float = if (portrait) height.toFloat() / width else width.toFloat() / height

fun Destination.screenView(ctx: Context) =
    Scene.screenView(phoneAspect(ctx, portrait), canvasAspect(), screenFit, screenZoom, screenPanX, screenPanY)
