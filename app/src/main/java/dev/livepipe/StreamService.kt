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
import com.pedro.encoder.input.gl.render.filters.`object`.GifFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.ImageFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import com.pedro.encoder.utils.gl.TranslateTo
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            ACTION_BUBBLE_TAP -> { // persist the icon<->timer choice the user toggled on the bubble
                val store = Store(this)
                store.prefsBundle = store.prefsBundle.let { it.copy(bubbleShowsTimer = !it.bubbleShowsTimer) }
                return START_STICKY
            }
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
        applyOverlay(s, dest.overlayUri)
        if (dest.facecam && android.Manifest.permission.CAMERA.let {
                checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }) applyFacecam(s, dest.facecamScale, dest.facecamCorner)
        s.startStream(dest.endpoint())
        // OBS-style stream delay: buffers encoded output N seconds before sending. ponytail: cheap for
        // short delays (~bitrate × seconds of RAM); long delays at high bitrate cost real memory.
        if (dest.delaySeconds > 0) runCatching { s.getStreamClient().setDelay(dest.delaySeconds * 1000L) }
        if (prefs.recordWhileStreaming) runCatching { s.startRecord(newRecordingPath()) {} }

        startElapsed = SystemClock.elapsedRealtime()
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
        stream = null; projection = null; bitrateAdapter = null
        state.value = State.IDLE; lastBitrate.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { state.value = State.IDLE; super.onDestroy() }

    private fun micDevice(id: Int): android.media.AudioDeviceInfo? =
        (getSystemService(AUDIO_SERVICE) as android.media.AudioManager)
            .getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }

    /** Composite one overlay onto the stream, top-right, ~25% size. Static (PNG/JPG/WebP) via
     *  ImageFilterRender, animated (GIF) via GifFilterRender. ponytail: one overlay per channel;
     *  multi-layer frame stacks if real layouts are needed. */
    private fun applyOverlay(s: GenericStream, uri: String) {
        if (uri.isBlank()) return
        val u = android.net.Uri.parse(uri)
        val isGif = contentResolver.getType(u)?.contains("gif") == true
        runCatching {
            if (isGif) {
                val f = GifFilterRender()
                contentResolver.openInputStream(u)?.use { f.setGif(it) }
                f.setScale(25f, 25f); f.setPosition(TranslateTo.TOP_RIGHT)
                s.getGlInterface().addFilter(f)   // addFilter so facecam can coexist
            } else {
                val bmp = contentResolver.openInputStream(u)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                } ?: return
                val f = ImageFilterRender()
                f.setImage(bmp); f.setScale(25f, 25f); f.setPosition(TranslateTo.TOP_RIGHT)
                s.getGlInterface().addFilter(f)
            }
        }
    }

    /** Front-camera PiP at the chosen [corner], size = [scalePct]% of frame. */
    private fun applyFacecam(s: GenericStream, scalePct: Int, corner: Int) {
        val render = SurfaceFilterRender(object : SurfaceFilterRender.SurfaceReadyCallback {
            override fun surfaceReady(surfaceTexture: android.graphics.SurfaceTexture) {
                facecam = Facecam(applicationContext, surfaceTexture).also { it.start() }
            }
        })
        s.getGlInterface().addFilter(render)
        val sc = scalePct.coerceIn(10, 60).toFloat()
        render.setScale(sc, sc)
        render.setPosition(when (corner) {
            0 -> TranslateTo.TOP_LEFT
            1 -> TranslateTo.TOP_RIGHT
            3 -> TranslateTo.BOTTOM_RIGHT
            else -> TranslateTo.BOTTOM_LEFT
        })
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
        const val ACTION_BUBBLE_TAP = "dev.livepipe.BUBBLE_TAP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_CONSENT = "consent"
        private const val CHANNEL = "live"
        private const val ID = 1

        /** Process-wide stream state the Compose UI observes. */
        val state = MutableStateFlow(State.IDLE)
        val lastBitrate = MutableStateFlow(0L)   // last reported upload bitrate (bps) for the health meter
    }
}
