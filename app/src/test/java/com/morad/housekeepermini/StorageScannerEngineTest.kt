package com.morad.housekeepermini

import com.morad.housekeepermini.engine.StorageScannerEngine
import com.morad.housekeepermini.model.StorageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class StorageScannerEngineTest {

    @Test
    fun scanDirectory_findsApksAndKeepsOldestDuplicate() {
        val tempRoot = createTempDirectory("hkmini-test").toFile()
        try {
            val downloads = File(tempRoot, "Download").apply { mkdirs() }
            val sub = File(downloads, "Sub").apply { mkdirs() }

            val payload = ByteArray(2048) { (it % 251).toByte() }
            val olderCopy = File(downloads, "photo.jpg").apply {
                writeBytes(payload)
                setLastModified(1_700_000_000_000L)
            }
            val newerCopy = File(sub, "photo_copy.jpg").apply {
                writeBytes(payload)
                setLastModified(1_700_000_500_000L)
            }
            val apk = File(downloads, "app-installer.apk").apply {
                writeBytes(ByteArray(4096) { 1 })
            }

            val items = StorageScannerEngine.scanDirectory(tempRoot, downloads)

            val apkItems = items.filter { it.category == StorageCategory.APK }
            assertEquals(1, apkItems.size)
            assertEquals(apk.absolutePath, apkItems.first().path)
            assertEquals("Download", apkItems.first().detail)

            val dupItems = items.filter { it.category == StorageCategory.DUPLICATE }
            assertEquals(1, dupItems.size)
            assertEquals(newerCopy.absolutePath, dupItems.first().path)
            assertTrue(dupItems.first().detail.contains("Copy of ${olderCopy.name}"))
            assertTrue(dupItems.first().detail.contains("Download/Sub"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
