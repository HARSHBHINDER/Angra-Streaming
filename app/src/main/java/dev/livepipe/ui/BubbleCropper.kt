package dev.livepipe.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * iOS contact-photo style cropper for the floating bubble: the picture sits under a circular window,
 * drag to move it, pinch to zoom. "Choose" renders exactly what's inside the circle to a 512px round PNG.
 */
@Composable
fun BubbleCropper(source: Uri, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(source) { decodeForCrop(ctx.contentResolver, source) }
    if (bmp == null) { onCancel(); return }
    val img = remember(bmp) { bmp.asImageBitmap() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var window by remember { mutableStateOf(Rect.Zero) }      // circle bounds in canvas px
    var base by remember { mutableFloatStateOf(1f) }          // screen px per bitmap px at scale 1 (image covers circle)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) - 48.dp).toPx() }
            Canvas(
                Modifier.fillMaxSize().pointerInput(bmp) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        // Keep the circle fully covered: clamp the pan to the image's overhang.
                        val w = bmp.width * base * scale; val h = bmp.height * base * scale
                        val mx = max(0f, (w - d) / 2); val my = max(0f, (h - d) / 2)
                        offset = Offset((offset.x + pan.x).coerceIn(-mx, mx), (offset.y + pan.y).coerceIn(-my, my))
                    }
                },
            ) {
                val c = center
                window = Rect(c.x - d / 2, c.y - d / 2, c.x + d / 2, c.y + d / 2)
                base = d / min(bmp.width, bmp.height)
                val w = bmp.width * base * scale; val h = bmp.height * base * scale
                drawImage(img, dstOffset = IntOffset((c.x + offset.x - w / 2).roundToInt(), (c.y + offset.y - h / 2).roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()))
                // Dim everything outside the circle, then outline the window.
                drawPath(Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size)); addOval(window)
                }, Color.Black.copy(alpha = 0.62f))
                drawCircle(Color.White.copy(alpha = 0.9f), d / 2, c, style = Stroke(1.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Move and Scale", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Drag to position · pinch to zoom", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Box(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cancel", color = Color.White, fontSize = 17.sp, modifier = Modifier.iosTap { onCancel() }.padding(8.dp))
                Text("Choose", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.iosTap { onDone(renderCircle(bmp, window, base * scale, offset)) }.padding(8.dp))
            }
        }
    }
}

/** Downsampled, EXIF-rotated, software bitmap (so it can be drawn into a Canvas). */
private fun decodeForCrop(cr: android.content.ContentResolver, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = max(info.size.width, info.size.height) / 1600
            if (s > 1) dec.setTargetSampleSize(s)
        }
    } else {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        opts.inSampleSize = max(1, max(opts.outWidth, opts.outHeight) / 1600)
        opts.inJustDecodeBounds = false
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}.getOrNull()

/** Maps the on-screen circle back to bitmap pixels and draws it into a round, transparent-cornered PNG. */
private fun renderCircle(bmp: Bitmap, window: Rect, pxPerBmp: Float, offset: Offset): Bitmap {
    val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    // Image top-left on screen, then the window's top-left expressed in bitmap coordinates.
    val imgLeft = window.center.x + offset.x - bmp.width * pxPerBmp / 2
    val imgTop = window.center.y + offset.y - bmp.height * pxPerBmp / 2
    val srcL = (window.left - imgLeft) / pxPerBmp; val srcT = (window.top - imgTop) / pxPerBmp
    val srcSize = window.width / pxPerBmp
    canvas.clipPath(android.graphics.Path().apply { addCircle(256f, 256f, 256f, android.graphics.Path.Direction.CW) })
    canvas.drawBitmap(bmp,
        android.graphics.Rect(srcL.roundToInt(), srcT.roundToInt(), (srcL + srcSize).roundToInt(), (srcT + srcSize).roundToInt()),
        android.graphics.Rect(0, 0, 512, 512), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    return out
}
