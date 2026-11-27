package com.example.batteryalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlarmHelper {
    private const val CHANNEL_ID = "battery_alarm_channel"
    private const val NOTIFICATION_ID = 2001
    private const val WAKE_LOCK_TIMEOUT_MS = 5 * 60 * 1000L // max 5 minuti

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _ringing = MutableStateFlow(false)

    /** true mentre l'allarme è attivo; osservato dalla UI. */
    val ringing: StateFlow<Boolean> = _ringing.asStateFlow()

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_desc)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    @Synchronized
    fun fireAlarm(context: Context) {
        val appContext = context.applicationContext
        acquireWakeLock(appContext)
        startAlarmSound(appContext)
        showNotification(appContext)
        _ringing.value = true
    }

    /** Ferma suono, rilascia il wake lock e rimuove la notifica. Sicura da chiamare più volte. */
    @Synchronized
    fun cancelAlarm(context: Context) {
        stopAlarmSound()
        releaseWakeLock()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        _ringing.value = false
    }

    private fun acquireWakeLock(context: Context) {
        releaseWakeLock()
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatteryAlarm::AlarmWakeLock")
            .apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startAlarmSound(context: Context) {
        stopAlarmSound()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (mediaPlayer === mp) mediaPlayer = null
                true
            }
            player.setDataSource(context, uri)
            player.isLooping = true
            player.prepare()
            player.start()
            mediaPlayer = player
        } catch (_: Exception) {
            // Nessun suono disponibile: rilascia subito il player, resta la notifica
            player.release()
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        mediaPlayer = null
    }

    private fun showNotification(context: Context) {
        // Tocco sulla notifica: apre l'app e ferma l'allarme
        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_STOP_ALARM, true)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Pulsante "Ferma allarme": ferma il suono senza aprire l'app
        val stopIntent = Intent(context, BatteryMonitorService::class.java)
            .setAction(BatteryMonitorService.ACTION_STOP_ALARM)
        val stopPending = PendingIntent.getService(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.alarm_title))
            .setContentText(context.getString(R.string.alarm_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.stop_alarm),
                stopPending
            )
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
