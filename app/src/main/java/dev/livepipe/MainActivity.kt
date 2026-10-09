package dev.livepipe

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

    private fun setPrefs(p: Prefs) { prefs.value = p; store.prefsBundle = p }

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
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) cropSource.value = uri
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
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { mics.value = micDevices() }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { mics.value = micDevices() }
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
        mics.value = micDevices()
        getSystemService(android.media.AudioManager::class.java).registerAudioDeviceCallback(micCallback, null)
        setContent {
            LivePipeTheme {
                Box {
                App(
                    initialDestinations = store.destinations,
                    initialActiveId = store.activeId,
                    prefs = prefs.value,
                    onPrefs = ::setPrefs,
                    onPersistDestinations = { list, active -> store.destinations = list; store.activeId = active },
                    onToggleStream = ::toggleStream,
                    onPickBubbleImage = { pickImage.launch("image/*") },
                    micDevices = mics.value,
                )
                cropSource.value?.let { BubbleCropper(it, onCancel = { cropSource.value = null }, onDone = ::saveBubble) }
                }
            }
        }
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
