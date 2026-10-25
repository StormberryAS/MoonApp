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
 * files: everything main asks for or declares, bar the launcher activity, must be removed by the
 * overlay. A permission or component added to main alone fails here, before it can reach a Play
 * bundle and a review gate.
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

    @Test fun theOverlayRemovesEveryComponentExceptTheLauncherActivity() {
        val launcher = ".MainActivity"
        val all = mainComponents()
        assertTrue(launcher in all)
        val removed = playRemovedComponents().toSet()
        for (component in all.filter { it != launcher }) {
            assertTrue("$component is declared in main but not removed for Play", component in removed)
        }
        assertFalse("the launcher activity must survive in the Play build", launcher in removed)
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
