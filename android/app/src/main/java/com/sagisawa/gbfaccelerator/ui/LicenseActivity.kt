package com.sagisawa.gbfaccelerator.ui

import android.os.Bundle
import android.widget.TextView
import com.sagisawa.gbfaccelerator.R

class LicenseActivity : BasePageActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        setupPageHeader(R.string.title_license)

        val licenseText = runCatching {
            resources.openRawResource(R.raw.gpl_3_0).bufferedReader().use { it.readText() }
        }.getOrElse { "Failed to load license text: ${it.message}" }

        findViewById<TextView>(R.id.tv_license_text).text = licenseText
    }
}
