package com.sarralle.begia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * The foreground service that hosts the Python acquisition core. The
 * persistent notification plus the wake and WiFi locks keep Android from
 * suspending acquisition while a trial records with the screen off.
 *
 * Boot is one call into runtime/begia_shell/android.py, which chooses the
 * slot, starts uvicorn on a daemon thread and returns when /api/state
 * answers. If it raises, the slot manager has been left saying "booting",
 * and this process exits so that the next start rolls back - Android
 * restarts a sticky foreground service on its own.
 */
class RecorderService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        show(getString(R.string.notif_starting))
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "begia:acquisition").also { it.acquire() }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "begia:wifi").also { it.acquire() }
        if (!booted) {
            booted = true
            thread(name = "begia-boot") { boot() }
        }
    }

    private fun boot() {
        try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            val embedded = Embedded.ensure(this)
            val debug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val restart = Runnable {
                Log.i(Recorder.TAG, "restart asked for by the dev server")
                exitProcess(0)
            }
            val report = Python.getInstance().getModule("begia_shell.android")
                .callAttr("start", filesDir.path, embedded?.path, Recorder.PORT, restart, debug)
                .toString()
            Log.i(Recorder.TAG, "healthy: $report")
            val version = JSONObject(report).optString("version", "")
            show(getString(R.string.notif_running) + (if (version.isEmpty()) "" else " · $version"))
        } catch (e: Exception) {
            Log.e(Recorder.TAG, "boot failed", e)
            show(getString(R.string.notif_failed))
            if (Recorder.canRollBack(this)) {
                Log.w(Recorder.TAG, "exiting so the next start rolls back")
                Thread.sleep(1500)
                exitProcess(1)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun show(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val CHANNEL = "recorder"
        private const val NOTIFICATION_ID = 1
        @Volatile private var booted = false
    }
}
