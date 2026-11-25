package com.example.batteryalarm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STOP_ALARM = "STOP_ALARM"
    }

    private lateinit var tvLevel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnToggle: Button
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateBatteryUi(intent)
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
        setContentView(R.layout.activity_main)

        tvLevel = findViewById(R.id.tvBatteryLevel)
        tvStatus = findViewById(R.id.tvChargingStatus)
        btnToggle = findViewById(R.id.btnToggle)

        handleStopAlarm(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        btnToggle.setOnClickListener {
            // Lo stato del servizio cambia in modo asincrono: aggiorna in base all'azione
            val starting = !BatteryMonitorService.isRunning
            if (starting) startMonitoring() else stopMonitoring()
            updateToggleText(starting)
        }

        findViewById<Button>(R.id.btnClose).setOnClickListener { closeApp() }
    }

    override fun onResume() {
        super.onResume()
        updateToggleText(BatteryMonitorService.isRunning)
        if (!receiverRegistered) {
            val sticky = ContextCompat.registerReceiver(
                this, batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            sticky?.let { updateBatteryUi(it) }
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

    private fun updateToggleText(running: Boolean) {
        btnToggle.setText(if (running) R.string.stop_monitoring else R.string.start_monitoring)
    }

    private fun updateBatteryUi(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) return
        val percent = (level * 100) / scale
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        tvLevel.text = getString(R.string.battery_level, percent)
        tvStatus.setText(if (isCharging) R.string.charging else R.string.not_charging)
    }
}
