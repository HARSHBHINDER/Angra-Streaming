package dev.livepipe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.math.abs

/**
 * Charging modes applied by the phone's charger IC.
 *
 * The charger IC regulates charge current to a ceiling the kernel driver programs; the driver exposes it as
 * power_supply sysfs attributes (constant_charge_current_max, vendor *_charge files, input limits). Writing them
 * needs root, so:
 *  - ROOT: the app writes the ceiling into those attributes (never above the phone's stock value, which it
 *    records once while charging) and re-applies it after every plug-in, because the OEM charge logic reloads
 *    its own values on each connection. Node discovery and unit rules follow ACC (VR-25/acc), the field-tested
 *    charge controller: values < 10000 are mA, larger are µA; 0/1/negative/text values are switches, not limits.
 *  - SAMSUNG: One UI's own Fast / Super fast charging switches (system settings, writable after a one-time adb
 *    grant of WRITE_SECURE_SETTINGS). Off, the phone negotiates the plain 5 V profile with the charger.
 *  - NONE: the OEM keeps charging control in its firmware (e.g. OnePlus/OPLUS VOOC); nothing can reach it.
 *
 * Targets use C-rate (current ÷ capacity): Normal 0.5C is the standard charge in Li-ion cell datasheets, Medium
 * 1C, Fast = the phone's own maximum. The live reading shows whether the IC is holding the limit.
 */
object Charging {
    const val NORMAL = 0
    const val MEDIUM = 1
    const val FAST = 2
    val names = listOf("Normal", "Medium", "Fast")

    enum class Engine { ROOT, SAMSUNG, SAMSUNG_NEEDS_GRANT, NONE }

    const val GRANT_COMMAND = "adb shell pm grant dev.livepipe android.permission.WRITE_SECURE_SETTINGS"

    /** Last result of applying a mode, for the Settings card. */
    val status = MutableStateFlow("")

    /** Charge-current ceiling (mA) for a mode, or null = the phone's stock maximum. Never below 500 mA. */
    fun targetMa(mode: Int, capacityMah: Int): Int? = when (mode) {
        NORMAL -> maxOf(500, capacityMah / 2)
        MEDIUM -> maxOf(500, capacityMah)
        else -> null
    }

    /** Battery capacity from the fuel gauge: charge counter ÷ level. µAh per spec; some firmware reports mAh. */
    fun capacityMah(counter: Int, levelPct: Int): Int {
        if (counter <= 0 || levelPct <= 0) return 4000
        val fromMicro = counter.toLong() * 100 / levelPct / 1000
        val mah = if (fromMicro >= 500) fromMicro else counter.toLong() * 100 / levelPct
        return mah.toInt().coerceIn(1000, 10000)
    }

    /** CURRENT_NOW is µA by spec, but some firmware (OnePlus/OPLUS, some Samsung) reports mA. Returns mA. */
    fun milliamps(raw: Int): Int = if (abs(raw) > 20_000) raw / 1000 else raw

    /** Thermal status LIGHT and up: where phones start trimming charge current on their own. */
    fun warm(thermal: Int) = thermal >= 1

    class Reading(
        val plugged: Boolean, val charging: Boolean, val level: Int, val mA: Int, val watts: Float,
        val tempC: Float, val source: String, val thermal: Int, val capacityMah: Int,
    )

    fun read(ctx: Context): Reading {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val plug = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val scale = (i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = (i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100 / scale
        val mV = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        // Sign conventions differ across OEMs; while charging the magnitude is the charge current.
        val mA = if (charging) abs(milliamps(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))) else 0
        val thermal = if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(PowerManager::class.java).currentThermalStatus else 0
        return Reading(
            plugged = plug != 0, charging = charging, level = level, mA = mA, watts = mA * mV / 1_000_000f,
            tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            source = when (plug) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Charger"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"; else -> "Dock"
            },
            thermal = thermal,
            capacityMah = capacityMah(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER), level),
        )
    }

    // Magisk, KernelSU and APatch all put su on one of these paths.
    private val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/system_ext/bin/su")
    fun hasRoot() = suPaths.any { File(it).exists() }

    fun engine(ctx: Context): Engine = when {
        hasRoot() -> Engine.ROOT
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ->
            if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
                Engine.SAMSUNG else Engine.SAMSUNG_NEEDS_GRANT
        else -> Engine.NONE
    }

    /** User picked a mode: apply it through whatever this phone allows. */
    fun setMode(ctx: Context, mode: Int) {
        when (engine(ctx)) {
            Engine.ROOT -> {
                // Limits are kept (and re-applied on plug-in) by ChargeService; Fast restores stock and stops it.
                if (mode == FAST) Thread { applyRoot(ctx, FAST, 4000); ChargeService.sync(ctx, FAST) }.start()
                else ChargeService.sync(ctx, mode)
            }
            Engine.SAMSUNG -> status.value = applySamsung(ctx, mode)
            else -> Unit
        }
    }

    /**
     * One UI's switches: Fast charging = adaptive (AFC, ~15 W), Super fast = PPS (25/45 W). Normal turns both off
     * (plain 5 V, ~10 W), Medium keeps adaptive only. The phone renegotiates with the charger on the next plug-in.
     */
    private fun applySamsung(ctx: Context, mode: Int): String = runCatching {
        Settings.System.putInt(ctx.contentResolver, "adaptive_fast_charging", if (mode >= MEDIUM) 1 else 0)
        Settings.System.putInt(ctx.contentResolver, "super_fast_charging", if (mode == FAST) 1 else 0)
        "Set. Unplug and plug in again so the phone renegotiates with the charger."
    }.getOrElse { "One UI refused the change (${it.javaClass.simpleName}). Use Settings › Battery › Charging settings." }

    /**
     * Writes the mode's ceiling into the charger's kernel controls. First run (while charging) records every
     * control file with its stock value; later runs write min(target, stock) in each file's own unit, or the stock
     * value back for Fast. When battery-side controls exist, input-side ones (input_current*, current_max) are
     * left alone so the limit isn't doubled up at the input.
     */
    fun applyRoot(ctx: Context, mode: Int, capacityMah: Int): Boolean {
        val target = targetMa(mode, capacityMah)
        val out = RootShell.run(script(File(ctx.filesDir, "charge-defaults").path, target?.toString() ?: "-"))
        val result = out?.lastOrNull { it.startsWith("result ") }?.removePrefix("result ")
        val set = out.orEmpty().filter { it.startsWith("set ") }.size
        status.value = when {
            out == null -> "Root access was denied."
            result == null -> "No answer from the root shell."
            result == "nonodes" -> "This kernel exposes no charge-current control."
            set == 0 -> "The kernel refused every write."
            target == null -> "Stock charging restored on $set control${if (set > 1) "s" else ""}."
            else -> "Charger limited to $target mA on $set control${if (set > 1) "s" else ""}."
        }
        return set > 0
    }

    private fun script(defaults: String, ma: String) = """
chg() {
  D='$defaults'; MA='$ma'
  cd /sys/class/power_supply 2>/dev/null || { echo "result nonodes"; return; }
  if [ ! -s "${'$'}D" ]; then
    for f in */constant_charge_current* */batt_tune_*_charge_current */restrict*_cur* /sys/class/qcom-battery/restrict*_cur* \
             */ac_charge */aca_charge */car_charge */dcp_charge */hv_charge */sdp_charge */wc_charge */so_limit_charge \
             */mhl_2000_charge */current_max */input_current* */ac_input */aca_input */car_input */dcp_input */sdp_input \
             */wc_input */so_limit_input */mhl_2000_input; do
      [ -f "${'$'}f" ] || continue
      case "${'$'}f" in *parallel*|bq[0-9]*/current_max) continue;; esac
      v=${'$'}(cat "${'$'}f" 2>/dev/null) || continue
      case "${'$'}v" in ""|-*|*" "*|[01]|*[!0-9]*) continue;; esac
      echo "${'$'}f ${'$'}v" >> "${'$'}D"
    done
    [ -s "${'$'}D" ] || { echo "result nonodes"; return; }
    chmod 644 "${'$'}D"
  fi
  hb=0
  while read f v; do case "${'$'}{f##*/}" in current_max|*input*) ;; *) hb=1;; esac; done < "${'$'}D"
  while read f v; do
    if [ "${'$'}MA" = "-" ]; then n=${'$'}v
    else
      if [ ${'$'}hb = 1 ]; then case "${'$'}{f##*/}" in current_max|*input*) continue;; esac; fi
      if [ "${'$'}v" -lt 10000 ]; then n=${'$'}MA; else n=${'$'}((MA * 1000)); fi
      [ "${'$'}n" -gt "${'$'}v" ] && n=${'$'}v
    fi
    chmod u+w "${'$'}f" 2>/dev/null
    if echo "${'$'}n" > "${'$'}f" 2>/dev/null; then echo "set ${'$'}f ${'$'}n"; else echo "fail ${'$'}f"; fi
  done < "${'$'}D"
  echo "result done"
}
chg; unset -f chg
"""
}

/** One long-lived su shell, so the root manager asks once and doesn't toast on every re-apply. */
object RootShell {
    private class Sh(val p: Process) {
        val w = p.outputStream.bufferedWriter()
        val r = p.inputStream.bufferedReader()
    }
    private var sh: Sh? = null

    /** Runs a script as root; its stdout lines, or null when root is unavailable or denied. Blocking. */
    @Synchronized fun run(script: String): List<String>? = try {
        val s = sh?.takeIf { it.p.isAlive } ?: Sh(ProcessBuilder("su").redirectErrorStream(true).start()).also { sh = it }
        s.w.write(script); s.w.write("\necho __end__\n"); s.w.flush()
        val out = generateSequence { s.r.readLine() }.takeWhile { it != "__end__" }.toList()
        if (s.p.isAlive) out else { sh = null; null }
    } catch (e: Exception) {
        sh = null; null
    }
}
