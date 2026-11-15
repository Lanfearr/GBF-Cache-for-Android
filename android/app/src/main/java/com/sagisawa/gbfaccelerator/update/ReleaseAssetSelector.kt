package com.sagisawa.gbfaccelerator.update

object ReleaseAssetSelector {

    fun select(assets: List<ReleaseAsset>): ReleaseAsset? {
        val apkAssets = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.isNotBlank()
        }
        if (apkAssets.isEmpty()) return null

        return apkAssets.firstOrNull { asset ->
            val name = asset.name.lowercase()
            name.contains("arm64") || name.contains("aarch64")
        } ?: apkAssets.firstOrNull { it.name.startsWith("GBF-Cache-", ignoreCase = true) }
            ?: apkAssets.first()
    }
}
