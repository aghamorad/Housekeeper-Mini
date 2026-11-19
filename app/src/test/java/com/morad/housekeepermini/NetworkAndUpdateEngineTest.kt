package com.morad.housekeepermini

import com.morad.housekeepermini.engine.NetworkAndUpdateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkAndUpdateEngineTest {

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
}
