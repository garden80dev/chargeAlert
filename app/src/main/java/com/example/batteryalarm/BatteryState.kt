package com.example.batteryalarm

import android.content.Intent
import android.os.BatteryManager

/** Livello (0-100, oppure -1 se sconosciuto) e stato di ricarica. */
data class BatteryState(val percent: Int, val charging: Boolean) {
    val known: Boolean get() = percent >= 0

    companion object {
        val Unknown = BatteryState(percent = -1, charging = false)

        /** Legge un intent ACTION_BATTERY_CHANGED; null se i dati non sono validi. */
        fun fromIntent(intent: Intent): BatteryState? {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryState((level * 100) / scale, charging)
        }
    }
}
