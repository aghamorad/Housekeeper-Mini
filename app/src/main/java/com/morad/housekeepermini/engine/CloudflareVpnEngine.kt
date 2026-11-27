package com.morad.housekeepermini.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class CfAccountInfo(
    val accountId: String,
    val accountName: String,
    val subdomain: String?,
    val workers: List<String>
)

data class CfCleanIpResult(
    val ip: String,
    val port: Int,
    val latencyMs: Int,
    val reachable: Boolean
) {
    val qualityLabel: String
        get() = when {
            !reachable -> "Blocked / Timed out"
            latencyMs <= 120 -> "Fast & Clean"
            latencyMs <= 260 -> "Clean"
            else -> "Slow"
        }
}

data class WarpConfigOutput(
    val confText: String,
    val registered: Boolean,
    val message: String
)

object CloudflareVpnEngine {

    private const val PREFS_NAME = "housekeeper_cf_vpn"
    private const val KEY_TOKEN = "cf_api_token"
    private const val KEY_WORKER_HOST = "cf_worker_host"
    private const val KEY_WORKER_UUID = "cf_worker_uuid"

    private const val WARP_PEER_PUBKEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    private const val WARP_REG_URL = "https://api.cloudflareclient.com/v0a4005/reg"

    val warpEndpoints: List<String> = listOf(
        "engage.cloudflareclient.com:2408",
        "162.159.192.1:2408",
        "162.159.193.1:2408",
        "162.159.195.1:2408",
        "162.159.193.5:2408",
        "162.159.193.10:2408",
        "162.159.193.1:500",
        "162.159.193.1:1701",
        "162.159.193.1:4500",
        "188.114.96.1:2408",
        "188.114.97.1:2408",
        "188.114.98.1:2408",
        "188.114.99.1:2408",
        "188.114.96.1:500",
        "188.114.96.1:4500"
    )

    val iranianCarriers: List<Pair<String, String>> = listOf(
        "MCI (Hamrah-e Aval)" to "mci.ircf.space",
        "Irancell (MTN)" to "mtn.ircf.space",
        "Rightel" to "rtl.ircf.space"
    )

    private val cfIpv4Prefixes = listOf(
        "104.16.132", "104.16.248", "104.17.64", "104.17.148",
        "104.18.2", "104.18.26", "104.19.37", "104.19.196",
        "104.21.16", "104.21.48", "104.22.4", "104.22.58",
        "104.24.10", "104.26.8", "104.27.128", "162.159.135",
        "162.159.136", "162.159.138", "162.159.192", "162.159.193",
        "172.64.32", "172.64.80", "172.67.128", "172.67.180",
        "188.114.96", "188.114.97", "188.114.98", "188.114.99"
    )

    // MARK: - Local Preferences

    fun sanitizeToken(raw: String): String =
        raw.trim().trim('"', '\'').removePrefix("Bearer ").trim()

    fun loadSavedToken(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, sanitizeToken(token)).apply()
    }

    fun forgetToken(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_TOKEN).apply()
    }

    fun loadWorkerHost(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_WORKER_HOST, "") ?: ""

    fun saveWorkerHost(context: Context, host: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WORKER_HOST, host.trim()).apply()
    }

    fun loadOrCreateUuid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_WORKER_UUID, "") ?: ""
        if (existing.isNotBlank()) return existing
        val fresh = UUID.randomUUID().toString().lowercase(Locale.US)
        prefs.edit().putString(KEY_WORKER_UUID, fresh).apply()
        return fresh
    }

    fun saveUuid(context: Context, uuid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WORKER_UUID, uuid.trim().lowercase(Locale.US)).apply()
    }

    fun generateNeutralWorkerName(): String {
        val adj = listOf("amber", "cedar", "maple", "linen", "pebble", "harbor", "meadow", "willow", "quartz", "copper")
        val noun = listOf("notes", "journal", "studio", "garden", "lantern", "archive", "atelier", "gallery", "ledger", "canvas")
        val r = SecureRandom()
        return "${adj[r.nextInt(adj.size)]}-${noun[r.nextInt(noun.size)]}-${10 + r.nextInt(90)}"
    }

    // MARK: - Pure Kotlin RFC 7748 X25519

    private val P255 = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)
    private val P255_MINUS_2 = P255.subtract(BigInteger.TWO)

    private fun modP(x: BigInteger): BigInteger {
        val r = x.mod(P255)
        return if (r.signum() < 0) r.add(P255) else r
    }

    private fun leToBigInt(bytes: ByteArray): BigInteger {
        var n = BigInteger.ZERO
        for (i in bytes.size - 1 downTo 0) {
            n = n.shiftLeft(8).or(BigInteger.valueOf((bytes[i].toInt() and 0xFF).toLong()))
        }
        return n
    }

    private fun bigIntToLe32(value: BigInteger): ByteArray {
        val out = ByteArray(32)
        var n = value
        for (i in 0 until 32) {
            out[i] = n.and(BigInteger.valueOf(0xFF)).toByte()
            n = n.shiftRight(8)
        }
        return out
    }

    internal fun x25519(scalarBytes: ByteArray, uBytes: ByteArray): ByteArray {
        val k = scalarBytes.copyOf(32)
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = ((k[31].toInt() and 127) or 64).toByte()
        val kInt = leToBigInt(k)
        val mask = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE)
        val x1 = leToBigInt(uBytes).and(mask)

        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val bit = if (kInt.testBit(t)) 1 else 0
            swap = swap xor bit
            if (swap == 1) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = bit
            val a = modP(x2.add(z2))
            val aa = modP(a.multiply(a))
            val b = modP(x2.subtract(z2))
            val bb = modP(b.multiply(b))
            val e = modP(aa.subtract(bb))
            val c = modP(x3.add(z3))
            val d = modP(x3.subtract(z3))
            val da = modP(d.multiply(a))
            val cb = modP(c.multiply(b))
            val nx3 = modP(da.add(cb).pow(2))
            val nz3 = modP(x1.multiply(modP(da.subtract(cb).pow(2))))
            val nx2 = modP(aa.multiply(bb))
            val nz2 = modP(e.multiply(modP(aa.add(A24.multiply(e)))))
            x3 = nx3; z3 = nz3; x2 = nx2; z2 = nz2
        }
        if (swap == 1) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }
        val res = modP(x2.multiply(z2.modPow(P255_MINUS_2, P255)))
        return bigIntToLe32(res)
    }

    private fun makeX25519KeyPair(): Pair<String, String> {
        val priv = ByteArray(32)
        SecureRandom().nextBytes(priv)
        priv[0] = (priv[0].toInt() and 248).toByte()
        priv[31] = ((priv[31].toInt() and 127) or 64).toByte()
        val base = ByteArray(32).apply { this[0] = 9 }
        val pub = x25519(priv, base)
        return Base64.encodeToString(priv, Base64.NO_WRAP) to Base64.encodeToString(pub, Base64.NO_WRAP)
    }

    // MARK: - Mode 1: Generate AmneziaWG WARP Config

    fun generateAmneziaWarpConfig(
        endpoint: String,
        heavyJunk: Boolean,
        registerOnline: Boolean
    ): WarpConfigOutput {
        val (privB64, pubB64) = makeX25519KeyPair()
        var v4 = "172.16.0.2"
        var peerKey = WARP_PEER_PUBKEY
        var registered = false
        var note = "Local AmneziaWG draft generated."

        if (registerOnline) {
            try {
                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                val body = JSONObject().apply {
                    put("key", pubB64)
                    put("install_id", "")
                    put("fcm_token", "")
                    put("tos", iso)
                    put("model", "Android")
                    put("serial_number", "")
                    put("locale", "en_US")
                }.toString()

                val conn = (URL(WARP_REG_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "okhttp/3.12.1")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                val respText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()

                if (code in 200..299) {
                    val json = JSONObject(respText)
                    val cfg = json.optJSONObject("config")
                    val iface = cfg?.optJSONObject("interface")?.optJSONObject("addresses")
                    v4 = iface?.optString("v4").takeUnless { it.isNullOrBlank() } ?: v4
                    val peers = cfg?.optJSONArray("peers")
                    if (peers != null && peers.length() > 0) {
                        peerKey = peers.getJSONObject(0).optString("public_key").takeUnless { it.isNullOrBlank() } ?: peerKey
                    }
                    registered = true
                    note = "WARP key registered! Copy or share into AmneziaVPN."
                } else {
                    note = "Cloudflare WARP returned HTTP $code. Draft created — retry once with internet."
                }
            } catch (e: Exception) {
                note = "Could not reach WARP registration (${e.localizedMessage ?: "network error"}). Retry once with internet."
            }
        }

        val jc = if (heavyJunk) 5 else 3
        val jmin = if (heavyJunk) 10 else 1
        val jmax = if (heavyJunk) 40 else 3

        val conf = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privB64")
            appendLine("Address = $v4/32")
            appendLine("DNS = 1.1.1.1")
            appendLine("MTU = 1280")
            appendLine("Jc = $jc")
            appendLine("Jmin = $jmin")
            appendLine("Jmax = $jmax")
            appendLine("S1 = 0")
            appendLine("S2 = 0")
            appendLine("H1 = 1")
            appendLine("H2 = 2")
            appendLine("H3 = 3")
            appendLine("H4 = 4")
            appendLine("I1 = <b 0xf6ab3267fa>")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $peerKey")
            appendLine("AllowedIPs = 0.0.0.0/0")
            appendLine("Endpoint = ${endpoint.trim()}")
            appendLine("PersistentKeepalive = 25")
        }
        return WarpConfigOutput(confText = conf, registered = registered, message = note)
    }

    // MARK: - Mode 2: Cloudflare Token Verify & Worker Deploy

    fun verifyToken(rawToken: String): Result<CfAccountInfo> {
        val token = sanitizeToken(rawToken)
        if (token.isBlank()) return Result.failure(IllegalArgumentException("Paste your Cloudflare API token first."))
        return try {
            val verify = cfGet("/user/tokens/verify", token)
            if (!verify.optBoolean("success", false)) {
                return Result.failure(IllegalStateException("Cloudflare rejected this token. Check that it was copied completely."))
            }
            val accounts = cfGet("/accounts?per_page=10", token)
            val arr = accounts.optJSONArray("result")
            if (arr == null || arr.length() == 0) {
                return Result.failure(IllegalStateException("No Cloudflare account found. Use the 'Edit Cloudflare Workers' template."))
            }
            val first = arr.getJSONObject(0)
            val accountId = first.getString("id")
            val accountName = first.optString("name", "Cloudflare Account")

            val subdomain = runCatching {
                cfGet("/accounts/$accountId/workers/subdomain", token)
                    .optJSONObject("result")?.optString("subdomain")?.takeIf { it.isNotBlank() }
            }.getOrNull()

            val workers = runCatching {
                val list = cfGet("/accounts/$accountId/workers/scripts", token).optJSONArray("result")
                buildList {
                    if (list != null) {
                        for (i in 0 until list.length()) {
                            val id = list.getJSONObject(i).optString("id")
                            if (id.isNotBlank()) add(id)
                        }
                    }
                }
            }.getOrDefault(emptyList())

            Result.success(CfAccountInfo(accountId, accountName, subdomain, workers))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteWorker(accountId: String, rawToken: String, workerName: String): Result<String> {
        val token = sanitizeToken(rawToken)
        return try {
            val conn = (URL("https://api.cloudflare.com/client/v4/accounts/$accountId/workers/scripts/$workerName")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Result.success("Deleted Worker '$workerName'.")
            else Result.failure(IllegalStateException("Cloudflare returned HTTP $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cfGet(path: String, token: String): JSONObject {
        val conn = (URL("https://api.cloudflare.com/client/v4$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        conn.disconnect()
        return JSONObject(text)
    }

    // MARK: - Clean Cloudflare IPv4 Scanner

    fun sampleCandidateIps(count: Int): List<String> {
        val r = SecureRandom()
        val shuffled = cfIpv4Prefixes.shuffled(r)
        val out = LinkedHashSet<String>()
        var i = 0
        while (out.size < count) {
            val prefix = shuffled[i % shuffled.size]
            out.add("$prefix.${2 + r.nextInt(248)}")
            i++
        }
        return out.toList()
    }

    fun scanCleanIps(
        count: Int = 24,
        port: Int = 443,
        onProgress: (tested: Int, total: Int, cleanSoFar: List<CfCleanIpResult>) -> Unit
    ): List<CfCleanIpResult> {
        val candidates = sampleCandidateIps(count)
        val pool = Executors.newFixedThreadPool(8)
        val clean = mutableListOf<CfCleanIpResult>()
        var tested = 0
        try {
            for (chunk in candidates.chunked(8)) {
                val futures: List<Future<CfCleanIpResult>> = chunk.map { ip ->
                    pool.submit<CfCleanIpResult> { probeTlsIp(ip, port) }
                }
                for (f in futures) {
                    val res = runCatching { f.get() }.getOrNull()
                    tested++
                    if (res != null && res.reachable) {
                        clean.add(res)
                        clean.sortBy { it.latencyMs }
                    }
                }
                onProgress(tested, candidates.size, clean.toList())
            }
        } finally {
            pool.shutdownNow()
        }
        return clean.sortedBy { it.latencyMs }
    }

    private fun probeTlsIp(ip: String, port: Int): CfCleanIpResult {
        val start = System.nanoTime()
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val socket = factory.createSocket() as SSLSocket
            socket.use { s ->
                s.connect(InetSocketAddress(ip, port), 2200)
                s.soTimeout = 2200
                s.startHandshake()
                val ms = ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
                CfCleanIpResult(ip = ip, port = port, latencyMs = ms, reachable = true)
            }
        } catch (_: Exception) {
            CfCleanIpResult(ip = ip, port = port, latencyMs = 9999, reachable = false)
        }
    }

    // MARK: - VLESS Link Builder & Share / Clipboard

    fun buildVlessLink(workerHost: String, uuid: String, address: String, port: Int = 443, label: String): String {
        val host = workerHost.trim()
        val cleanUuid = uuid.trim().lowercase(Locale.US)
        val safeLabel = label.replace(" ", "-")
        return "vless://$cleanUuid@$address:$port?encryption=none&security=tls&sni=$host&fp=chrome&type=ws&host=$host&path=%2F%3Fed%3D2048#$safeLabel"
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
