package com.morad.housekeepermini.model

import android.text.format.Formatter
import android.content.Context
import java.util.concurrent.TimeUnit

enum class NavTab(val label: String) {
    OVERVIEW("Home"),
    STORAGE("Storage"),
    APPS("Apps"),
    PRIVACY("Privacy"),
    NETWORK("Network")
}

data class DeviceVitals(
    val deviceName: String = "",
    val androidVersion: String = "",
    val isQuestOrVr: Boolean = false,
    val totalRamBytes: Long = 0L,
    val availRamBytes: Long = 0L,
    val lowMemory: Boolean = false,
    val totalStorageBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val batteryTempC: Float = 0f,
    val thermalLabel: String = "Normal",
    val cpuAbi: String = ""
) {
    val usedRamBytes: Long get() = (totalRamBytes - availRamBytes).coerceAtLeast(0L)
    val usedStorageBytes: Long get() = (totalStorageBytes - freeStorageBytes).coerceAtLeast(0L)
    val ramProgress: Float get() = if (totalRamBytes > 0) usedRamBytes.toFloat() / totalRamBytes else 0f
    val storageProgress: Float get() = if (totalStorageBytes > 0) usedStorageBytes.toFloat() / totalStorageBytes else 0f
}

enum class StorageCategory(val title: String, val subtitle: String) {
    APK("Leftover Installers", "Downloaded .apk files you probably already installed"),
    LARGE("Large Files", "Files over 25 MB taking up space"),
    DUPLICATE("Duplicates", "Exact copies of the same file"),
    OLD_DOWNLOAD("Old Downloads", "Files in Downloads untouched for 30+ days")
}

data class StorageItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val category: StorageCategory,
    val detail: String = ""
)

data class AppEntry(
    val packageName: String,
    val label: String,
    val versionName: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val lastUsedMs: Long,
    val apkPath: String,
    val sensitivePermissions: List<String>
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

data class PrivacyFinding(
    val packageName: String,
    val appLabel: String,
    val permissions: List<String>,
    val summary: String
)

data class RouteProbeResult(
    val name: String,
    val target: String,
    val reachable: Boolean,
    val latencyMs: Long,
    val detail: String
)

data class SpeedTestResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    val serverSummary: String = "Cloudflare Edge",
    val error: String? = null
) {
    val verdictTitle: String
        get() = when {
            error != null -> "Speed test incomplete"
            downloadMbps >= 100.0 -> "Very fast connection"
            downloadMbps >= 25.0 -> "Good everyday connection"
            downloadMbps >= 10.0 -> "Moderate connection"
            else -> "Slow connection"
        }

    val verdictDetail: String
        get() = when {
            error != null -> error
            downloadMbps >= 100.0 -> "Ready for 4K streaming, Meta Quest VR streaming, and large downloads."
            downloadMbps >= 25.0 -> "Smooth for HD video, calls, and everyday browsing."
            downloadMbps >= 10.0 -> "Fine for browsing and standard video; large downloads will take longer."
            else -> "Video calls or large downloads may stutter. Checking your Wi-Fi or VPN route may help."
        }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Available(
        val version: String,
        val notes: String,
        val apkUrl: String
    ) : UpdateState
    data class Downloading(val progress: Int) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class TrackedRepoApp(
    val repo: String,
    val displayName: String,
    val packageName: String = "",
    val installedVersion: String = "",
    val latestVersion: String = "",
    val apkUrl: String = "",
    val hasUpdate: Boolean = false,
    val isBuiltIn: Boolean = false,
    val statusText: String = "Not checked yet"
)

fun formatBytes(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0L))

fun formatLastUsed(ms: Long): String {
    if (ms <= 0L) return "Not tracked yet"
    val diff = System.currentTimeMillis() - ms
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        days <= 0L -> "Used today"
        days == 1L -> "Used yesterday"
        days < 30L -> "Used $days days ago"
        else -> "Unused for $days days"
    }
}
