package com.sagisawa.gbfaccelerator.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.sagisawa.gbfaccelerator.R
import com.sagisawa.gbfaccelerator.update.ApkInstallResult
import com.sagisawa.gbfaccelerator.update.ApkInstaller
import com.sagisawa.gbfaccelerator.update.GitHubReleaseClient
import com.sagisawa.gbfaccelerator.update.LatestRelease
import com.sagisawa.gbfaccelerator.update.ReleaseAsset
import com.sagisawa.gbfaccelerator.update.UpdateCheckResult
import com.sagisawa.gbfaccelerator.update.UpdateDownloader
import com.sagisawa.gbfaccelerator.update.Versioning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AboutActivity : BasePageActivity() {

    private val projectUrl = "https://github.com/Lanfearr/GBF-Cache-for-Android"

    private lateinit var tvUpdateStatus: TextView
    private var currentVersionName = "?"
    private var currentVersionCode = 0L
    private var latestRelease: LatestRelease? = null
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var progressDialog: AlertDialog? = null
    private var pendingDownloadAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        setupPageHeader(R.string.title_about)
        bindVersion()
        bindUpdate()
        checkForUpdates(force = false)
    }

    override fun onResume() {
        super.onResume()
        if (pendingDownloadAfterPermission && canInstallPackages()) {
            pendingDownloadAfterPermission = false
            latestRelease?.apkAsset?.let { startDownload(it) }
        }
    }

    override fun onDestroy() {
        checkJob?.cancel()
        downloadJob?.cancel()
        progressDialog?.dismiss()
        progressDialog = null
        super.onDestroy()
    }

    private fun bindVersion() {
        val versionText = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            currentVersionName = info.versionName ?: "?"
            currentVersionCode = PackageInfoCompat.getLongVersionCode(info)
            getString(
                R.string.about_version_format,
                currentVersionName,
                currentVersionCode
            )
        } catch (e: Throwable) {
            "版本 ?"
        }
        findViewById<TextView>(R.id.tv_about_version).text = versionText
    }

    private fun bindUpdate() {
        tvUpdateStatus = findViewById(R.id.tv_update_status)
        findViewById<android.view.View>(R.id.btn_check_update).setOnClickListener {
            if (downloadJob?.isActive == true) return@setOnClickListener
            checkForUpdates(force = true)
        }
        findViewById<android.view.View>(R.id.btn_github).setOnClickListener {
            openUrl(projectUrl)
        }
        findViewById<android.view.View>(R.id.btn_licenses).setOnClickListener {
            startActivity(Intent(this, LicenseActivity::class.java))
        }
    }

    private fun checkForUpdates(force: Boolean) {
        if (checkJob?.isActive == true) return
        tvUpdateStatus.setText(R.string.about_update_checking)
        checkJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                GitHubReleaseClient.check(currentVersionName, force)
            }
            if (!isActive) return@launch
            when (result) {
                is UpdateCheckResult.Success -> handleRelease(result.release, force)
                is UpdateCheckResult.Failure -> {
                    latestRelease = null
                    tvUpdateStatus.setText(R.string.about_update_check_failed)
                    if (force) {
                        showMessageDialog(
                            R.string.about_update_check_error_title,
                            result.message
                        )
                    }
                }
            }
        }
    }

    private fun handleRelease(release: LatestRelease, manual: Boolean) {
        latestRelease = release
        if (Versioning.isNewer(release.version, currentVersionName)) {
            tvUpdateStatus.text = getString(R.string.about_update_available, release.version)
            showUpdateDialog(release)
        } else {
            tvUpdateStatus.setText(R.string.about_update_latest)
            if (manual) {
                Toast.makeText(this, R.string.about_update_latest, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(release: LatestRelease) {
        val notes = release.notes
            .trim()
            .ifEmpty { getString(R.string.about_update_no_notes) }
            .take(4000)
        val message = getString(
            R.string.about_update_dialog_message,
            currentVersionName,
            release.version,
            notes
        )
        val hasApk = release.apkAsset != null

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_update_dialog_title, release.version))
            .setMessage(message)
            .setPositiveButton(
                if (hasApk) R.string.about_update_download else R.string.about_update_open_release
            ) { _, _ ->
                val asset = release.apkAsset
                if (asset != null) {
                    startDownloadFlow(asset)
                } else {
                    openUrl(release.releaseUrl)
                }
            }
            .setNeutralButton(R.string.about_update_release_page) { _, _ ->
                openUrl(release.releaseUrl)
            }
            .setNegativeButton(R.string.about_update_later, null)
            .show()
    }

    private fun startDownloadFlow(asset: ReleaseAsset) {
        if (downloadJob?.isActive == true) return
        if (!canInstallPackages()) {
            pendingDownloadAfterPermission = true
            showInstallPermissionDialog()
            return
        }
        startDownload(asset)
    }

    private fun canInstallPackages(): Boolean {
        return packageManager.canRequestPackageInstalls()
    }

    private fun showInstallPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_update_install_permission_title)
            .setMessage(R.string.about_update_install_permission_message)
            .setPositiveButton(R.string.about_update_go_settings) { _, _ ->
                openUnknownSourcesSettings()
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                pendingDownloadAfterPermission = false
            }
            .show()
    }

    private fun openUnknownSourcesSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:$packageName".toUri()
                )
            )
        } catch (e: Throwable) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:$packageName".toUri()
                    )
                )
            } catch (fallbackError: Throwable) {
                Toast.makeText(
                    this,
                    getString(R.string.about_update_install_failed, fallbackError.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startDownload(asset: ReleaseAsset) {
        pendingDownloadAfterPermission = false

        val dialogView = layoutInflater.inflate(R.layout.dialog_update_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.pb_update_download)
        val percentText = dialogView.findViewById<TextView>(R.id.tv_update_download_percent)
        progressBar.isIndeterminate = true

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.about_update_downloading)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                downloadJob?.cancel()
            }
            .setCancelable(false)
            .create()
        dialog.show()
        progressDialog = dialog

        downloadJob = lifecycleScope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    UpdateDownloader.download(asset, File(filesDir, "updates")) { downloaded, total ->
                        runOnUiThread {
                            if (progressDialog === dialog && dialog.isShowing) {
                                if (total > 0) {
                                    val percent = ((downloaded * 100) / total)
                                        .toInt()
                                        .coerceIn(0, 100)
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = percent
                                    percentText.text = getString(
                                        R.string.about_update_download_progress,
                                        percent
                                    )
                                } else {
                                    progressBar.isIndeterminate = true
                                    percentText.setText(R.string.about_update_downloading)
                                }
                            }
                        }
                    }
                }

                validateDownloadedApk(apkFile)
                tvUpdateStatus.setText(R.string.about_update_installing)
                when (val result = ApkInstaller().install(this@AboutActivity, apkFile)) {
                    is ApkInstallResult.Prompted -> Unit
                    is ApkInstallResult.Failure -> {
                        tvUpdateStatus.setText(R.string.about_update_check_failed)
                        showMessageDialog(R.string.about_update_install_failed_title, result.message)
                    }
                }
            } catch (e: CancellationException) {
                tvUpdateStatus.setText(R.string.about_update_download_canceled)
                throw e
            } catch (e: Throwable) {
                tvUpdateStatus.setText(R.string.about_update_check_failed)
                showMessageDialog(
                    R.string.about_update_download_failed,
                    getString(
                        R.string.about_update_download_failed_message,
                        e.message ?: ""
                    )
                )
            } finally {
                if (progressDialog === dialog) {
                    dialog.dismiss()
                    progressDialog = null
                }
            }
        }
    }

    private fun validateDownloadedApk(apkFile: File) {
        val info = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        val packageMatches = info?.packageName == packageName
        val versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        if (!packageMatches || versionCode <= currentVersionCode) {
            apkFile.delete()
            throw IllegalStateException(getString(R.string.about_update_invalid_apk))
        }
    }

    private fun showMessageDialog(titleRes: Int, message: String) {
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.about_update_release_page) { _, _ ->
                openUrl(GitHubReleaseClient.RELEASES_PAGE_URL)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Throwable) {
            Toast.makeText(
                this,
                getString(R.string.about_update_open_failed, e.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
