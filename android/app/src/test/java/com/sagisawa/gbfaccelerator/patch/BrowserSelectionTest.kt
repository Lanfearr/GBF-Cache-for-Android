package com.sagisawa.gbfaccelerator.patch

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserSelectionTest {

    private fun browser(packageName: String) = InstalledBrowser(packageName, packageName)

    @Test
    fun firstRunSelectsAllInstalledBrowsers() {
        val installed = listOf(browser("com.example.alpha"), browser("com.example.beta"))

        assertEquals(
            setOf("com.example.alpha", "com.example.beta"),
            BrowserCatalog.resolveSelection(installed, null)
        )
    }

    @Test
    fun explicitEmptySelectionRemainsEmpty() {
        val installed = listOf(browser("com.example.alpha"))

        assertEquals(emptySet<String>(), BrowserCatalog.resolveSelection(installed, emptySet()))
    }

    @Test
    fun storedSelectionDropsUninstalledPackages() {
        val installed = listOf(browser("com.example.alpha"))

        assertEquals(
            setOf("com.example.alpha"),
            BrowserCatalog.resolveSelection(
                installed,
                setOf("com.example.alpha", "com.example.removed")
            )
        )
    }

    @Test
    fun targetsUsePackageAsIdentity() {
        val target = BrowserCatalog.toTargets(listOf(browser("com.example.browser"))).single()

        assertEquals("com.example.browser", target.id)
        assertEquals("com.example.browser", target.name)
        assertEquals(setOf("com.example.browser"), target.targetPackages)
    }
}
