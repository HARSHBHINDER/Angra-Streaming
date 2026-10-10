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
    private var micMuted = false                 // bubble control; keeps the mixer volume for unmute

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
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
        destId = dest.id; this.audio = audio
        // Layer order = filter order: screen crop first, then overlay, then facecam on top.
        crop = CropFilterRender().also { s.getGlInterface().addFilter(it) }
        loadOverlay(s, dest.overlayUri)
        if (dest.facecam && android.Manifest.permission.CAMERA.let {
                checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }) addFacecam(s)
        applyScene(dest)
        applyVolumes(prefs)
        s.startStream(dest.endpoint())
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
        if (uri.isBlank()) return
        val u = android.net.Uri.parse(uri)
        runCatching {
            overlay = if (contentResolver.getType(u)?.contains("gif") == true || u.path.orEmpty().endsWith(".gif")) {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
                overlayAspect = o.outWidth.toFloat() / o.outHeight.coerceAtLeast(1)
                GifFilterRender().apply { contentResolver.openInputStream(u)?.use { setGif(it) } }
            } else {
                val bmp = contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return
                overlayAspect = bmp.width.toFloat() / bmp.height
                ImageFilterRender().apply { setImage(bmp) }
            }
            s.getGlInterface().addFilter(overlay!!)
        }
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
        val c = d.screenView(this).cropPercent()
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
            is MixAudioSource -> { a.microphoneVolume = if (micMuted) 0f else p.micVolume; a.internalVolume = p.gameVolume }
            is MicrophoneSource -> a.microphoneVolume = if (micMuted) 0f else p.micVolume
            is InternalAudioSource -> a.internalVolume = p.gameVolume
            else -> Unit
        }
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
        data class Controls(val micMuted: Boolean, val screenHidden: Boolean, val recording: Boolean)

        fun controls(): Controls? = live?.let { svc ->
            svc.stream?.let { Controls(svc.micMuted, it.getGlInterface().isVideoMuted, it.isRecording) }
        }

        fun toggleMic() { live?.run { micMuted = !micMuted; applyVolumes(Store(this).prefsBundle) } }

        /** Privacy: viewers get a black frame (e.g. while typing a password); audio keeps going. */
        fun toggleScreen() { live?.stream?.getGlInterface()?.run { if (isVideoMuted) unMuteVideo() else muteVideo() } }

        fun toggleRecord() {
            live?.run { stream?.let { if (it.isRecording) it.stopRecord() else runCatching { it.startRecord(newRecordingPath()) {} } } }
        }
    }
}
