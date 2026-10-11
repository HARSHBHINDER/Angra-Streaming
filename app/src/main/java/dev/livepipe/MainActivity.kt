package dev.livepipe

import androidx.compose.runtime.collectAsState
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import dev.livepipe.ui.App
import dev.livepipe.ui.BubbleCropper
import dev.livepipe.ui.LivePipeTheme

/**
 * Compose host. Owns the Activity-result flows the UI can't do itself:
 * runtime permissions, the MediaProjection consent dialog, overlay permission, and image picking.
 */
class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private val prefs = mutableStateOf(Prefs())   // single source of truth for prefs, shared with Compose

    private fun setPrefs(p: Prefs) {
        if (p.charging != prefs.value.charging) Charging.setMode(this, p.charging)
        val widgetsChanged = p.recordWhileStreaming != prefs.value.recordWhileStreaming
        prefs.value = p; store.prefsBundle = p
        if (widgetsChanged) Widgets.refresh(this)
    }
    private val openSuperChat = mutableStateOf(false)      // widget's Super Chat button

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.RECORD_AUDIO] == true) ensureOverlayThenCapture()
    }
    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, StreamService::class.java)
                .putExtra(StreamService.EXTRA_RESULT_CODE, r.resultCode)
                .putExtra(StreamService.EXTRA_CONSENT, data))
        }
    }
    // Picked bubble image goes to the cropper first; the cropped circle is saved as the bubble icon.
    private val cropSource = mutableStateOf<Uri?>(null)
    private var cropLogo = false                       // the open cropper is for the app logo (square), not the bubble
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { cropLogo = false; cropSource.value = uri }
    }
    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { cropLogo = true; cropSource.value = uri }
    }

    private fun saveLogo(bmp: Bitmap) {
        filesDir.listFiles { f -> f.name.startsWith("logo_") }?.forEach { it.delete() }
        val f = File(filesDir, "logo_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        setPrefs(prefs.value.copy(logoUri = Uri.fromFile(f).toString()))
        cropSource.value = null
        // A Home Screen icon made earlier picks up the new picture.
        runCatching { androidx.core.content.pm.ShortcutManagerCompat.updateShortcuts(this, listOf(logoShortcut(bmp))) }
    }

    /** Home Screen icon carrying the user's logo; opens the app like the launcher icon. */
    private fun logoShortcut(logo: Bitmap): androidx.core.content.pm.ShortcutInfoCompat {
        // Adaptive icon: 108dp canvas, the launcher masks the centre ~72dp, so the logo fills the visible area.
        val s = 432
        val icon = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(icon).apply {
            drawColor(android.graphics.Color.BLACK)
            val inner = (s * 0.70f).toInt(); val o = (s - inner) / 2
            drawBitmap(logo, null, android.graphics.Rect(o, o, o + inner, o + inner), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        }
        return androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "custom_logo")
            .setShortLabel("Angra Streaming")
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithAdaptiveBitmap(icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
    }

    /** Deletes the custom logo: the oni returns in the app and the home-screen icon made from it is retired. */
    private fun resetLogo() {
        filesDir.listFiles { f -> f.name.startsWith("logo_") }?.forEach { it.delete() }
        setPrefs(prefs.value.copy(logoUri = ""))
        runCatching { androidx.core.content.pm.ShortcutManagerCompat.disableShortcuts(this, listOf("custom_logo"), "Logo removed") }
    }

    private fun pinLogoIcon() {
        val uri = prefs.value.logoUri
        if (uri.isBlank()) { android.widget.Toast.makeText(this, "Choose your logo first", android.widget.Toast.LENGTH_SHORT).show(); return }
        val bmp = runCatching { contentResolver.openInputStream(Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() ?: return
        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            android.widget.Toast.makeText(this, "Your launcher doesn't support adding icons", android.widget.Toast.LENGTH_LONG).show(); return
        }
        androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, logoShortcut(bmp), null)
    }

    private fun saveBubble(bmp: Bitmap) {
        // New file name each time so the bubble reloads; drop the previous crop.
        filesDir.listFiles { f -> f.name.startsWith("bubble_") }?.forEach { it.delete() }
        val f = File(filesDir, "bubble_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        setPrefs(prefs.value.copy(bubbleImageUri = Uri.fromFile(f).toString()))
        cropSource.value = null
    }

    // Mic list refreshes as headsets/USB mics come and go.
    private val mics = mutableStateOf(listOf<Pair<Int, String>>())
    private val micCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refreshMics()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refreshMics()
    }

    /** A newly plugged/paired mic is picked automatically; if the chosen one goes away, back to the phone mic. */
    private fun refreshMics() {
        val old = mics.value.map { it.first }.toSet()
        val now = micDevices()
        mics.value = now
        val p = prefs.value
        val fresh = now.lastOrNull { it.first !in old }
        when {
            fresh != null -> setPrefs(p.copy(micDeviceId = fresh.first))
            p.micDeviceId >= 0 && now.none { it.first == p.micDeviceId } -> setPrefs(p.copy(micDeviceId = -1))
        }
    }
    /** Input devices for the mic picker: (AudioDeviceInfo.id, label); -1 = system default. */
    private fun micDevices(): List<Pair<Int, String>> {
        val am = getSystemService(android.media.AudioManager::class.java)
        // Only external mics: built-in ones are covered by Default (and all report the phone model as name).
        // Repeats are numbered: "Bluetooth · Buds", "Bluetooth · Buds 2".
        val seen = mutableMapOf<String, Int>()
        val list = am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).mapNotNull { d ->
            val type = micTypeName(d.type) ?: return@mapNotNull null
            val name = d.productName?.toString()?.takeIf { it.isNotBlank() }
            val base = if (name != null) "$type · $name" else type
            val n = seen.merge(base, 1, Int::plus)!!
            d.id to if (n > 1) "$base $n" else base
        }
        return listOf(-1 to "Phone microphone") + list
    }

    private fun micTypeName(type: Int) = when (type) {
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        else -> null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        prefs.value = store.prefsBundle
        ChargeService.sync(this, prefs.value.charging)   // keep a chosen charge limit enforced
        mics.value = micDevices()
        getSystemService(android.media.AudioManager::class.java).registerAudioDeviceCallback(micCallback, null)
        setContent {
            // Live theme pauses while a stream is starting or on air: the app is out of sight then anyway.
            val streaming = StreamService.state.collectAsState().value.let { it == StreamService.State.LIVE || it == StreamService.State.CONNECTING }
            LivePipeTheme(prefs.value.theme, live = prefs.value.liveTheme && !streaming) {
                Box {
                App(
                    initialDestinations = store.destinations,
                    initialActiveId = store.activeId,
                    prefs = prefs.value,
                    onPrefs = ::setPrefs,
                    onPersistDestinations = { list, active -> store.destinations = list; store.activeId = active; Widgets.refresh(this@MainActivity) },
                    onToggleStream = ::toggleStream,
                    onPickBubbleImage = { pickImage.launch("image/*") },
                    micDevices = mics.value,
                    onPreviewBubble = ::previewBubble,
                    onPickLogo = { pickLogo.launch("image/*") },
                    onPinIcon = ::pinLogoIcon,
                    onResetLogo = ::resetLogo,
                    openSuperChat = openSuperChat.value,
                    onSuperChatOpened = { openSuperChat.value = false },
                )
                cropSource.value?.let { BubbleCropper(it, onCancel = { cropSource.value = null }, onDone = { b -> if (cropLogo) saveLogo(b) else saveBubble(b) }, round = !cropLogo) }
                }
            }
        }
        handleWidget(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidget(intent)
    }

    override fun onResume() {
        super.onResume()
        prefs.value = store.prefsBundle          // the widget may have flipped Record / Bubble
    }

    /** Home-screen widget taps that need the app: Go Live (Android asks for capture consent here), Super Chat. */
    private fun handleWidget(i: Intent?) {
        when (i?.action) {
            Widgets.ACTION_GO_LIVE -> {
                val s = StreamService.state.value
                if ((s == StreamService.State.IDLE || s == StreamService.State.ERROR) && store.active() != null) toggleStream()
            }
            Widgets.ACTION_SUPER_CHAT -> openSuperChat.value = true
        }
    }

    /** Bubble preview before going live; needs the draw-over-apps permission. */
    private fun previewBubble(on: Boolean) {
        if (!on) { BubbleService.stop(this); return }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        BubbleService.preview(this, prefs.value)
    }

    override fun onDestroy() {
        getSystemService(android.media.AudioManager::class.java).unregisterAudioDeviceCallback(micCallback)
        super.onDestroy()
    }

    private fun toggleStream() {
        val s = StreamService.state.value
        if (s != StreamService.State.IDLE && s != StreamService.State.ERROR) {
            startService(Intent(this, StreamService::class.java).setAction(StreamService.ACTION_STOP))
            return
        }
        val need = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (store.active()?.facecam == true) add(Manifest.permission.CAMERA)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) perms.launch(need.toTypedArray()) else ensureOverlayThenCapture()
    }

    private fun ensureOverlayThenCapture() {
        val prefs = store.prefsBundle
        if (prefs.bubbleEnabled && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            // Ask once; streaming still works without it, the bubble just won't show.
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val mpm = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        capture.launch(mpm.createScreenCaptureIntent())
    }
}
