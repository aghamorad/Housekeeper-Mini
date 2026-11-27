package com.morad.housekeepermini

import com.morad.housekeepermini.engine.CloudflareVpnEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareVpnEngineTest {

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun x25519MatchesRfc7748Vectors() {
        val v1 = bytesToHex(
            CloudflareVpnEngine.x25519(
                hexToBytes("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4"),
                hexToBytes("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
            )
        )
        assertEquals("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552", v1)

        val base = ByteArray(32).apply { this[0] = 9 }
        val alice = bytesToHex(
            CloudflareVpnEngine.x25519(
                hexToBytes("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"),
                base
            )
        )
        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", alice)
    }

    @Test
    fun sanitizeTokenStripsBearerAndQuotes() {
        assertEquals("cfat_abc123", CloudflareVpnEngine.sanitizeToken("  \"Bearer cfat_abc123\" \n"))
    }

    @Test
    fun buildVlessLinkUsesCleanIpAsAddressAndWorkerAsSniHost() {
        val link = CloudflareVpnEngine.buildVlessLink(
            workerHost = "amber-notes-42.example.workers.dev",
            uuid = "044caab5-3190-4464-944e-709fe5924265",
            address = "104.18.2.55",
            port = 443,
            label = "HK Clean"
        )
        assertTrue(link.startsWith("vless://044caab5-3190-4464-944e-709fe5924265@104.18.2.55:443?"))
        assertTrue(link.contains("sni=amber-notes-42.example.workers.dev"))
        assertTrue(link.contains("host=amber-notes-42.example.workers.dev"))
        assertTrue(link.endsWith("#HK-Clean"))
    }

    @Test
    fun neutralWorkerNameAvoidsAbuseKeywords() {
        repeat(20) {
            val name = CloudflareVpnEngine.generateNeutralWorkerName()
            assertFalse(name.contains("vpn"))
            assertFalse(name.contains("proxy"))
            assertFalse(name.contains("bpb"))
        }
    }

    @Test
    fun sampleCandidateIpsReturnsRequestedIpv4Count() {
        val ips = CloudflareVpnEngine.sampleCandidateIps(24)
        assertEquals(24, ips.size)
        assertTrue(ips.all { it.split(".").size == 4 && !it.contains(":") })
    }
}
