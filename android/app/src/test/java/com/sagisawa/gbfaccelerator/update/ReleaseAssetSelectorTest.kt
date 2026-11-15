package com.sagisawa.gbfaccelerator.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseAssetSelectorTest {

    @Test
    fun select_prefersArm64Apk() {
        val assets = listOf(
            asset("GBF-Cache-1.2.0-armeabi-v7a.apk"),
            asset("GBF-Cache-1.2.0-arm64-v8a.apk"),
            asset("GBF-Cache-1.2.0.apk")
        )

        assertEquals("GBF-Cache-1.2.0-arm64-v8a.apk", ReleaseAssetSelector.select(assets)?.name)
    }

    @Test
    fun select_ignoresNonApkAssets() {
        val assets = listOf(
            asset("GBF-Cache-1.2.0.zip"),
            asset("sha256sums.txt"),
            asset("GBF-Cache-1.2.0.apk")
        )

        assertEquals("GBF-Cache-1.2.0.apk", ReleaseAssetSelector.select(assets)?.name)
    }

    @Test
    fun select_returnsNullWithoutApk() {
        val assets = listOf(
            asset("GBF-Cache-1.2.0.zip"),
            asset("source.tar.gz")
        )

        assertNull(ReleaseAssetSelector.select(assets))
    }

    private fun asset(name: String): ReleaseAsset {
        return ReleaseAsset(
            name = name,
            downloadUrl = "https://example.com/$name",
            size = 1L
        )
    }
}
