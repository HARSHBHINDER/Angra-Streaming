package dev.livepipe

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlin.math.abs

/**
 * Charging modes that actually change how fast the battery fills while streaming.
 *
 * What an app can't do: set the charger IC's current. That lives in the kernel (power_supply sysfs, e.g.
 * constant_charge_current_max) behind root + SELinux, and OEM services (OnePlus/OPLUS VOOC, Samsung, Pixel) own
 * it; Android and iOS expose no API. Charge limits (80%, optimized charging) are system settings.
 *
 * What it can: the charger feeds the phone first and the battery gets what's left, and the charger IC cuts
 * current when the phone runs warm (JEITA bands + OEM thermal rules). A live stream is the phone's biggest load
 * and heat source (encoder + radio scale with bitrate). So on the charger, Medium and Fast ease the stream's
 * bitrate: less draw, less heat, more current into the battery. [read] shows the result in watts.
 */
object Charging {
    const val NORMAL = 0
    const val MEDIUM = 1
    const val FAST = 2
    val names = listOf("Normal", "Medium", "Fast")

    /** Share of the channel's bitrate the stream may use. Normal never eases; off the charger nothing eases. */
    fun bitrateFactor(mode: Int, plugged: Boolean, warm: Boolean): Float = when {
        !plugged || mode == NORMAL -> 1f
        mode == MEDIUM -> if (warm) 0.6f else 1f
        else -> if (warm) 0.35f else 0.6f
    }

    /** CURRENT_NOW is µA by spec, but some firmware (OnePlus/OPLUS, some Samsung) reports mA. Returns mA. */
    fun milliamps(raw: Int): Int = if (abs(raw) > 20_000) raw / 1000 else raw

    /** Thermal status LIGHT and up: the point where phones start trimming charge current. */
    fun warm(thermal: Int) = thermal >= 1

    class Reading(
        val plugged: Boolean, val charging: Boolean, val level: Int, val mA: Int, val watts: Float,
        val tempC: Float, val source: String, val thermal: Int,
    )

    fun read(ctx: Context): Reading {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plug = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val scale = (i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = (i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100 / scale
        val mV = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        // Sign conventions differ across OEMs; while charging the magnitude is the charge current.
        val mA = if (charging) abs(milliamps(ctx.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))) else 0
        val thermal = if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(PowerManager::class.java).currentThermalStatus else 0
        return Reading(
            plugged = plug != 0, charging = charging, level = level, mA = mA, watts = mA * mV / 1_000_000f,
            tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            source = when (plug) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Charger"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"; else -> "Dock"
            },
            thermal = thermal,
        )
    }
}
