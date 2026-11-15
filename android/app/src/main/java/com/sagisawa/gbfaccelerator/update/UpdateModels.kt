package com.sagisawa.gbfaccelerator.update

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

data class LatestRelease(
    val version: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
    val apkAsset: ReleaseAsset?
)

sealed class UpdateCheckResult {
    data class Success(val release: LatestRelease) : UpdateCheckResult()
    data class Failure(val message: String) : UpdateCheckResult()
}
