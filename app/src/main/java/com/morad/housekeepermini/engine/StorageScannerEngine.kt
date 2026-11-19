package com.morad.housekeepermini.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.morad.housekeepermini.model.StorageCategory
import com.morad.housekeepermini.model.StorageItem
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object StorageScannerEngine {

    private const val LARGE_FILE_BYTES = 25L * 1024L * 1024L
    private const val OLD_DOWNLOAD_DAYS = 30L
    private const val MAX_HASH_SIZE_BYTES = 200L * 1024L * 1024L

    fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun openStoragePermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val intents = listOf(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
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
                // Try next fallback intent (important on Meta Quest / TV)
            }
        }
        return false
    }

    fun scan(): List<StorageItem> {
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return scanDirectory(root, downloadsDir)
    }

    internal fun scanDirectory(root: File, downloadsDir: File? = null): List<StorageItem> {
        if (!root.exists() || !root.canRead()) return emptyList()

        val apks = mutableListOf<StorageItem>()
        val large = mutableListOf<StorageItem>()
        val oldDownloads = mutableListOf<StorageItem>()
        val sizeBuckets = HashMap<Long, MutableList<File>>()

        val now = System.currentTimeMillis()
        val oldCutoff = now - TimeUnit.DAYS.toMillis(OLD_DOWNLOAD_DAYS)
        val downloadsPrefix = downloadsDir?.absolutePath ?: ""
        val rootPath = root.absolutePath

        fun relativeParent(file: File): String {
            val parent = file.parentFile?.absolutePath ?: return "Storage"
            val rel = parent.removePrefix(rootPath).trimStart('/')
            return rel.ifEmpty { "Internal Storage" }
        }

        walkSafe(root, depth = 0, maxDepth = 6) { file ->
            val len = file.length()
            if (len <= 0L) return@walkSafe
            val name = file.name
            val lower = name.lowercase()
            val mod = file.lastModified()

            if (lower.endsWith(".apk")) {
                apks.add(
                    StorageItem(
                        path = file.absolutePath,
                        name = name,
                        sizeBytes = len,
                        modifiedMs = mod,
                        category = StorageCategory.APK,
                        detail = relativeParent(file)
                    )
                )
            }

            if (len >= LARGE_FILE_BYTES) {
                large.add(
                    StorageItem(
                        path = file.absolutePath,
                        name = name,
                        sizeBytes = len,
                        modifiedMs = mod,
                        category = StorageCategory.LARGE,
                        detail = relativeParent(file)
                    )
                )
            }

            if (downloadsPrefix.isNotEmpty() &&
                file.absolutePath.startsWith(downloadsPrefix) &&
                mod in 1..oldCutoff &&
                !lower.endsWith(".apk")
            ) {
                val days = TimeUnit.MILLISECONDS.toDays(now - mod)
                oldDownloads.add(
                    StorageItem(
                        path = file.absolutePath,
                        name = name,
                        sizeBytes = len,
                        modifiedMs = mod,
                        category = StorageCategory.OLD_DOWNLOAD,
                        detail = "Untouched for $days days • ${relativeParent(file)}"
                    )
                )
            }

            if (len in 1024..MAX_HASH_SIZE_BYTES) {
                sizeBuckets.getOrPut(len) { mutableListOf() }.add(file)
            }
        }

        val duplicates = mutableListOf<StorageItem>()
        for ((size, files) in sizeBuckets) {
            if (files.size < 2) continue
            val byHash = HashMap<String, MutableList<File>>()
            for (f in files) {
                val hash = quickSha256(f) ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(f)
            }
            for ((_, group) in byHash) {
                if (group.size < 2) continue
                val sorted = group.sortedBy { it.lastModified() }
                val originalName = sorted.first().name
                // Keep the oldest copy untouched; surface the extra copies
                for (dup in sorted.drop(1)) {
                    duplicates.add(
                        StorageItem(
                            path = dup.absolutePath,
                            name = dup.name,
                            sizeBytes = size,
                            modifiedMs = dup.lastModified(),
                            category = StorageCategory.DUPLICATE,
                            detail = "Copy of $originalName • ${relativeParent(dup)}"
                        )
                    )
                }
            }
        }

        return (apks.sortedByDescending { it.sizeBytes } +
            duplicates.sortedByDescending { it.sizeBytes } +
            large.sortedByDescending { it.sizeBytes }.take(80) +
            oldDownloads.sortedByDescending { it.sizeBytes }.take(80))
    }

    fun deleteItems(paths: Collection<String>): Int {
        var deleted = 0
        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.isFile && file.delete()) {
                deleted++
            }
        }
        return deleted
    }

    private fun walkSafe(dir: File, depth: Int, maxDepth: Int, onFile: (File) -> Unit) {
        if (depth > maxDepth) return
        val name = dir.name
        // Skip Android/data and hidden system/backup folders for speed & safety
        if (depth > 0 && (name.startsWith(".") || name == "Android" || name == "HousekeeperMini-Backups")) {
            return
        }
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                walkSafe(child, depth + 1, maxDepth, onFile)
            } else if (child.isFile) {
                onFile(child)
            }
        }
    }

    private fun quickSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(16 * 1024)
                var read = input.read(buf)
                while (read != -1) {
                    digest.update(buf, 0, read)
                    read = input.read(buf)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
