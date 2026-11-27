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
        "8.6.112.224:5279",
        "8.6.112.46:903",
        "8.6.112.206:3854",
        "8.6.112.31:859",
        "8.6.112.113:864",
        "8.6.112.121:500",
        "8.6.112.208:2408",
        "8.6.112.10:1701",
        "8.6.112.132:8854",
        "8.6.112.146:942",
        "188.114.96.1:2408",
        "188.114.97.1:2408",
        "188.114.98.1:2408",
        "188.114.99.1:2408",
        "188.114.96.1:500",
        "188.114.96.1:4500",
        "engage.cloudflareclient.com:2408"
    )

    private const val QUIC_DISGUISE_I1 = "<b 0xc70000000108ce1bf31eec7d93360000449e227e4596ed7f75c4d35ce31880b4133107c822c6355b51f0d7c1bba96d5c210a48aca01885fed0871cfc37d59137d73b506dc013bb4a13c060ca5b04b7ae215af71e37d6e8ff1db235f9fe0c25cb8b492471054a7c8d0d6077d430d07f6e87a8699287f6e69f54263c7334a8e144a29851429bf2e350e519445172d36953e96085110ce1fb641e5efad42c0feb4711ece959b72cc4d6f3c1e83251adb572b921534f6ac4b10927167f41fe50040a75acef62f45bded67c0b45b9d655ce374589cad6f568b8475b2e8921ff98628f86ff2eb5bcce6f3ddb7dc89e37c5b5e78ddc8d93a58896e530b5f9f1448ab3b7a1d1f24a63bf981634f6183a21af310ffa52e9ddf5521561760288669de01a5f2f1a4f922e68d0592026bbe4329b654d4f5d6ace4f6a23b8560b720a5350691c0037b10acfac9726add44e7d3e880ee6f3b0d6429ff33655c297fee786bb5ac032e48d2062cd45e305e6d8d8b82bfbf0fdbc5ec09943d1ad02b0b5868ac4b24bb10255196be883562c35a713002014016b8cc5224768b3d330016cf8ed9300fe6bf39b4b19b3667cddc6e7c7ebe4437a58862606a2a66bd4184b09ab9d2cd3d3faed4d2ab71dd821422a9540c4c5fa2a9b2e6693d411a22854a8e541ed930796521f03a54254074bc4c5bca152a1723260e7d70a24d49720acc544b41359cfc252385bda7de7d05878ac0ea0343c77715e145160e6562161dfe2024846dfda3ce99068817a2418e66e4f37dea40a21251c8a034f83145071d93baadf050ca0f95dc9ce2338fb082d64fbc8faba905cec66e65c0e1f9b003c32c943381282d4ab09bef9b6813ff3ff5118623d2617867e25f0601df583c3ac51bc6303f79e68d8f8de4b8363ec9c7728b3ec5fcd5274edfca2a42f2727aa223c557afb33f5bea4f64aeb252c0150ed734d4d8eccb257824e8e090f65029a3a042a51e5cc8767408ae07d55da8507e4d009ae72c47ddb138df3cab6cc023df2532f88fb5a4c4bd917fafde0f3134be09231c389c70bc55cb95a779615e8e0a76a2b4d943aabfde0e394c985c0cb0376930f92c5b6998ef49ff4a13652b787503f55c4e3d8eebd6e1bc6db3a6d405d8405bd7a8db7cefc64d16e0d105a468f3d33d29e5744a24c4ac43ce0eb1bf6b559aed520b91108cda2de6e2c4f14bc4f4dc58712580e07d217c8cca1aaf7ac04bab3e7b1008b966f1ed4fba3fd93a0a9d3a27127e7aa587fbcc60d548300146bdc126982a58ff5342fc41a43f83a3d2722a26645bc961894e339b953e78ab395ff2fb854247ad06d446cc2944a1aefb90573115dc198f5c1efbc22bc6d7a74e41e666a643d5f85f57fde81b87ceff95353d22ae8bab11684180dd142642894d8dc34e402f802c2fd4a73508ca99124e428d67437c871dd96e506ffc39c0fc401f666b437adca41fd563cbcfd0fa22fbbf8112979c4e677fb533d981745cceed0fe96da6cc0593c430bbb71bcbf924f70b4547b0bb4d41c94a09a9ef1147935a5c75bb2f721fbd24ea6a9f5c9331187490ffa6d4e34e6bb30c2c54a0344724f01088fb2751a486f425362741664efb287bce66c4a544c96fa8b124d3c6b9eaca170c0b530799a6e878a57f402eb0016cf2689d55c76b2a91285e2273763f3afc5bc9398273f5338a06d>"

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
            appendLine("I1 = $QUIC_DISGUISE_I1")
            appendLine("ContentPaddingAddition = 27-97")
            appendLine("MaxHandshakeAttempts = 18-26")
            appendLine("KeepaliveTimeout = 9-16")
            appendLine("RejectAfterTime = 171-196")
            appendLine("RekeyAfterTime = 116-131")
            appendLine("RekeyTimeout = 6-9")
            appendLine("RandomTrailers = on")
            appendLine("DisableCookies = on")
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
