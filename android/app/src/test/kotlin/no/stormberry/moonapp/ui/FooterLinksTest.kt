package no.stormberry.moonapp.ui

import java.io.File
import no.stormberry.moonapp.ui.components.FooterLinks
import no.stormberry.moonapp.ui.components.FooterText
import no.stormberry.moonapp.ui.components.HERO_TAGLINE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The footer's links, pinned. A footer is the part of an app nobody tests by hand twice, and
 * a wrong address in it is a wrong address in front of every user.
 */
class FooterLinksTest {

    @Test fun theIconRowIsCheckLinksAddressForAddress() {
        assertEquals("https://stormberry.as", FooterLinks.SITE)
        assertEquals("https://linkedin.com/company/StormberryAS", FooterLinks.LINKEDIN)
        assertEquals("https://github.com/StormberryAS", FooterLinks.GITHUB)
        assertEquals(
            "nostr:npub1zz9w77p2jkn95t0st9tcuea8zdn2vknf0ctrwyd4g46w3kswdccqesr0pa",
            FooterLinks.NOSTR,
        )
        assertEquals(
            "http://stormberryr7fz5xdopi7f265d7id27bgizc6uxeliaepzstompcfpyd.onion",
            FooterLinks.ONION,
        )
        assertEquals(
            "https://virksomhet.brreg.no/nb/oppslag/enheter/937751249",
            FooterLinks.ORG_NR,
        )
    }

    @Test fun privacyAndDisclaimerOpenTheSamePagesAs104() {
        assertEquals("https://stormberry.as/privacy.html", FooterLinks.PRIVACY)
        assertEquals("https://moon.stormberry.as/disclaimer.html", FooterLinks.DISCLAIMER)
    }

    @Test fun everyLinkOpensInAnotherApp() {
        // The app has no INTERNET permission, so a link must leave through an intent, which
        // means a scheme another app can take: https, plain http for the onion, or nostr.
        val allowed = setOf("https", "http", "nostr")
        for (url in FooterLinks.ALL) {
            val scheme = url.substringBefore(':')
            assertTrue("$url has an unexpected scheme", scheme in allowed)
            assertFalse("$url must not contain a space", url.contains(' '))
        }
        // Plain http only for the onion address, which has no TLS certificate to speak of.
        val plainHttp = FooterLinks.ALL.filter { it.startsWith("http://") }
        assertEquals(listOf(FooterLinks.ONION), plainHttp)
        assertTrue(FooterLinks.ONION.endsWith(".onion"))
    }

    @Test fun theNostrAddressIsAnNpubOfTheRightLength() {
        val id = FooterLinks.NOSTR.removePrefix("nostr:")
        assertTrue(id.startsWith("npub1"))
        assertEquals(63, id.length)
    }

    @Test fun orgNumberReadsAsOneUnbreakableRun() {
        assertFalse(FooterText.ORG_NR.contains(' '))
        assertEquals("Org.nr. NO 937 751 249", FooterText.ORG_NR.replace(' ', ' '))
    }

    @Test fun cityListDateIsAnIsoDate() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(FooterText.CITY_LIST_DATE))
        assertEquals("City list dated ${FooterText.CITY_LIST_DATE}", FooterText.CITY_LIST_DATED)
    }

    @Test fun copyHasNoEmDashAndNoDoubleHyphen() {
        val copy = listOf(
            HERO_TAGLINE, FooterText.TAGLINE, FooterText.OFFLINE, FooterText.PERMISSIONS,
            FooterText.PRIVACY_FIRST, FooterText.AI, FooterText.CITY_LIST_DATED,
        )
        for (c in copy) {
            assertFalse(c, c.contains('—'))
            assertFalse(c, c.contains("--"))
        }
    }

    @Test fun theOfflineLineAndThePermissionsLineAreVerbatim() {
        assertEquals(
            "Your places and alarms never leave this device. Disconnect and try it.",
            FooterText.OFFLINE,
        )
        assertEquals("Alarm permissions only. No internet. No tracking.", FooterText.PERMISSIONS)
    }

    @Test fun theFooterCreditsOnlyTheProjectsAndDataTheFunctionIsBuiltOn() {
        // (source, licence) for each credit. SunCalc is the formulae the moon is computed with;
        // GeoNames and Kartverket are the place names and coordinates it is computed for.
        val credits = mapOf(
            "SunCalc" to (FooterLinks.SUNCALC to FooterLinks.SUNCALC_LICENCE),
            "GeoNames" to (FooterLinks.GEONAMES to FooterLinks.CC_BY),
            "Kartverket" to (FooterLinks.KARTVERKET to FooterLinks.CC_BY),
        )
        for ((part, pair) in credits) {
            assertTrue("$part source is not linked", pair.first in FooterLinks.ALL)
            assertTrue("$part licence is not linked", pair.second in FooterLinks.ALL)
        }
        assertEquals(
            "https://creativecommons.org/licenses/by/4.0/",
            FooterLinks.CC_BY,
        )
    }

    @Test fun iconsLanguagesPlatformLibrariesAndTheTypefaceAreNotInTheFooter() {
        // Marcos, 2026-10-24: these are credited in LICENSE and README only. Any link to one of
        // them in the footer is the excess he removed.
        val notInTheFooter = listOf(
            "lucide", "rsms.me", "openfontlicense", "kotlinlang", "developer.android.com",
            "apache.org", "desugar", "openjdk",
        )
        for (url in FooterLinks.ALL) {
            for (word in notInTheFooter) {
                assertFalse("$url credits $word in the footer", url.contains(word))
            }
        }
        // The same check on the footer's source, so a link added without a constant is caught.
        val chrome = findInRepo("android/app/src/main/kotlin/no/stormberry/moonapp/ui/components/Chrome.kt")
        val credits = chrome.readText().substringAfter("Runs entirely on your device")
            .substringBefore("Nothing is sent.")
        for (word in listOf("Inter", "Lucide", "Kotlin", "Jetpack", "desugaring", "Material", "OFL")) {
            assertFalse("the credits sentence names $word", credits.contains(word))
        }
        assertTrue(credits.contains("SunCalc") && credits.contains("GeoNames") && credits.contains("Kartverket"))
    }

    @Test fun eachLicenceLabelIsOneUnbreakableRun() {
        for (label in listOf(FooterText.LICENCE_BSD, FooterText.LICENCE_CC_BY)) {
            assertFalse("$label has a breaking space", label.contains(' '))
        }
        assertEquals("BSD 2-Clause", FooterText.LICENCE_BSD.replace('\u00A0', ' '))
        assertEquals("CC BY 4.0", FooterText.LICENCE_CC_BY.replace('\u00A0', ' '))
    }

    private fun findInRepo(path: String): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")

    @Test fun theFooterNeverAsksForTheInternetPermission() {
        // expected-permissions.txt is what CI diffs against the built APK; links open in
        // another app and need nothing. Guard the one line that would break the claim.
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "android/expected-permissions.txt") }
            .plus(generateSequence(File("").absoluteFile) { it.parentFile }
                .map { File(it, "expected-permissions.txt") })
            .firstOrNull { it.exists() }
        assertTrue("expected-permissions.txt not found", file != null)
        assertFalse(file!!.readLines().any { it.trim() == "android.permission.INTERNET" })
    }
}
