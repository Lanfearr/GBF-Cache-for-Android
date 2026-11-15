package com.sagisawa.gbfaccelerator.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

sealed class ApkInstallResult {
    object Prompted : ApkInstallResult()
    data class Failure(val message: String, val cause: Throwable? = null) : ApkInstallResult()
}

class ApkInstaller(
    private val fileProviderAuthority: String? = null
) {

    fun install(context: Context, apkFile: File): ApkInstallResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            return ApkInstallResult.Failure("安装包不存在")
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            return ApkInstallResult.Failure("需要允许安装未知应用")
        }

        return try {
            val authority = fileProviderAuthority ?: "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            ApkInstallResult.Prompted
        } catch (e: Throwable) {
            ApkInstallResult.Failure("无法启动安装程序: ${e.message}", e)
        }
    }
}
