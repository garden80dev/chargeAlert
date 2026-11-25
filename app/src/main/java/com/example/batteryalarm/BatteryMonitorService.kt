package com.example.batteryalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class BatteryMonitorService : Service() {

    companion object {
        const val ACTION_STOP_ALARM = "com.example.batteryalarm.action.STOP_ALARM"
        private const val FG_CHANNEL_ID = "battery_monitor_fg"
        private const val FG_NOTIFICATION_ID = 1001
        private const val BATTERY_THRESHOLD = 80

        /** Stato letto dalla UI per mostrare il pulsante corretto. */
        @Volatile
        var isRunning = false
            private set
    }

    private var alarmFired = false
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleBatteryIntent(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createForegroundChannel()
        AlarmHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            // Ferma solo l'allarme; il monitoraggio continua.
            // Se il servizio non era attivo, chiudilo subito.
            AlarmHelper.cancelAlarm(this)
            if (!isRunning) stopSelf(startId)
            return if (isRunning) START_STICKY else START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this, FG_NOTIFICATION_ID, buildForegroundNotification(0, false), type
        )
        isRunning = true

        if (!receiverRegistered) {
            // ACTION_BATTERY_CHANGED è sticky: il valore di ritorno contiene già lo stato attuale
            val sticky = ContextCompat.registerReceiver(
                this, batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            sticky?.let { handleBatteryIntent(it) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(batteryReceiver) } catch (_: IllegalArgumentException) {}
            receiverRegistered = false
        }
        AlarmHelper.cancelAlarm(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) return
        val percent = (level * 100) / scale
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging && percent >= BATTERY_THRESHOLD && !alarmFired) {
            alarmFired = true
            AlarmHelper.fireAlarm(this)
        }
        // Reset quando si scollega il caricatore
        if (!isCharging && alarmFired) {
            alarmFired = false
            AlarmHelper.cancelAlarm(this)
        }
        updateForegroundNotification(percent, isCharging)
    }

    private fun createForegroundChannel() {
        val channel = NotificationChannel(
            FG_CHANNEL_ID,
            getString(R.string.fg_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.fg_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(percent: Int, charging: Boolean): Notification {
        val statusText = getString(if (charging) R.string.charging else R.string.not_charging)
        val openPending = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.fg_title))
            .setContentText(getString(R.string.fg_text, statusText, percent))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateForegroundNotification(percent: Int, charging: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(FG_NOTIFICATION_ID, buildForegroundNotification(percent, charging))
    }
}
