package com.sagisawa.gbfaccelerator.ui

import android.os.Bundle
import android.widget.TextView
import com.sagisawa.gbfaccelerator.R

class LicenseActivity : BasePageActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        setupPageHeader(R.string.title_license)

        val builder = StringBuilder()
        appendSection(builder, "组件归属", readAsset("NOTICE"))
        appendSection(builder, "GBF-Accelerator - MIT", readAsset("licenses/GBF-Accelerator-MIT.txt"))
        appendSection(
            builder,
            "Apache License 2.0 - AndroidX / Kotlin / kotlinx / purego",
            readAsset("licenses/Apache-2.0.txt")
        )
        appendSection(builder, "mihomo - GPL-3.0", readAsset("licenses/mihomo.txt"))
        appendSection(builder, "GNU General Public License v3.0", readRaw(R.raw.gpl_3_0))

        findViewById<TextView>(R.id.tv_license_text).text = builder.toString().trimEnd()
    }

    private fun appendSection(builder: StringBuilder, title: String, body: String) {
        if (builder.isNotEmpty()) {
            builder.append("\n\n")
        }
        builder.append(title).append('\n')
        builder.append("=".repeat(72)).append("\n\n")
        builder.append(body.trim())
    }

    private fun readAsset(path: String): String =
        runCatching {
            assets.open(path).bufferedReader().use { it.readText() }
        }.getOrElse { "Failed to load $path: ${it.message}" }

    private fun readRaw(resId: Int): String =
        runCatching {
            resources.openRawResource(resId).bufferedReader().use { it.readText() }
        }.getOrElse { "Failed to load license resource: ${it.message}" }
}
