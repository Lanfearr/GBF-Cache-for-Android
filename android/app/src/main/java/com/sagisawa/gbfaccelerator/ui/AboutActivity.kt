package com.sagisawa.gbfaccelerator.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import com.sagisawa.gbfaccelerator.R

class AboutActivity : BasePageActivity() {

    private val projectUrl = "https://github.com/Lanfearr/GBF-Cache-for-Android"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        setupPageHeader(R.string.title_about)
        bindVersion()
        findViewById<android.view.View>(R.id.btn_github).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(projectUrl)))
            } catch (e: Throwable) {
                Toast.makeText(this, "无法打开浏览器: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<android.view.View>(R.id.btn_licenses).setOnClickListener {
            startActivity(Intent(this, LicenseActivity::class.java))
        }
    }

    private fun bindVersion() {
        val versionText = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            getString(
                R.string.about_version_format,
                info.versionName ?: "?",
                PackageInfoCompat.getLongVersionCode(info)
            )
        } catch (e: Throwable) {
            "版本 ?"
        }
        findViewById<TextView>(R.id.tv_about_version).text = versionText
    }
}
