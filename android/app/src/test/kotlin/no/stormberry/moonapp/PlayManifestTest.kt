package no.stormberry.moonapp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The Google Play build has no alarms, and this is the test that keeps it so.
 *
 * `src/play/AndroidManifest.xml` is merged over `src/main/AndroidManifest.xml` under
 * `-PplayBuild=true` and does nothing but remove. So the guarantee is a relation between two
 * files: everything main asks for or declares, bar the launcher activity and the four home screen
 * widget receivers (1.0.8, which need no permission), must be removed by the overlay. A
 * permission or component added to main alone fails here, before it can reach a Play bundle and
 * a review gate.
 *
 * The test reads the two source manifests; the merged manifest of the real bundle is checked by
 * the release notes (aapt2 on the bundle: no uses-permission, no alarm component).
 */
class PlayManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val toolsNs = "http://schemas.android.com/tools"

    private fun manifest(rel: String): Element {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "app/$rel"), File(it, "android/app/$rel"), File(it, rel)) }
            .firstOrNull { it.exists() } ?: error("$rel not found above the working directory")
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return f.newDocumentBuilder().parse(file).documentElement
    }

    private val main = manifest("src/main/AndroidManifest.xml")
    private val play = manifest("src/play/AndroidManifest.xml")

    private fun Element.kids(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.name(): String = getAttributeNS(androidNs, "name")
    private fun Element.node(): String = getAttributeNS(toolsNs, "node")

    private val components = listOf("activity", "service", "receiver", "provider")

    private fun Element.application(): Element = kids("application").single()

    private fun mainPermissions(): List<String> =
        main.kids("uses-permission").filter { it.node() != "remove" }.map { it.name() }

    private fun mainComponents(): List<String> =
        components.flatMap { main.application().kids(it) }.map { it.name() }

    private fun playRemovedPermissions(): List<String> =
        play.kids("uses-permission").filter { it.node() == "remove" }.map { it.name() }

    private fun playRemovedComponents(): List<String> =
        components.flatMap { play.application().kids(it) }.filter { it.node() == "remove" }.map { it.name() }

    @Test fun theOverlayRemovesEveryPermissionMainDeclares() {
        val declared = mainPermissions()
        assertTrue("main declares no permission: the test is reading the wrong file", declared.isNotEmpty())
        val removed = playRemovedPermissions().toSet()
        for (permission in declared) {
            assertTrue("$permission is declared in main but not removed for Play", permission in removed)
        }
    }

    /**
     * What the Play build keeps: the launcher activity and, since 1.0.8, the home screen widgets
     * (three providers and the receiver that redraws them), which need no permission.
     */
    private val keptForPlay = setOf(
        ".MainActivity",
        ".widget.MoonWidgetSmall",
        ".widget.MoonWidgetMedium",
        ".widget.MoonWidgetLarge",
        ".widget.WidgetEventReceiver",
    )

    private val widgetProviders = listOf(".widget.MoonWidgetSmall", ".widget.MoonWidgetMedium", ".widget.MoonWidgetLarge")

    private fun mainComponent(name: String): Element =
        components.flatMap { main.application().kids(it) }.single { it.name() == name }

    @Test fun theOverlayRemovesEveryComponentExceptTheLauncherActivityAndTheWidgets() {
        val all = mainComponents()
        val removed = playRemovedComponents().toSet()
        for (kept in keptForPlay) {
            assertTrue("$kept must be declared in main", kept in all)
            assertFalse("$kept must survive in the Play build", kept in removed)
        }
        for (component in all.filter { it !in keptForPlay }) {
            assertTrue("$component is declared in main but not removed for Play", component in removed)
        }
    }

    @Test fun theWidgetsNeedNoPermission() {
        // Actions only a receiver holding a permission may get; none may reach a kept receiver.
        val needPermission = setOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
        for (name in keptForPlay.filter { it.startsWith(".widget.") }) {
            val receiver = mainComponent(name)
            assertEquals("$name is a receiver", "receiver", receiver.tagName)
            assertEquals("$name must not require a permission", "", receiver.getAttributeNS(androidNs, "permission"))
            val actions = receiver.kids("intent-filter").flatMap { it.kids("action") }.map { it.name() }
            for (a in actions) assertFalse("$name listens for $a, which needs a permission", a in needPermission)
        }
        // And the main manifest still asks for exactly the nine alarm permissions.
        assertEquals(
            setOf(
                "android.permission.USE_EXACT_ALARM", "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.WAKE_LOCK", "android.permission.VIBRATE",
                "android.permission.USE_FULL_SCREEN_INTENT", "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
            ),
            mainPermissions().toSet(),
        )
        assertEquals(9, mainPermissions().size)
    }

    /**
     * No widget receiver is exported (the system's broadcasts reach them anyway), so no other app
     * can drive a refresh loop; and the clock receiver is off until a widget is placed, so the
     * system never starts the app for TIME_SET when there is no widget.
     */
    @Test fun theWidgetReceiversAreNotExportedAndTheClockReceiverStartsDisabled() {
        for (name in keptForPlay.filter { it.startsWith(".widget.") }) {
            assertEquals("$name must not be exported", "false", mainComponent(name).getAttributeNS(androidNs, "exported"))
        }
        assertEquals(
            ".widget.WidgetEventReceiver must be disabled in the manifest",
            "false",
            mainComponent(".widget.WidgetEventReceiver").getAttributeNS(androidNs, "enabled"),
        )
        for (name in widgetProviders) {
            assertEquals("$name must be enabled", "", mainComponent(name).getAttributeNS(androidNs, "enabled"))
        }
    }

    @Test fun everyWidgetProviderHasItsInfo() {
        for (name in widgetProviders) {
            val receiver = mainComponent(name)
            val meta = receiver.kids("meta-data").singleOrNull { it.name() == "android.appwidget.provider" }
            assertTrue("$name has no android.appwidget.provider meta-data", meta != null)
            assertTrue("$name points at no provider info", meta!!.getAttributeNS(androidNs, "resource").startsWith("@xml/widget_"))
            val actions = receiver.kids("intent-filter").flatMap { it.kids("action") }.map { it.name() }
            assertEquals("$name filters APPWIDGET_UPDATE only", listOf("android.appwidget.action.APPWIDGET_UPDATE"), actions)
        }
    }

    @Test fun theOverlayDeclaresNothingAndRemovesOnlyWhatExists() {
        // Every child of the overlay is a removal.
        for (tag in listOf("uses-permission", "permission")) {
            for (e in play.kids(tag)) assertEquals("${e.name()} is not a removal", "remove", e.node())
        }
        for (c in components) {
            for (e in play.application().kids(c)) assertEquals("${e.name()} is not a removal", "remove", e.node())
        }
        // No stale removal: each named permission and component is in main (or is a library's, listed here).
        val fromLibraries = setOf("\${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val mainAll = main.kids("uses-permission").map { it.name() }.toSet() + fromLibraries
        for (name in playRemovedPermissions()) assertTrue("$name is removed for Play but main does not name it", name in mainAll)
        val mainComponentNames = mainComponents().toSet()
        for (name in playRemovedComponents()) assertTrue("$name is removed for Play but main does not declare it", name in mainComponentNames)
    }

    @Test fun theAlarmPermissionsThatNeedAPlayReviewAreRemoved() {
        // The three Play review gates the Play build exists to avoid.
        val removed = playRemovedPermissions().toSet()
        for (gate in listOf(
            "android.permission.USE_EXACT_ALARM",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
        )) assertTrue(gate, gate in removed)
    }

    @Test fun theOverlayDoesNotRestoreTheInternet() {
        // INTERNET stays removed in main and the overlay must never ask for it.
        for (e in play.kids("uses-permission")) assertEquals("remove", e.node())
        assertTrue(main.kids("uses-permission").any { it.name() == "android.permission.INTERNET" && it.node() == "remove" })
    }

    @Test fun theBothDynamicReceiverEntriesAreRemovedForPlay() {
        val permission = "\${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        assertTrue(play.kids("permission").any { it.name() == permission && it.node() == "remove" })
        assertTrue(play.kids("uses-permission").any { it.name() == permission && it.node() == "remove" })
    }
}
