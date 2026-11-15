package com.sagisawa.gbfaccelerator.patch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import com.sagisawa.gbfaccelerator.core.AppPreferences

data class InstalledBrowser(
    val packageName: String,
    val label: String
)

object BrowserCatalog {

    private val httpsIntent = Intent(Intent.ACTION_VIEW, "https://".toUri()).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }

    fun discover(context: Context): List<InstalledBrowser> {
        val packageManager = context.packageManager
        val found = linkedMapOf<String, InstalledBrowser>()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                httpsIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(httpsIntent, PackageManager.MATCH_ALL)
        }

        resolved.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.applicationInfo?.packageName ?: return@forEach
            addPackage(context, found, packageName)
        }
        BrowserTargets.skyLeap.targetPackages.forEach { packageName ->
            addPackage(context, found, packageName)
        }

        return found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun addPackage(
        context: Context,
        found: MutableMap<String, InstalledBrowser>,
        packageName: String
    ) {
        if (packageName == context.packageName || found.containsKey(packageName)) return

        val packageManager = context.packageManager
        val appInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        if (packageManager.getLaunchIntentForPackage(packageName) == null) return

        val label = packageManager.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        found[packageName] = InstalledBrowser(packageName, label)
    }

    fun toTargets(browsers: List<InstalledBrowser>): List<BrowserTarget> =
        browsers.map { browser ->
            BrowserTarget(
                id = browser.packageName,
                name = browser.label,
                targetPackages = setOf(browser.packageName)
            )
        }

    internal fun resolveSelection(
        installed: List<InstalledBrowser>,
        stored: Set<String>?
    ): Set<String> {
        val installedPackages = installed.mapTo(linkedSetOf()) { it.packageName }
        return if (stored == null) installedPackages else stored.intersect(installedPackages)
    }

    fun resolveSelectedPackages(
        context: Context,
        installed: List<InstalledBrowser> = discover(context)
    ): Set<String> {
        val stored = AppPreferences.getSelectedBrowserPackages(context)
        val resolved = resolveSelection(installed, stored)
        if (stored == null) {
            AppPreferences.setSelectedBrowserPackages(context, resolved)
        }
        return resolved
    }

    fun saveSelectedPackages(context: Context, packages: Set<String>) {
        AppPreferences.setSelectedBrowserPackages(context, packages)
    }
}
