package com.sagisawa.gbfaccelerator.update

import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

object GitHubReleaseClient {

    private const val OWNER = "Lanfearr"
    private const val REPO = "GBF-Cache-for-Android"

    private const val RELEASES_API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val RELEASES_ATOM_URL = "https://github.com/$OWNER/$REPO/releases.atom"
    const val RELEASES_PAGE_URL = "https://github.com/$OWNER/$REPO/releases"

    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    @Volatile
    private var cachedRelease: CachedRelease? = null

    fun check(currentVersion: String, force: Boolean = false): UpdateCheckResult {
        if (!force) {
            cachedRelease?.takeIf {
                System.currentTimeMillis() - it.checkedAt < CACHE_TTL_MS
            }?.let {
                return UpdateCheckResult.Success(it.release)
            }
        }

        val apiError = try {
            val release = fetchFromApi(currentVersion)
            cache(release)
            return UpdateCheckResult.Success(release)
        } catch (e: Throwable) {
            e.message ?: "检查更新失败"
        }

        return try {
            val release = fetchFromAtom(currentVersion)
            cache(release)
            UpdateCheckResult.Success(release)
        } catch (_: Throwable) {
            UpdateCheckResult.Failure(apiError)
        }
    }

    private fun fetchFromApi(currentVersion: String): LatestRelease {
        val connection = openConnection(
            RELEASES_API_URL,
            currentVersion,
            "application/vnd.github+json"
        )
        try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            when {
                code == HttpURLConnection.HTTP_FORBIDDEN ->
                    throw IOException("GitHub API 访问受限（可能触发频率限制），请稍后重试。")
                code != HttpURLConnection.HTTP_OK ->
                    throw IOException("GitHub API 返回 HTTP $code")
            }
            return parseRelease(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchFromAtom(currentVersion: String): LatestRelease {
        val connection = openConnection(
            RELEASES_ATOM_URL,
            currentVersion,
            "application/atom+xml, application/xml;q=0.9, */*;q=0.8"
        )
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Release 页面返回 HTTP $code")
            }
            return parseAtomFeed(readBody(connection, code))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject): LatestRelease {
        val tag = json.optString("tag_name").trim()
        if (tag.isEmpty()) {
            throw IOException("Release 缺少版本号")
        }
        val version = Versioning.stripPrefix(tag)
        if (version.isEmpty()) {
            throw IOException("Release 版本号无效")
        }

        val assetsJson = json.optJSONArray("assets")
        val assets = buildList {
            if (assetsJson != null) {
                for (index in 0 until assetsJson.length()) {
                    val item = assetsJson.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    val url = item.optString("browser_download_url").trim()
                    if (name.isNotEmpty() && url.isNotEmpty()) {
                        add(ReleaseAsset(name, url, item.optLong("size", 0L)))
                    }
                }
            }
        }

        val htmlUrl = json.optString("html_url").trim()
        return LatestRelease(
            version = version,
            title = json.optString("name").trim().ifEmpty { tag },
            notes = json.optString("body").trim(),
            releaseUrl = htmlUrl.ifEmpty { "$RELEASES_PAGE_URL/tag/$tag" },
            apkAsset = ReleaseAssetSelector.select(assets)
        )
    }

    private fun parseAtomFeed(xml: String): LatestRelease {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        var event = parser.eventType
        var inEntry = false
        var inTitle = false
        var inContent = false
        var done = false
        val title = StringBuilder()
        val content = StringBuilder()
        var releaseUrl = ""

        while (event != XmlPullParser.END_DOCUMENT && !done) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> if (!inEntry) {
                        inEntry = true
                    }
                    "link" -> if (inEntry && releaseUrl.isEmpty()) {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        if ((rel.isNullOrEmpty() || rel == "alternate") && href.contains("/releases/tag/")) {
                            releaseUrl = href
                        }
                    }
                    "title" -> if (inEntry) inTitle = true
                    "content" -> if (inEntry) inContent = true
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) title.append(parser.text)
                    if (inContent) content.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> inTitle = false
                    "content" -> inContent = false
                    "entry" -> if (inEntry) done = true
                }
            }
            if (!done) {
                event = parser.next()
            }
        }

        if (releaseUrl.isEmpty()) {
            throw IOException("未找到可用 Release")
        }
        val tag = releaseUrl.substringAfterLast("/tag/", "")
        val version = Versioning.stripPrefix(tag)
        if (version.isEmpty()) {
            throw IOException("Release 版本号无效")
        }

        val assetName = "GBF-Cache-$version.apk"
        val notes = content.toString().replace(Regex("<[^>]*>"), "").trim()
        return LatestRelease(
            version = version,
            title = title.toString().trim().ifEmpty { tag },
            notes = notes,
            releaseUrl = releaseUrl,
            apkAsset = ReleaseAsset(
                name = assetName,
                downloadUrl = "$RELEASES_PAGE_URL/download/$tag/$assetName",
                size = 0L
            )
        )
    }

    private fun openConnection(
        url: String,
        currentVersion: String,
        accept: String
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GBFCache/$currentVersion")
            setRequestProperty("Accept", accept)
        }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun cache(release: LatestRelease) {
        cachedRelease = CachedRelease(System.currentTimeMillis(), release)
    }

    private data class CachedRelease(
        val checkedAt: Long,
        val release: LatestRelease
    )
}
