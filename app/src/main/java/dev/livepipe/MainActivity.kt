package dev.livepipe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import dev.livepipe.ui.App
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
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setPrefs(prefs.value.copy(bubbleImageUri = uri.toString()))
        }
    }
    /** Input devices for the mic picker: (AudioDeviceInfo.id, label); -1 = system default. */
    private fun micDevices(): List<Pair<Int, String>> {
        val am = getSystemService(android.media.AudioManager::class.java)
        val list = am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).map {
            it.id to (it.productName?.toString()?.ifBlank { null } ?: micTypeName(it.type))
        }
        return listOf(-1 to "Default") + list
    }

    private fun micTypeName(type: Int) = when (type) {
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone mic"
        else -> "Mic $type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        prefs.value = store.prefsBundle
        setContent {
            LivePipeTheme {
                App(
                    initialDestinations = store.destinations,
                    initialActiveId = store.activeId,
                    prefs = prefs.value,
                    onPrefs = ::setPrefs,
                    onPersistDestinations = { list, active -> store.destinations = list; store.activeId = active },
                    onToggleStream = ::toggleStream,
                    onPickBubbleImage = { pickImage.launch("image/*") },
                    micDevices = micDevices(),
                )
            }
        }
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
