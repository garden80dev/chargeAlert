package com.example.batteryalarm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.batteryalarm.ui.BatteryAlarmTheme
import com.example.batteryalarm.ui.BatteryScreen

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STOP_ALARM = "STOP_ALARM"
    }

    private var battery by mutableStateOf(BatteryState.Unknown)
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            BatteryState.fromIntent(intent)?.let { battery = it }
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.notifications_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStopAlarm(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val monitoring by BatteryMonitorService.running.collectAsState()
            val ringing by AlarmHelper.ringing.collectAsState()
            BatteryAlarmTheme {
                BatteryScreen(
                    battery = battery,
                    monitoring = monitoring,
                    ringing = ringing,
                    threshold = BatteryMonitorService.BATTERY_THRESHOLD,
                    onToggleMonitoring = {
                        if (BatteryMonitorService.isRunning) stopMonitoring() else startMonitoring()
                    },
                    onStopAlarm = { AlarmHelper.cancelAlarm(this) },
                    onClose = ::closeApp,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            val sticky = ContextCompat.registerReceiver(
                this, batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            sticky?.let(BatteryState::fromIntent)?.let { battery = it }
        }
    }

    override fun onPause() {
        unregisterBatteryReceiver()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStopAlarm(intent)
    }

    /**
     * Chiude l'app liberando tutte le risorse: ferma il servizio in primo piano
     * (che a sua volta rilascia receiver, MediaPlayer, wake lock e notifiche),
     * poi chiude l'activity e la rimuove dalle app recenti.
     */
    private fun closeApp() {
        unregisterBatteryReceiver()
        stopMonitoring()
        AlarmHelper.cancelAlarm(this)
        finishAndRemoveTask()
    }

    private fun handleStopAlarm(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_STOP_ALARM, false) == true) {
            AlarmHelper.cancelAlarm(this)
            intent.removeExtra(EXTRA_STOP_ALARM)
        }
    }

    private fun startMonitoring() {
        ContextCompat.startForegroundService(
            this, Intent(this, BatteryMonitorService::class.java)
        )
    }

    private fun stopMonitoring() {
        stopService(Intent(this, BatteryMonitorService::class.java))
    }

    private fun unregisterBatteryReceiver() {
        if (receiverRegistered) {
            try { unregisterReceiver(batteryReceiver) } catch (_: IllegalArgumentException) {}
            receiverRegistered = false
        }
    }
}
