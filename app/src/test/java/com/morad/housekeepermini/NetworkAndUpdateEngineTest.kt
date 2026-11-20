package com.morad.housekeepermini

import com.morad.housekeepermini.engine.NetworkAndUpdateEngine
import com.morad.housekeepermini.model.SpeedTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAndUpdateEngineTest {

    @Test
    fun speedTestStats_andVerdicts_areAccurate() {
        assertEquals(20.0, NetworkAndUpdateEngine.median(listOf(40.0, 10.0, 20.0)), 0.01)
        assertEquals(25.0, NetworkAndUpdateEngine.median(listOf(40.0, 10.0, 20.0, 30.0)), 0.01)
        assertEquals(6.0, NetworkAndUpdateEngine.jitter(listOf(20.0, 26.0, 20.0)), 0.01)

        val fast = SpeedTestResult(downloadMbps = 145.0, uploadMbps = 42.0, pingMs = 18.0, jitterMs = 2.1)
        assertEquals("Very fast connection", fast.verdictTitle)
        val everyday = SpeedTestResult(downloadMbps = 38.0, uploadMbps = 12.0, pingMs = 34.0, jitterMs = 4.0)
        assertEquals("Good everyday connection", everyday.verdictTitle)
    }

    @Test
    fun parseGitHubRepo_handlesUrlsAndShorthand() {
        assertEquals("ImranR98/Obtainium", NetworkAndUpdateEngine.parseGitHubRepo("ImranR98/Obtainium"))
        assertEquals(
            "ImranR98/Obtainium",
            NetworkAndUpdateEngine.parseGitHubRepo("https://github.com/ImranR98/Obtainium/releases/tag/v1.7.4")
        )
        assertEquals(
            "aghamorad/Housekeeper-Mini",
            NetworkAndUpdateEngine.parseGitHubRepo("https://github.com/aghamorad/Housekeeper-Mini/")
        )
        assertNull(NetworkAndUpdateEngine.parseGitHubRepo("not-a-repo"))
    }

    @Test
    fun isNewerVersion_comparesSemverSegments() {
        assertTrue(NetworkAndUpdateEngine.isNewerVersion("0.2.0", "0.1.0"))
        assertTrue(NetworkAndUpdateEngine.isNewerVersion("1.0.0", "0.9.9"))
        assertFalse(NetworkAndUpdateEngine.isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(NetworkAndUpdateEngine.isNewerVersion("0.1.0", "0.2.0"))
    }
}
