package com.morad.housekeepermini.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import com.morad.housekeepermini.model.DeviceVitals

object DeviceVitalsEngine {

    fun read(context: Context): DeviceVitals {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.totalBytes
        val freeStorage = stat.availableBytes

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        val pm = context.packageManager
        val isQuest = Build.MANUFACTURER.equals("Oculus", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Meta", ignoreCase = true) ||
            Build.MODEL.contains("Quest", ignoreCase = true) ||
            pm.hasSystemFeature("oculus.hardware.vr.headtracking") ||
            pm.hasSystemFeature(PackageManager.FEATURE_VR_HEADTRACKING)

        val thermalLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (power.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Cool"
                PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Heat"
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Hot — let it rest"
                else -> "Normal"
            }
        } else {
            if (tempTenths > 420) "Warm" else "Normal"
        }

        val prettyName = buildString {
            val mfg = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            if (!Build.MODEL.startsWith(mfg, ignoreCase = true)) {
                append(mfg).append(' ')
            }
            append(Build.MODEL)
        }

        return DeviceVitals(
            deviceName = prettyName,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            isQuestOrVr = isQuest,
            totalRamBytes = memInfo.totalMem,
            availRamBytes = memInfo.availMem,
            lowMemory = memInfo.lowMemory,
            totalStorageBytes = totalStorage,
            freeStorageBytes = freeStorage,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempC = tempTenths / 10f,
            thermalLabel = thermalLabel,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"
        )
    }
}
