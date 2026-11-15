package com.sagisawa.gbfaccelerator.update

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

object UpdateDownloader {

    suspend fun download(
        asset: ReleaseAsset,
        destinationDir: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File {
        destinationDir.mkdirs()

        val safeName = asset.name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "update.apk" }
        val finalFile = File(destinationDir, safeName)
        val partFile = File(destinationDir, "$safeName.part")
        partFile.delete()
        finalFile.delete()

        val connection = openFollowingRedirects(asset.downloadUrl)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code")
            }

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
            var downloaded = 0L
            var lastProgressAt = 0L

            connection.inputStream.use { input ->
                partFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt >= 100 || (total > 0 && downloaded >= total)) {
                            lastProgressAt = now
                            onProgress(downloaded, total)
                        }
                    }
                }
            }

            if (total > 0 && downloaded != total) {
                throw IOException("下载不完整")
            }
            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            return finalFile
        } catch (e: Throwable) {
            partFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun openFollowingRedirects(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(5) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "GBFCache")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("HTTP $code")
                }
                current = URL(current, location)
            } else {
                return connection
            }
        }
        throw IOException("重定向次数过多")
    }
}
