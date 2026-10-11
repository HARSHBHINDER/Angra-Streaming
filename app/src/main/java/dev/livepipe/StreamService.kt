package dev.livepipe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.IntentCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.encoder.input.gl.render.filters.CropFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.BaseObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.GifFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.ImageFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.BitrateAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen -> H.264/AAC -> RTMP(S), driven by the active [Destination] and [Prefs] from [Store].
 * Can record to MP4 at the same time. Starts/stops the [BubbleService] overlay. Hardware MediaCodec
 * only (RootEncoder default) to keep CPU/battery low.
 */
class StreamService : Service(), ConnectChecker {
    private var stream: GenericStream? = null
    private var projection: MediaProjection? = null
    private var startElapsed = 0L
    private var bitrateAdapter: BitrateAdapter? = null
    private var facecam: Facecam? = null
    private var maxBitrate = 0
    private var adaptive = true
    // Scene layers kept so the editor can move them while live (OBS-style).
    private var destId: String? = null
    private var audio: AudioSource? = null
    private var crop: CropFilterRender? = null
    private var overlay: BaseObjectFilterRender? = null
    private var overlayAspect = 1f
    private var facecamLayer: SurfaceFilterRender? = null
    // Bubble controls state (reset when the stream stops).
    private var gameMuted = false
    private var recPaused = false
    private var overlayHidden = false
    private var dataSaver = false
    private var zoomed = false
    private var sceneDest: Destination? = null
    private var destName = ""
    // Super Chat alerts: one card at a time on its own layer, queued.
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var chat: YouTubeChat? = null
    private var alertLayer: ImageFilterRender? = null
    private val alertQueue = ArrayDeque<SuperChat>()
    private var alertBusy = false
    private var micMuted = false                 // bubble control; keeps the mixer volume for unmute
    private var breakLayers = listOf<BaseObjectFilterRender>()
    private var canvasAspect = 16f / 9

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            // Home-screen widget controls for a running stream.
            ACTION_BREAK -> { toggleBreak(); return START_STICKY }
            ACTION_MIC -> { toggleMic(); return START_STICKY }
            ACTION_RECORD -> { toggleRecord(); Widgets.refresh(this); return START_STICKY }
        }
        val consent = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_CONSENT, Intent::class.java) }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val store = Store(this)
        val dest = store.active()
        if (consent == null || dest == null || !dest.endpoint().startsWith("rtmp")) {
            stopEverything(); return START_NOT_STICKY
        }
        state.value = State.CONNECTING
        goForeground("Connecting…")
        Widgets.refresh(this)
        val mp = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .getMediaProjection(resultCode, consent)
        if (mp == null) { stopEverything(); return START_NOT_STICKY }
        projection = mp

        val prefsEarly = store.prefsBundle
        // Internal/game audio needs API 29 + the projection token; fall back to mic below that.
        // When capturing mic, honor the chosen input device (BT/wired/USB); -1 = system default.
        fun mic() = MicrophoneSource().apply {
            if (prefsEarly.micDeviceId >= 0 && Build.VERSION.SDK_INT >= 23) {
                micDevice(prefsEarly.micDeviceId)?.let { setPreferredDevice(it) }
            }
        }
        val audio: AudioSource = when {
            Build.VERSION.SDK_INT < 29 -> mic()
            prefsEarly.audioSource == Prefs.AUDIO_MIC -> mic()
            prefsEarly.audioSource == Prefs.AUDIO_GAME -> InternalAudioSource(mp)
            else -> MixAudioSource(mp)
        }
        val s = GenericStream(applicationContext, this, ScreenSource(applicationContext, mp), audio)
        // MediaProjection only emits frames when the screen changes; force a constant fps.
        s.getGlInterface().setForceRender(true, dest.fps)
        s.setVideoCodec(if (dest.hevc) VideoCodec.H265 else VideoCodec.H264)
        val rotation = if (dest.portrait) 90 else 0
        val ok = s.prepareVideo(dest.width, dest.height, dest.videoBitrate, dest.fps, dest.keyframeSeconds, rotation) &&
            s.prepareAudio(dest.sampleRate, dest.stereo, dest.audioBitrate, echoCanceler = true, noiseSuppressor = true)
        if (!ok) { s.release(); stopEverything(); return START_NOT_STICKY }
        stream = s

        val prefs = store.prefsBundle
        maxBitrate = dest.videoBitrate
        adaptive = prefs.adaptiveBitrate
        destId = dest.id; this.audio = audio; canvasAspect = dest.canvasAspect(); destName = dest.name
        // Layer order = filter order: screen crop first, then overlay, then facecam on top.
        crop = CropFilterRender().also { s.getGlInterface().addFilter(it) }
        loadOverlay(s, dest.overlayUri)
        if (dest.facecam && android.Manifest.permission.CAMERA.let {
                checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }) addFacecam(s)
        applyScene(dest)
        applyVolumes(prefs)
        s.startStream(dest.endpoint())
        superChatStatus.value = ""
        if (prefs.superChat && prefs.ytApiKey.isNotBlank() && dest.ytSource.isNotBlank()) {
            chat = YouTubeChat(prefs.ytApiKey, dest.ytSource, { e -> main.post { queueAlert(e) } }, { superChatStatus.value = it })
        }
        live = this
        // OBS-style stream delay: buffers encoded output N seconds before sending. ponytail: cheap for
        // short delays (~bitrate × seconds of RAM); long delays at high bitrate cost real memory.
        if (dest.delaySeconds > 0) runCatching { s.getStreamClient().setDelay(dest.delaySeconds * 1000L) }
        if (prefs.recordWhileStreaming) runCatching { s.startRecord(newRecordingPath()) {} }

        startElapsed = SystemClock.elapsedRealtime()
        liveSince.value = startElapsed
        if (prefs.bubbleEnabled && Build.VERSION.SDK_INT >= 23 &&
            android.provider.Settings.canDrawOverlays(this)) {
            BubbleService.start(this, prefs, startElapsed)
        }
        return START_STICKY
    }

    private fun stopEverything() {
        BubbleService.stop(this)
        chat?.stop(); chat = null
        main.removeCallbacksAndMessages(null)
        alertQueue.clear(); alertBusy = false; alertLayer = null; superChatStatus.value = ""
        facecam?.stop(); facecam = null
        stream?.run {
            if (isRecording) stopRecord()
            if (isStreaming) stopStream()
            release()
        }
        projection?.stop()
        if (live === this) live = null
        stream = null; projection = null; bitrateAdapter = null
        audio = null; crop = null; overlay = null; facecamLayer = null; destId = null; micMuted = false
        breakLayers = emptyList(); onBreak.value = false
        gameMuted = false; recPaused = false; overlayHidden = false; dataSaver = false; zoomed = false; sceneDest = null
        Widgets.refresh(this)
        state.value = State.IDLE; lastBitrate.value = 0L; liveSince.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { state.value = State.IDLE; super.onDestroy() }

    private fun micDevice(id: Int): android.media.AudioDeviceInfo? =
        (getSystemService(AUDIO_SERVICE) as android.media.AudioManager)
            .getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }

    /** Overlay layer: static image (PNG/JPG/WebP) or animated GIF; placed by [applyScene]. */
    private fun loadOverlay(s: GenericStream, uri: String) {
        imageLayer(uri)?.let { (f, aspect) -> overlay = f; overlayAspect = aspect; s.getGlInterface().addFilter(f) }
    }

    /** Overlay / break-screen layer: animated GIF, or any still the phone decodes (downsampled to ~1920 px). */
    private fun imageLayer(uri: String): Pair<BaseObjectFilterRender, Float>? {
        if (uri.isBlank()) return null
        val u = android.net.Uri.parse(uri)
        return runCatching {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
            if (contentResolver.getType(u)?.contains("gif") == true || u.path.orEmpty().endsWith(".gif")) {
                GifFilterRender().apply { contentResolver.openInputStream(u)?.use { setGif(it) } } to
                    o.outWidth.toFloat() / o.outHeight.coerceAtLeast(1)
            } else {
                var sample = 1
                while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 1920) sample *= 2
                val bmp = contentResolver.openInputStream(u)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return null
                ImageFilterRender().apply { setImage(bmp) } to bmp.width.toFloat() / bmp.height
            }
        }.getOrNull()
    }

    /**
     * Break screen over everything (stream keeps running, audio keeps going): plain black with no name or
     * branding, or the user's image fitted on black, never stretched.
     */
    private fun applyBreak(on: Boolean) {
        val gl = stream?.getGlInterface() ?: return
        breakLayers.forEach { gl.removeFilter(it) }
        breakLayers = emptyList()
        onBreak.value = on
        Widgets.refresh(this)
        if (!on) return
        val black = ImageFilterRender().apply {
            setImage(android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                .apply { eraseColor(android.graphics.Color.BLACK) })
        }
        gl.addFilter(black); black.setScale(100f, 100f); black.setPosition(0f, 0f)
        breakLayers = listOf(black)
        val (img, aspect) = imageLayer(Store(this).prefsBundle.breakImageUri) ?: return
        val (w, h) = if (aspect > canvasAspect) 100f to 100f * canvasAspect / aspect else 100f * aspect / canvasAspect to 100f
        gl.addFilter(img); img.setScale(w, h); img.setPosition((100 - w) / 2, (100 - h) / 2)
        breakLayers = listOf(black, img)
    }

    /** Facecam layer: front camera into a surface the GL pipeline composites; placed by [applyScene]. */
    private fun addFacecam(s: GenericStream) {
        facecamLayer = SurfaceFilterRender(object : SurfaceFilterRender.SurfaceReadyCallback {
            override fun surfaceReady(surfaceTexture: android.graphics.SurfaceTexture) {
                facecam = Facecam(applicationContext, surfaceTexture).also { it.start() }
            }
        }).also { s.getGlInterface().addFilter(it) }
    }

    /** Positions every layer from the channel's scene. Object filters take top-left % of the canvas. */
    private fun applyScene(d: Destination) {
        sceneDest = d
        // Bubble "Zoom" punches in 1.5x on top of the scene's own zoom.
        val c = (if (zoomed) d.copy(screenZoom = (d.screenZoom * 1.5f).coerceAtMost(4f)) else d).screenView(this).cropPercent()
        crop?.setCropArea(c[0], c[1], c[2], c[3])
        overlay?.let {
            val h = Scene.overlayH(d.overlayW, overlayAspect, d.canvasAspect())
            it.setScale(d.overlayW * 100, h * 100)
            it.setPosition((d.overlayX - d.overlayW / 2) * 100, (d.overlayY - h / 2) * 100)
        }
        facecamLayer?.let {
            val f = d.facecamScale.coerceIn(10, 60) / 100f
            it.setScale(f * 100, f * 100)
            it.setPosition((d.facecamX - f / 2) * 100, (d.facecamY - f / 2) * 100)
        }
    }

    private fun applyVolumes(p: Prefs) {
        when (val a = audio) {
            is MixAudioSource -> { a.microphoneVolume = if (micMuted) 0f else p.micVolume; a.internalVolume = if (gameMuted) 0f else p.gameVolume }
            is MicrophoneSource -> a.microphoneVolume = if (micMuted) 0f else p.micVolume
            is InternalAudioSource -> a.internalVolume = if (gameMuted) 0f else p.gameVolume
            else -> Unit
        }
    }

    private fun queueAlert(e: SuperChat) { alertQueue.addLast(e); if (!alertBusy) showNextAlert() }

    /** Rises and fades the next card in at the scene's alert spot, holds it, fades it out, then the next. */
    private fun showNextAlert() {
        val e = alertQueue.removeFirstOrNull()
        val s = stream
        val d = sceneDest
        if (e == null || s == null || d == null) { alertBusy = false; return }
        alertBusy = true
        val p = Store(this).prefsBundle
        val bmp = SuperChatCard.render(e, p.alertStyle, p.alertShowTotal)
        val f = alertLayer ?: ImageFilterRender().also {
            it.setImage(bmp); it.alpha = 0f; s.getGlInterface().addFilter(it); alertLayer = it
        }
        f.setImage(bmp)
        val w = d.alertW
        val h = w * canvasAspect * bmp.height / bmp.width
        val x = (d.alertX - w / 2) * 100
        val y = (d.alertY - h / 2) * 100
        f.setScale(w * 100, h * 100)
        animateAlert(f, 0f, 1f, x, y, 420) {
            main.postDelayed({
                animateAlert(f, 1f, 0f, x, y, 320) { main.postDelayed({ showNextAlert() }, 450) }
            }, p.alertSeconds * 1000L)
        }
    }

    private fun animateAlert(f: ImageFilterRender, from: Float, to: Float, x: Float, y: Float, ms: Long, done: () -> Unit) {
        android.animation.ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            interpolator = android.view.animation.DecelerateInterpolator(2f)
            addUpdateListener {
                val a = it.animatedValue as Float
                f.alpha = a
                f.setPosition(x, y + (1 - a) * 4f)              // rises into place as it appears
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = done()
            })
            start()
        }
    }

    /** Saves a frame of the stream output (exactly what viewers see) to Pictures/Angra Streaming. */
    private fun savePicture(bmp: android.graphics.Bitmap): Boolean = runCatching {
        val name = "stream_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Angra Streaming")
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            contentResolver.openOutputStream(uri)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) } ?: false
        } else {
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), name).outputStream()
                .use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
        }
    }.getOrDefault(false)

    /** Notes the stream time in highlights.txt beside the recordings, to find the moment later. */
    private fun markHighlight(): String {
        val s = (SystemClock.elapsedRealtime() - startElapsed) / 1000
        val t = "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "LivePipe").apply { mkdirs() }
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        File(dir, "highlights.txt").appendText("$now  stream time $t  $destName\n")
        return t
    }

    private fun newRecordingPath(): String {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "LivePipe").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "livepipe_$stamp.mp4").absolutePath
    }

    private fun goForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live broadcast", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0,
            Intent(this, StreamService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Angra Streaming")
            .setContentText(text)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(ID, n)
    }

    override fun onConnectionStarted(url: String) = Unit
    override fun onConnectionSuccess() {
        state.value = State.LIVE
        goForeground("LIVE")
        Widgets.refresh(this)
        if (adaptive) bitrateAdapter = BitrateAdapter { bps ->
            stream?.setVideoBitrateOnFly(bps)
        }.apply { setMaxBitrate(maxBitrate) }
    }
    override fun onNewBitrate(bitrate: Long) {
        lastBitrate.value = bitrate              // feeds the live latency/health meter on the Stream tab
        bitrateAdapter?.adaptBitrate(bitrate)
    }
    override fun onConnectionFailed(reason: String) { state.value = State.ERROR; stopEverything() }
    override fun onDisconnect() = stopEverything()
    override fun onAuthError() { state.value = State.ERROR; stopEverything() }
    override fun onAuthSuccess() = Unit

    enum class State { IDLE, CONNECTING, LIVE, ERROR }

    companion object {
        const val ACTION_STOP = "dev.livepipe.STOP"
        const val ACTION_BREAK = "dev.livepipe.BREAK"
        const val ACTION_MIC = "dev.livepipe.MIC"
        const val ACTION_RECORD = "dev.livepipe.RECORD"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_CONSENT = "consent"
        private const val CHANNEL = "live"
        private const val ID = 1

        /** Process-wide stream state the Compose UI observes. */
        val state = MutableStateFlow(State.IDLE)
        val lastBitrate = MutableStateFlow(0L)   // last reported upload bitrate (bps) for the health meter
        val liveSince = MutableStateFlow(0L)     // elapsedRealtime when the stream started; 0 = not streaming

        @Volatile private var live: StreamService? = null

        /** Live scene edits (editor "Done" while streaming) move layers on air for the channel being streamed. */
        fun updateScene(d: Destination) { live?.takeIf { it.destId == d.id }?.applyScene(d) }

        /** Mixer changes apply instantly while live. */
        fun updateVolumes(p: Prefs) { live?.applyVolumes(p) }

        /** Bubble controls. All no-ops when nothing is live. */
        data class Controls(
            val micMuted: Boolean, val onBreak: Boolean, val recording: Boolean, val recPaused: Boolean,
            val gameMuted: Boolean, val facecam: Boolean, val overlayHidden: Boolean, val dataSaver: Boolean, val zoomed: Boolean,
        )

        fun controls(): Controls? = live?.let { svc ->
            svc.stream?.let {
                Controls(svc.micMuted, onBreak.value, it.isRecording, svc.recPaused, svc.gameMuted, svc.facecamLayer != null,
                    svc.overlayHidden, svc.dataSaver, svc.zoomed)
            }
        }

        /** True while the break screen is on air; the Stream tab and bubble reflect it. */
        val onBreak = MutableStateFlow(false)

        /** Break screen on/off; calling it while on reloads a changed break image. */
        fun setBreak(on: Boolean) { live?.applyBreak(on) }

        fun toggleMic() { live?.run { micMuted = !micMuted; applyVolumes(Store(this).prefsBundle); Widgets.refresh(this) } }

        fun toggleBreak() = setBreak(!onBreak.value)

        fun toggleRecord() {
            live?.run {
                stream?.let { if (it.isRecording) it.stopRecord() else runCatching { it.startRecord(newRecordingPath()) {} } }
                recPaused = false
            }
        }

        fun toggleRecordPause() {
            live?.run {
                stream?.takeIf { it.isRecording }?.let { if (recPaused) it.resumeRecord() else it.pauseRecord(); recPaused = !recPaused }
            }
        }

        fun toggleGame() { live?.run { gameMuted = !gameMuted; applyVolumes(Store(this).prefsBundle) } }

        fun toggleOverlay() { live?.run { overlayHidden = !overlayHidden; overlay?.alpha = if (overlayHidden) 0f else 1f } }

        /** Halves the bitrate for weak connections (the adaptive ceiling follows). */
        fun toggleDataSaver() {
            live?.run {
                dataSaver = !dataSaver
                val b = if (dataSaver) maxBitrate / 2 else maxBitrate
                bitrateAdapter?.setMaxBitrate(b); stream?.setVideoBitrateOnFly(b)
            }
        }

        fun toggleZoom() { live?.run { zoomed = !zoomed; sceneDest?.let { applyScene(it) } } }

        /** Facecam on/off mid-stream. False when the camera permission hasn't been granted in the app. */
        fun toggleFacecam(): Boolean = live?.run {
            val s = stream ?: return@run false
            facecamLayer?.let {
                s.getGlInterface().removeFilter(it); facecam?.stop(); facecam = null; facecamLayer = null
                return@run true
            }
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@run false
            addFacecam(s)
            sceneDest?.let { applyScene(it) }
            if (onBreak.value) applyBreak(true)        // the break screen stays on top of everything
            true
        } ?: false

        /** Saves what viewers see right now to Pictures; [done] runs on a background thread. */
        fun screenshot(done: (Boolean) -> Unit) {
            val svc = live ?: return done(false)
            val gl = svc.stream?.getGlInterface() ?: return done(false)
            gl.takePhoto { bmp -> Thread { done(svc.savePicture(bmp)) }.start() }
        }

        fun markHighlight(): String? = live?.markHighlight()

        /** Super Chat connection status for the Super Chat screen ("Connected…", errors); empty when off. */
        val superChatStatus = MutableStateFlow("")

        /** Plays a sample card on the live stream, so placement and style can be checked on air. */
        fun testSuperChat(tier: Int) { live?.queueAlert(SuperChatCard.sample(tier).copy(id = "test-${System.nanoTime()}")) }
    }
}
