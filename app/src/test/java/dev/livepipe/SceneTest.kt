package dev.livepipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTest {
    private val phone = 20f / 9f      // typical phone screen in landscape
    private val yt = 16f / 9f         // YouTube 1080p/720p canvas

    @Test fun fillCoversTheCanvasWithNoBorders() {
        val r = Scene.screenView(phone, yt, fit = false, zoom = 1f, panX = 0f, panY = 0f).contentRect()
        assertTrue("left edge", r[0] <= 0f); assertTrue("top edge", r[1] <= 1e-6f)
        assertTrue("right edge", r[0] + r[2] >= 1f); assertTrue("bottom edge", r[1] + r[3] >= 1f - 1e-6f)
    }

    @Test fun fillPannedToTheLimitStillHasNoBorders() {
        for (p in listOf(-1f, 1f)) {
            val r = Scene.screenView(phone, yt, false, 2f, p, p).contentRect()
            assertTrue(r[0] <= 1e-6f && r[1] <= 1e-6f && r[0] + r[2] >= 1f - 1e-6f && r[1] + r[3] >= 1f - 1e-6f)
        }
    }

    @Test fun fitShowsTheWholeScreenLetterboxed() {
        val v = Scene.screenView(phone, yt, fit = true, zoom = 1f, panX = 0f, panY = 0f)
        assertEquals(1f, v.z); assertTrue(v.isIdentity)
        assertEquals(0.8f, v.contentRect()[3], 1e-4f)          // 16:9 / 20:9 of the height is picture
    }

    @Test fun cropMatchesContentRect() {
        val v = Scene.screenView(phone, yt, false, 1.5f, 0.3f, -0.7f)
        val c = v.cropPercent()
        assertEquals(c[2], c[3])                                 // same % both axes keeps the aspect
        assertEquals(v.cx * 100, c[0] + c[2] / 2, 1e-3f)
        assertEquals(v.cy * 100, c[1] + c[3] / 2, 1e-3f)
    }

    @Test fun panRoundTrips() {
        val v = Scene.screenView(phone, yt, false, 2f, 0.4f, -0.25f)
        assertEquals(0.4f, Scene.panFrom(v.cx, v.rx), 1e-5f)
        assertEquals(-0.25f, Scene.panFrom(v.cy, v.ry), 1e-5f)
    }
}
