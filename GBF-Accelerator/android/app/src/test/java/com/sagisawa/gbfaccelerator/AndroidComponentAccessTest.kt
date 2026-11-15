package com.sagisawa.gbfaccelerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Static verification of the component access boundary declared in AndroidManifest.xml.
 *
 * Only the launcher activity and the system-bound Quick Settings tile service may be reachable from
 * other apps. The core service and the control receiver are driven exclusively by explicit in-app
 * intents, so they must stay unexported.
 */
class AndroidComponentAccessTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun manifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")
    }

    private fun components(tag: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(manifestFile())
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun attribute(element: Element, name: String): String? =
        element.getAttributeNS(androidNs, name).ifEmpty { null }

    private fun component(tag: String, name: String): Element {
        val matches = components(tag).filter { attribute(it, "name") == name }
        assertEquals("expected exactly one <$tag> named $name", 1, matches.size)
        return matches.single()
    }

    @Test
    fun coreServiceIsNotExported() {
        val service = component("service", "com.sagisawa.gbfaccelerator.core.CoreService")
        assertEquals(
            "CoreService must not be reachable from other apps",
            "false",
            attribute(service, "exported"),
        )
    }

    @Test
    fun coreControlReceiverIsNotExported() {
        val receiver = component("receiver", "com.sagisawa.gbfaccelerator.core.CoreControlReceiver")
        assertEquals(
            "CoreControlReceiver must not be reachable from other apps",
            "false",
            attribute(receiver, "exported"),
        )
    }

    @Test
    fun launcherActivityStaysExported() {
        val activity = component("activity", "com.sagisawa.gbfaccelerator.ui.MainActivity")
        assertEquals(
            "MainActivity must stay exported so the desktop launcher can open it",
            "true",
            attribute(activity, "exported"),
        )

        val actions = (0 until activity.getElementsByTagName("action").length)
            .map { activity.getElementsByTagName("action").item(it) as Element }
            .mapNotNull { attribute(it, "name") }
        assertTrue(
            "MainActivity must keep the MAIN/LAUNCHER entry point",
            actions.contains("android.intent.action.MAIN"),
        )
    }

    @Test
    fun launcherActivityDeclaresOnlyLauncherAndTilePreferenceFilters() {
        val activity = component("activity", "com.sagisawa.gbfaccelerator.ui.MainActivity")
        val actions = (0 until activity.getElementsByTagName("action").length)
            .map { activity.getElementsByTagName("action").item(it) as Element }
            .mapNotNull { attribute(it, "name") }
            .toSet()
        assertEquals(
            "MainActivity may only expose the launcher and Quick Settings preference actions",
            setOf(
                "android.intent.action.MAIN",
                "android.service.quicksettings.action.QS_TILE_PREFERENCES",
            ),
            actions,
        )
    }

    @Test
    fun quickSettingsTileIsProtectedBySystemPermission() {
        val service = component("service", "com.sagisawa.gbfaccelerator.ui.ProxyTileService")
        assertEquals(
            "ProxyTileService must be exported for the system Quick Settings host",
            "true",
            attribute(service, "exported"),
        )
        assertEquals(
            "ProxyTileService must only be bindable by the system tile host",
            "android.permission.BIND_QUICK_SETTINGS_TILE",
            attribute(service, "permission"),
        )

        val actions = (0 until service.getElementsByTagName("action").length)
            .map { service.getElementsByTagName("action").item(it) as Element }
            .mapNotNull { attribute(it, "name") }
            .toSet()
        assertEquals(
            "ProxyTileService must expose only the Quick Settings tile action",
            setOf("android.service.quicksettings.action.QS_TILE"),
            actions,
        )
    }

    @Test
    fun onlyTheLauncherAndTileServiceAreExported() {
        val exported = listOf("activity", "service", "receiver", "provider").flatMap { tag ->
            components(tag)
                .filter { attribute(it, "exported") == "true" }
                .map { "$tag:${attribute(it, "name")}" }
        }
        assertEquals(
            "only the launcher activity and the system tile service may be exported",
            listOf(
                "activity:com.sagisawa.gbfaccelerator.ui.MainActivity",
                "service:com.sagisawa.gbfaccelerator.ui.ProxyTileService",
            ),
            exported,
        )
    }

    @Test
    fun manifestDeclaresBrowserDiscoveryQuery() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(manifestFile())
        val intents = doc.getElementsByTagName("intent")

        val hasBrowserQuery = (0 until intents.length).any { index ->
            val intent = intents.item(index) as Element
            val actions = (0 until intent.getElementsByTagName("action").length)
                .map { intent.getElementsByTagName("action").item(it) as Element }
                .mapNotNull { attribute(it, "name") }
            val categories = (0 until intent.getElementsByTagName("category").length)
                .map { intent.getElementsByTagName("category").item(it) as Element }
                .mapNotNull { attribute(it, "name") }
            val schemes = (0 until intent.getElementsByTagName("data").length)
                .map { intent.getElementsByTagName("data").item(it) as Element }
                .mapNotNull { attribute(it, "scheme") }

            actions.contains("android.intent.action.VIEW") &&
                categories.contains("android.intent.category.BROWSABLE") &&
                schemes.contains("https")
        }

        assertTrue(
            "Android 11+ browser discovery requires a VIEW/BROWSABLE/https query",
            hasBrowserQuery,
        )
    }
}
