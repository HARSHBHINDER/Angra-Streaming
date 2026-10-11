package dev.livepipe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Rooted phones only: keeps the chosen charge-current limit in the charger. OEM charge logic reloads its own
 * values on every plug-in and on some JEITA/thermal steps, so the limit is written on plug-in and re-checked
 * every 30 s while plugged (the same approach as ACC's apply-on-plug). Off the charger it does nothing.
 */
class ChargeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var plugged = false

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val now = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val connected = now && !plugged
            plugged = now
            if (connected) enforce()
        }
    }
    private val tick = object : Runnable {
        override fun run() { enforce(); main.postDelayed(this, 30_000) }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Charging control", NotificationManager.IMPORTANCE_MIN))
        val mode = Store(this).prefsBundle.charging
        val n = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("Charging: ${Charging.names[mode]}")
            .setContentText(if (mode == Charging.NORMAL) "0.5C, the standard Li-ion rate" else "1C charge current")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, n)
        registerReceiver(power, IntentFilter(Intent.ACTION_BATTERY_CHANGED))   // sticky: fires now with the current state
        main.postDelayed(tick, 30_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { enforce(); return START_STICKY }

    private fun enforce() {
        if (!plugged) return
        io.execute {
            val mode = Store(this).prefsBundle.charging
            if (mode != Charging.FAST) Charging.applyRoot(this, mode, Charging.read(this).capacityMah)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(power) }
        main.removeCallbacksAndMessages(null)
        io.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Limits reset on reboot; bring the service back if a limit was chosen. */
    class Boot : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) sync(ctx, Store(ctx).prefsBundle.charging)
        }
    }

    companion object {
        private const val ID = 7
        private const val CHANNEL = "charging"

        /** Runs the service while a limit is chosen on a rooted phone; stops it otherwise. */
        fun sync(ctx: Context, mode: Int) {
            val i = Intent(ctx, ChargeService::class.java)
            if (mode != Charging.FAST && Charging.engine(ctx) == Charging.Engine.ROOT) ctx.startForegroundService(i)
            else ctx.stopService(i)
        }
    }
}
