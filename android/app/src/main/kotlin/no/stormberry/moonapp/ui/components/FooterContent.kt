package no.stormberry.moonapp.ui.components

/**
 * Where each footer link goes, in one place with no Android in it, so a plain JUnit test can
 * pin every target (FooterLinksTest).
 *
 * The icon row and the Stormberry links are CheckLink's, address for address, copied from the
 * footer of CheckLink/index.html. Privacy and Disclaimer are the two pages the 1.0.4 footer
 * already opened: the app has no screen of its own for either, so both open in the browser.
 */
object FooterLinks {
    const val SITE = "https://stormberry.as"
    const val LINKEDIN = "https://linkedin.com/company/StormberryAS"
    const val GITHUB = "https://github.com/StormberryAS"
    const val NOSTR = "nostr:npub1zz9w77p2jkn95t0st9tcuea8zdn2vknf0ctrwyd4g46w3kswdccqesr0pa"
    const val ONION = "http://stormberryr7fz5xdopi7f265d7id27bgizc6uxeliaepzstompcfpyd.onion"
    const val ORG_NR = "https://virksomhet.brreg.no/nb/oppslag/enheter/937751249"

    // Unchanged from 1.0.4.
    const val PRIVACY = "https://stormberry.as/privacy.html"
    const val DISCLAIMER = "https://moon.stormberry.as/disclaimer.html"

    // Credits: every third-party part the APK ships, each with where it comes from and the licence
    // it comes under. SunCalc and GeoNames are the two the web footer credits; the rest are what
    // the APK adds (the Inter typeface, the Jetpack, Kotlin and desugaring libraries, the Lucide
    // LinkedIn and GitHub icons, and Kartverket's names for a few Askoy places). The licence texts sit in LICENSE, under
    // "Third-Party Notices".
    const val SUNCALC = "https://github.com/mourner/suncalc"
    const val SUNCALC_LICENCE = "https://github.com/mourner/suncalc/blob/master/LICENSE"
    const val INTER = "https://rsms.me/inter/"
    const val OFL = "https://openfontlicense.org/open-font-license-official-text/"
    const val JETPACK = "https://developer.android.com/jetpack"
    const val KOTLIN = "https://kotlinlang.org/"
    const val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"
    const val DESUGAR = "https://github.com/google/desugar_jdk_libs"
    const val GPL_CPE = "https://openjdk.org/legal/gplv2+ce.html"
    const val LUCIDE = "https://lucide.dev/"
    const val LUCIDE_LICENCE = "https://lucide.dev/license"
    const val GEONAMES = "https://www.geonames.org/"
    const val KARTVERKET = "https://www.kartverket.no/"
    const val CC_BY = "https://creativecommons.org/licenses/by/4.0/"

    /** Every link the footer can open, for the test. */
    val ALL = listOf(
        SITE, LINKEDIN, GITHUB, NOSTR, ONION, ORG_NR, PRIVACY, DISCLAIMER,
        SUNCALC, SUNCALC_LICENCE, INTER, OFL, JETPACK, KOTLIN, APACHE, DESUGAR, GPL_CPE,
        LUCIDE, LUCIDE_LICENCE, GEONAMES, KARTVERKET, CC_BY,
    )
}

/** The footer's sentences. */
object FooterText {
    /** CheckLink's, verbatim. */
    const val TAGLINE = "Smarter sales. Global culture. AI-driven strategy."

    /** CheckLink's, verbatim. */
    const val PRIVACY_FIRST = "Privacy First: We do not collect cookies or personal data."

    /** CheckLink's, verbatim. */
    const val AI = "Proudly powered by sovereign AI agents."

    /** CheckLink's, with no-break spaces so the number never breaks inside itself. */
    const val ORG_NR = "Org.nr. NO 937 751 249"

    /**
     * Approved by Marcos, 2026-10-23. CheckLink's line is "Your links and pictures never leave
     * this device. Disconnect and try it." This is the same sentence for what MoonApp keeps: the
     * places and alarms a person sets. The test is real: the APK has no INTERNET permission, so
     * it works the same with the radio off.
     */
    const val OFFLINE = "Your places and alarms never leave this device. Disconnect and try it."

    /** The line the footer carried before 1.0.5; Marcos asked for it back, 2026-10-23. Verbatim. */
    const val PERMISSIONS = "Alarm permissions only. No internet. No tracking."

    /**
     * The date of the bundled city list, as CheckLink dates its rules and its domain list.
     * The file carries no date of its own, so this is the date `data/cities.tsv` was
     * last committed (2026-09-04, the Android port). Move it when the list is regenerated.
     */
    const val CITY_LIST_DATE = "2026-09-04"
    const val CITY_LIST_DATED = "City list dated $CITY_LIST_DATE"
}
