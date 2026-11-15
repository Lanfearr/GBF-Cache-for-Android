package com.sagisawa.gbfaccelerator.core

import android.content.Context
import java.io.File
import java.util.Locale

object CacheStats {

    fun cacheDir(context: Context): File = File(context.filesDir, "cache/gbf/https")

    fun getStats(dir: File): Pair<Int, Long> {
        var count = 0
        var size = 0L
        if (dir.exists() && dir.isDirectory) {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    count++
                    size += file.length()
                }
            }
        }
        return Pair(count, size)
    }

    fun formatSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
