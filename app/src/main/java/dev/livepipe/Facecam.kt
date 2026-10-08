package dev.livepipe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface

/**
 * Minimal front-camera preview piped into a GL [SurfaceTexture] — the facecam PiP source for
 * RootEncoder's SurfaceFilterRender. One fixed preview size; the filter handles scale/position.
 * ponytail: preview-only Camera2, no capture/zoom/exposure controls — add if the facecam needs them.
 */
class Facecam(
    private val context: Context,
    surfaceTexture: SurfaceTexture,
    width: Int = 640,
    height: Int = 480,
) {
    private val thread = HandlerThread("facecam").apply { start() }
    private val handler = Handler(thread.looper)
    private val surface: Surface
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    init {
        surfaceTexture.setDefaultBufferSize(width, height)
        surface = Surface(surfaceTexture)
    }

    @SuppressLint("MissingPermission") // caller gates on CAMERA permission before start()
    fun start() {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val frontId = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        } ?: cm.cameraIdList.firstOrNull() ?: return
        runCatching {
            cm.openCamera(frontId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    openSession(camera)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, handler)
        }
    }

    @Suppress("DEPRECATION") // createCaptureSession(List,...) is fine for a single fixed surface
    private fun openSession(camera: CameraDevice) {
        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                runCatching { s.setRepeatingRequest(req.build(), null, handler) }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) {}
        }, handler)
    }

    fun stop() {
        runCatching { session?.close() }
        runCatching { device?.close() }
        session = null; device = null
        surface.release()
        thread.quitSafely()
    }
}
