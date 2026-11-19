package com.morad.housekeepermini.engine

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import com.morad.housekeepermini.model.AppEntry
import com.morad.housekeepermini.model.PrivacyFinding
import java.io.File
import java.util.concurrent.TimeUnit

object AppsAndPrivacyEngine {

    private val watchedPermissions = mapOf(
        Manifest.permission.CAMERA to "Camera",
        Manifest.permission.RECORD_AUDIO to "Microphone",
        Manifest.permission.ACCESS_FINE_LOCATION to "Precise Location",
        Manifest.permission.READ_CONTACTS to "Contacts",
        Manifest.permission.REQUEST_INSTALL_PACKAGES to "Install Apps",
        Manifest.permission.SYSTEM_ALERT_WINDOW to "Draw Over Apps"
    )

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Fallback on Quest / TV
            }
        }
        return false
    }

    fun scanApps(context: Context): Pair<List<AppEntry>, List<PrivacyFinding>> {
        val pm = context.packageManager
        val packages = getInstalledPackagesCompat(pm)
        val usageMap = loadLastUsedMap(context)
        val storageStats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val canReadStats = hasUsageAccess(context)

        val appEntries = mutableListOf<AppEntry>()
        val privacyFindings = mutableListOf<PrivacyFinding>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            // Focus on user-installed apps (plus updated system apps)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem) continue
            if (pkg.packageName == context.packageName) continue

            val label = pm.getApplicationLabel(appInfo).toString()
            val version = pkg.versionName ?: "—"
            val apkPath = appInfo.sourceDir ?: ""

            var appBytes = if (apkPath.isNotEmpty()) File(apkPath).length() else 0L
            var dataBytes = 0L
            var cacheBytes = 0L

            if (canReadStats && storageStats != null) {
                try {
                    val stats = storageStats.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        pkg.packageName,
                        Process.myUserHandle()
                    )
                    appBytes = stats.appBytes
                    dataBytes = stats.dataBytes
                    cacheBytes = stats.cacheBytes
                } catch (_: Exception) {
                    // Keep APK size fallback
                }
            }

            val grantedSensitive = mutableListOf<String>()
            val requested = pkg.requestedPermissions
            val flags = pkg.requestedPermissionsFlags
            if (requested != null && flags != null) {
                for (i in requested.indices) {
                    val perm = requested[i]
                    val friendly = watchedPermissions[perm] ?: continue
                    val granted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (granted) {
                        grantedSensitive.add(friendly)
                    }
                }
            }

            appEntries.add(
                AppEntry(
                    packageName = pkg.packageName,
                    label = label,
                    versionName = version,
                    appBytes = appBytes,
                    dataBytes = dataBytes,
                    cacheBytes = cacheBytes,
                    lastUsedMs = usageMap[pkg.packageName] ?: 0L,
                    apkPath = apkPath,
                    sensitivePermissions = grantedSensitive
                )
            )

            if (grantedSensitive.isNotEmpty()) {
                privacyFindings.add(
                    PrivacyFinding(
                        packageName = pkg.packageName,
                        appLabel = label,
                        permissions = grantedSensitive,
                        summary = "Can access ${grantedSensitive.joinToString(", ")}"
                    )
                )
            }
        }

        return appEntries.sortedByDescending { it.totalBytes } to
            privacyFindings.sortedByDescending { it.permissions.size }
    }

    fun openAppDetails(context: Context, packageName: String): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun requestUninstall(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            openAppDetails(context, packageName)
        }
    }

    fun backupApk(entry: AppEntry): String? {
        if (entry.apkPath.isEmpty()) return null
        val src = File(entry.apkPath)
        if (!src.exists() || !src.canRead()) return null
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: return null
        val outDir = File(downloads, "HousekeeperMini-Backups")
        if (!outDir.exists()) outDir.mkdirs()
        val safeName = entry.label.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(outDir, "${safeName}-${entry.versionName}.apk")
        return try {
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackagesCompat(pm: PackageManager): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
    }

    private fun loadLastUsedMap(context: Context): Map<String, Long> {
        if (!hasUsageAccess(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(365)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end) ?: return emptyMap()
        val result = HashMap<String, Long>()
        for (item in stats) {
            val prev = result[item.packageName] ?: 0L
            if (item.lastTimeUsed > prev) {
                result[item.packageName] = item.lastTimeUsed
            }
        }
        return result
    }
}
