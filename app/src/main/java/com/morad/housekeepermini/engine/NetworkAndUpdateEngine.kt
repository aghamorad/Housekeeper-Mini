package com.morad.housekeepermini.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.morad.housekeepermini.BuildConfig
import com.morad.housekeepermini.model.RouteProbeResult
import com.morad.housekeepermini.model.SpeedTestResult
import com.morad.housekeepermini.model.TrackedRepoApp
import com.morad.housekeepermini.model.UpdateState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs

data class NetworkSummary(
    val connected: Boolean,
    val transportLabel: String,
    val localIp: String,
    val dnsServers: String
)

object NetworkAndUpdateEngine {

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/aghamorad/Housekeeper-Mini/releases/latest"

    fun readNetworkSummary(context: Context): NetworkSummary {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val links = active?.let { cm.getLinkProperties(it) }

        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }

        val ip = links?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress ?: "Unavailable"

        val dns = links?.dnsServers
            ?.mapNotNull { it.hostAddress }
            ?.take(2)
            ?.joinToString(", ")
            ?.ifEmpty { "Auto" } ?: "Auto"

        return NetworkSummary(
            connected = caps != null,
            transportLabel = transport,
            localIp = ip,
            dnsServers = dns
        )
    }

    fun runRouteSweep(customLanHost: String): List<RouteProbeResult> {
        val targets = mutableListOf(
            "Cloudflare DNS" to "https://1.1.1.1/cdn-cgi/trace",
            "Google" to "https://www.google.com/generate_204",
            "GitHub API" to "https://api.github.com"
        )

        val results = mutableListOf<RouteProbeResult>()
        for ((name, url) in targets) {
            results.add(probeHttp(name, url))
        }

        val trimmed = customLanHost.trim()
        if (trimmed.isNotEmpty()) {
            results.add(probeLanEndpoint("Custom LAN Host", trimmed))
        }

        return results
    }

    fun runSpeedTest(
        onProgress: (phase: String, dlMbps: Double?, ulMbps: Double?, pingMs: Double?, jitterMs: Double?) -> Unit
    ): SpeedTestResult {
        var finalPing = 0.0
        var finalJitter = 0.0
        var finalDownload = 0.0
        var finalUpload = 0.0
        return try {
            // 1. Ping & jitter (5 lightweight requests)
            onProgress("Measuring ping and jitter…", null, null, null, null)
            val rtts = mutableListOf<Double>()
            for (i in 0 until 5) {
                val t0 = System.nanoTime()
                val conn = (URL("https://speed.cloudflare.com/__down?bytes=0").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                    requestMethod = "GET"
                }
                val code = conn.responseCode
                conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                if (code in 200..399) {
                    rtts.add(ms)
                    finalPing = median(rtts)
                    finalJitter = jitter(rtts)
                    onProgress("Measuring ping and jitter…", null, null, finalPing, finalJitter)
                }
            }
            if (rtts.isEmpty()) {
                return SpeedTestResult(0.0, 0.0, 0.0, 0.0, error = "Could not reach speed test server. Check your internet or VPN.")
            }

            // 2. Streaming download (15 MB cap or 8 seconds max)
            onProgress("Testing download speed…", 0.0, null, finalPing, finalJitter)
            val downConn = (URL("https://speed.cloudflare.com/__down?bytes=15728640").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                useCaches = false
            }
            val downStart = System.nanoTime()
            var lastUiNs = downStart
            var totalDownBytes = 0L
            downConn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                var read = input.read(buf)
                while (read != -1) {
                    totalDownBytes += read
                    val now = System.nanoTime()
                    val elapsedSec = (now - downStart) / 1_000_000_000.0
                    if ((now - lastUiNs) >= 180_000_000L && elapsedSec > 0.05) {
                        lastUiNs = now
                        finalDownload = (totalDownBytes * 8.0) / (elapsedSec * 1_000_000.0)
                        onProgress("Testing download speed…", finalDownload, null, finalPing, finalJitter)
                    }
                    if (elapsedSec >= 8.0) break
                    read = input.read(buf)
                }
            }
            downConn.disconnect()
            val downElapsed = ((System.nanoTime() - downStart) / 1_000_000_000.0).coerceAtLeast(0.05)
            finalDownload = (totalDownBytes * 8.0) / (downElapsed * 1_000_000.0)
            onProgress("Testing upload speed…", finalDownload, 0.0, finalPing, finalJitter)

            // 3. Timed upload (3 MB payload)
            val uploadBytes = 3 * 1024 * 1024
            val upConn = (URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
                requestMethod = "POST"
                setFixedLengthStreamingMode(uploadBytes)
                setRequestProperty("Content-Type", "application/octet-stream")
            }
            val upStart = System.nanoTime()
            var lastUpUiNs = upStart
            upConn.outputStream.use { out ->
                val chunk = ByteArray(32 * 1024) { 0x5A }
                var sent = 0
                while (sent < uploadBytes) {
                    val toWrite = minOf(chunk.size, uploadBytes - sent)
                    out.write(chunk, 0, toWrite)
                    sent += toWrite
                    val now = System.nanoTime()
                    val elapsedSec = (now - upStart) / 1_000_000_000.0
                    if ((now - lastUpUiNs) >= 180_000_000L && elapsedSec > 0.05) {
                        lastUpUiNs = now
                        finalUpload = (sent * 8.0) / (elapsedSec * 1_000_000.0)
                        onProgress("Testing upload speed…", finalDownload, finalUpload, finalPing, finalJitter)
                    }
                }
                out.flush()
            }
            val upCode = upConn.responseCode
            upConn.disconnect()
            val upElapsed = ((System.nanoTime() - upStart) / 1_000_000_000.0).coerceAtLeast(0.05)
            if (upCode in 200..399) {
                finalUpload = (uploadBytes * 8.0) / (upElapsed * 1_000_000.0)
            }

            SpeedTestResult(
                downloadMbps = finalDownload,
                uploadMbps = finalUpload,
                pingMs = finalPing,
                jitterMs = finalJitter
            )
        } catch (e: Exception) {
            SpeedTestResult(
                downloadMbps = finalDownload,
                uploadMbps = finalUpload,
                pingMs = finalPing,
                jitterMs = finalJitter,
                error = e.localizedMessage ?: "Speed test timed out or connection dropped."
            )
        }
    }

    internal fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    internal fun jitter(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until values.size) {
            sum += abs(values[i] - values[i - 1])
        }
        return sum / (values.size - 1)
    }

    private fun probeHttp(name: String, targetUrl: String): RouteProbeResult {
        val start = System.nanoTime()
        return try {
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "HEAD"
            }
            val code = conn.responseCode
            val ms = (System.nanoTime() - start) / 1_000_000L
            conn.disconnect()
            RouteProbeResult(
                name = name,
                target = targetUrl,
                reachable = code in 200..399,
                latencyMs = ms,
                detail = "HTTP $code • ${ms} ms"
            )
        } catch (e: Exception) {
            RouteProbeResult(
                name = name,
                target = targetUrl,
                reachable = false,
                latencyMs = -1,
                detail = e.javaClass.simpleName
            )
        }
    }

    private fun probeLanEndpoint(name: String, hostPort: String): RouteProbeResult {
        val parts = hostPort.removePrefix("http://").removePrefix("https://").split(":")
        val host = parts.firstOrNull()?.trim().orEmpty()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
        if (host.isEmpty()) {
            return RouteProbeResult(name, hostPort, false, -1, "Invalid host")
        }
        val start = System.nanoTime()
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), 2500)
            }
            val ms = (System.nanoTime() - start) / 1_000_000L
            RouteProbeResult(name, "$host:$port", true, ms, "Open • ${ms} ms")
        } catch (e: Exception) {
            RouteProbeResult(name, "$host:$port", false, -1, "Unreachable")
        }
    }

    fun checkForUpdate(): UpdateState {
        return try {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = conn.responseCode
            if (code == 404) {
                conn.disconnect()
                return UpdateState.UpToDate(BuildConfig.VERSION_NAME)
            }
            if (code !in 200..299) {
                conn.disconnect()
                return UpdateState.Error("GitHub returned HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v").trim()
            val notes = json.optString("body").ifBlank { "New update available on GitHub." }
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val url = asset.optString("browser_download_url")
                    if (url.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = url
                        break
                    }
                }
            }

            if (tag.isNotEmpty() && isNewerVersion(tag, BuildConfig.VERSION_NAME) && apkUrl.isNotEmpty()) {
                UpdateState.Available(version = tag, notes = notes, apkUrl = apkUrl)
            } else {
                UpdateState.UpToDate(BuildConfig.VERSION_NAME)
            }
        } catch (e: Exception) {
            UpdateState.Error(e.localizedMessage ?: "Could not check GitHub")
        }
    }

    fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): String? {
        return try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "HousekeeperMini-update.apk")

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 20000
                instanceFollowRedirects = true
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var read = input.read(buf)
                    while (read != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                        }
                        read = input.read(buf)
                    }
                }
            }
            conn.disconnect()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            if (!context.packageManager.canRequestPackageInstalls()) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(permIntent)
                } catch (_: ActivityNotFoundException) {
                    // Fallback on Quest / stripped Android
                }
                return "Allow 'Install unknown apps' for Housekeeper Mini, then tap Install again."
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(installIntent)
                null
            } catch (_: ActivityNotFoundException) {
                "No package installer found on this device."
            }
        } catch (e: Exception) {
            e.localizedMessage ?: "Download failed"
        }
    }

    private const val PREFS_NAME = "housekeeper_mini_updater"
    private const val KEY_TRACKED_APPS = "tracked_apps_json"
    private const val BUILT_IN_REPO = "aghamorad/Housekeeper-Mini"

    fun parseGitHubRepo(input: String): String? {
        val cleaned = input.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .trim('/')
        val parts = cleaned.split("/")
        if (parts.size < 2) return null
        val owner = parts[0].trim()
        val repo = parts[1].trim()
        if (owner.isEmpty() || repo.isEmpty()) return null
        return "$owner/$repo"
    }

    fun loadTrackedApps(context: Context): List<TrackedRepoApp> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TRACKED_APPS, null)
        val list = mutableListOf<TrackedRepoApp>()
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val repo = obj.optString("repo")
                    if (repo.isBlank() || repo.equals(BUILT_IN_REPO, ignoreCase = true)) continue
                    val pkg = obj.optString("packageName")
                    list.add(
                        TrackedRepoApp(
                            repo = repo,
                            displayName = obj.optString("displayName").ifBlank { repo.substringAfter("/") },
                            packageName = pkg,
                            installedVersion = readInstalledVersion(context, pkg),
                            isBuiltIn = false
                        )
                    )
                }
            } catch (_: Exception) {
                // Ignore corrupt prefs and start clean
            }
        }
        val self = TrackedRepoApp(
            repo = BUILT_IN_REPO,
            displayName = "Housekeeper Mini",
            packageName = context.packageName,
            installedVersion = BuildConfig.VERSION_NAME,
            isBuiltIn = true,
            statusText = "Installed v${BuildConfig.VERSION_NAME}"
        )
        return listOf(self) + list
    }

    fun saveTrackedApps(context: Context, apps: List<TrackedRepoApp>) {
        val arr = JSONArray()
        for (app in apps) {
            if (app.isBuiltIn) continue
            arr.put(
                JSONObject().apply {
                    put("repo", app.repo)
                    put("displayName", app.displayName)
                    put("packageName", app.packageName)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRACKED_APPS, arr.toString())
            .apply()
    }

    fun checkTrackedApp(context: Context, app: TrackedRepoApp): TrackedRepoApp {
        val installed = if (app.isBuiltIn) {
            BuildConfig.VERSION_NAME
        } else {
            readInstalledVersion(context, app.packageName)
        }
        return try {
            val apiUrl = "https://api.github.com/repos/${app.repo}/releases/latest"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = conn.responseCode
            if (code == 404) {
                conn.disconnect()
                return app.copy(
                    installedVersion = installed,
                    hasUpdate = false,
                    statusText = "No GitHub releases published yet"
                )
            }
            if (code !in 200..299) {
                conn.disconnect()
                return app.copy(installedVersion = installed, statusText = "GitHub HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v").trim()
            val apkUrl = pickBestApkAsset(json.optJSONArray("assets"))

            if (apkUrl.isEmpty()) {
                return app.copy(
                    installedVersion = installed,
                    latestVersion = tag,
                    hasUpdate = false,
                    statusText = if (tag.isNotEmpty()) "Latest v$tag (no APK asset)" else "No APK asset found"
                )
            }

            val updateAvailable = when {
                installed.isNotEmpty() -> isNewerVersion(tag, installed)
                else -> true
            }
            val status = when {
                installed.isEmpty() -> "Ready to install v$tag"
                updateAvailable -> "Update available: v$installed → v$tag"
                else -> "Up to date (v$installed)"
            }
            app.copy(
                installedVersion = installed,
                latestVersion = tag,
                apkUrl = apkUrl,
                hasUpdate = updateAvailable,
                statusText = status
            )
        } catch (e: Exception) {
            app.copy(
                installedVersion = installed,
                statusText = e.localizedMessage ?: "Check failed"
            )
        }
    }

    private fun pickBestApkAsset(assets: JSONArray?): String {
        if (assets == null) return ""
        val urls = mutableListOf<String>()
        for (i in 0 until assets.length()) {
            val url = assets.getJSONObject(i).optString("browser_download_url")
            if (url.endsWith(".apk", ignoreCase = true)) {
                urls.add(url)
            }
        }
        if (urls.isEmpty()) return ""
        val arch = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase().orEmpty()
        return urls.firstOrNull { arch.isNotEmpty() && it.lowercase().contains(arch) }
            ?: urls.firstOrNull { it.lowercase().contains("universal") }
            ?: urls.first()
    }

    private fun readInstalledVersion(context: Context, packageName: String): String {
        if (packageName.isBlank()) return ""
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(packageName, 0)
            (info.versionName ?: "").removePrefix("v").trim()
        } catch (_: Exception) {
            ""
        }
    }

    internal fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        val max = maxOf(r.size, l.size)
        for (i in 0 until max) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
