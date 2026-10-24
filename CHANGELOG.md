# Changelog

Every released version of the MoonApp Android app, newest first. Each release is tagged `android-vX.Y.Z` and
its APK is on the [releases page](https://github.com/StormberryAS/MoonApp/releases). The short note shown in
the app store for a release is `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`; this file is
the full log it points to. Website changes (moon.stormberry.as) have no version number of their own; the ones
that ship with a release are listed under it.

## 1.0.5 (versionCode 6)

A new top and bottom for the times screen, in the manner of CheckLink. Nothing else changes: the moon
calendar, the alarms and the permissions are as in 1.0.4.

Top
- The mark, the name and the tagline are now centred, with the mark larger and glowing, and the date and
  the moon's figures follow. The tagline is the one on moon.stormberry.as, word for word.

Bottom
- The small footer is replaced by CheckLink's: the Stormberry name and tagline, what runs on your device
  and everything it is built on, the Stormberry icon row (LinkedIn, GitHub, Nostr, Tor), a line to test
  the privacy claim by disconnecting, "Alarm permissions only. No internet. No tracking.", then the
  company line with Privacy and Disclaimer, and the date of the city list.
- The credits are in the footer itself, each with a link to its source and to its licence: SunCalc's
  formulae (BSD 2-Clause), the Inter typeface (OFL 1.1), Android Jetpack and Kotlin (Apache 2.0),
  Google's Java desugaring library (GPL 2 with Classpath Exception), the Lucide LinkedIn and GitHub icons
  (ISC), and city data from GeoNames and Kartverket (CC BY 4.0). The licence texts are also in LICENSE.
- Every link opens in your browser, or in a Nostr client for the Nostr one. If your phone has no app for a
  link, MoonApp says so instead of closing. The app still has no INTERNET permission.
- The footer's small print was dark blue on near black, hard to read. It is now at a readable contrast.

Tests and build
- The permissions are unchanged. A new test pins every footer link, and another keeps the tagline equal to the website's.

## 1.0.4 (versionCode 5), 2026-10-23

Alarms that are harder to lose, and a corrected moon calendar. Brings over the alarm fixes made in
EclipseTracker and SunApp, adapted to MoonApp's simpler engine (no snooze, one notification), fixes three
alarm defects found only in MoonApp, and carries the Android side of the website's moon calendar fixes.

Alarms
- Android 12 with exact alarms switched off: in 1.0.3 nothing was armed at all. Alarms now fall back to
  inexact alarms, with a backup notification that rings with the phone's alarm sound, and are set again
  whenever MoonApp is opened. If exact alarms are also off and notifications are off as well, the backup
  ring cannot sound until notifications are on again.
- Notifications off or turned down: the alarm still rings, and opening MoonApp shows the screen that stops
  it. The alarms screen says which Android setting is limiting alarms, and it updates when you come back
  from Settings.
- A second alarm while one is ringing no longer leaves the first tone playing after Dismiss.
- Android 7 to 8.1: the tone now repeats until dismissed instead of playing once.
- If Android refuses to start the ringing service, the alarm rings through the backup notification instead
  of crashing.
- No double rings: an alarm that has rung does not ring again when the clock is set back, and a transit
  alarm can no longer ring twice within an hour, which happened a few times a year.
- The ring screen fits at font size 2.0 in landscape, where Dismiss used to fall off the screen, and it
  closes by itself when the alarm stops.
- New notification channel "Ringing alarms, backup", beside "MoonApp Lunar Alarms" in Android's settings.

Moon calendar (the times screen)
- The next new and full moon are dated in the place's own time zone. Before, one that fell between midnight
  and noon UTC of the chosen day could be skipped, so the next one, a month later, was shown, and one between
  noon and midnight UTC was dated a day late.
- Lunar transit: the real culmination, not the highest of a set of samples, and a day without one shows a
  dash, so no transit alarm rings for it. "Always Up" now uses the same 0.133 degree threshold as a rise.
- The day runs from its own first instant to the next day's, so it is 23 or 25 hours long on the days a
  time zone changes its offset.
- A date the clocks skipped (Apia, 30 December 2011) says it did not happen there.

Known limit
- SunCalc's model of the moon puts a new or full moon a median of about two hours, and at most about six
  hours, from the true event (checked against astronomy-engine 2.1.19). An event close to midnight can still
  be dated on the neighbouring day: 4 of the 50 events in 2026 and 2027. The website and the app share this.

Tests and build
- 52 unit tests in 11 classes, up from 21 in 5. The permissions are unchanged. The unsigned release build is
  reproducible: Debian's JDK 21 and Temurin 21 gave the same APK, in different checkout paths.

Website, shipped with this release
- The page code is written in TypeScript (`ts/app.ts`) and compiled to the `app.js` the page loads. Visitors
  see no difference from the TypeScript move itself. The test-data generators now run from the repository,
  and the site has Node tests, city search tests and a browser check.
- The moon calendar fixes above apply to the website too. `data/cities.tsv`, which the page never loads, is
  no longer published.
- An opt-in offline trial for the iPhone Home Screen: a service worker keeps a copy of the site, and it
  switches on only for visits to `https://moon.stormberry.as/?offline=1`.

## 1.0.3 (versionCode 4), 2026-10-10

- Uses the Inter typeface throughout the app, the same one the website uses. The fonts are bundled inside
  the app, so nothing is downloaded and the app still has no INTERNET permission.
- The alarm screen that appears when an alarm rings uses the same typeface as the rest of the app.
- On narrow phones, a long date such as the next new moon no longer runs into its label. The date wraps on
  the right.

## 1.0.2 (versionCode 3), 2026-09-05

- Privacy and Disclaimer are links in the footer, so the terms are reachable from inside the app rather than
  only from the website.
- The Stormberry logo in the footer opens stormberry.as.
- Opening a link hands the address to your browser. MoonApp still has no INTERNET permission and still
  cannot reach the network itself.

## 1.0.1 (versionCode 2), 2026-09-05

Repairs the first release. Testing 1.0.0 on a phone found the app wearing SunApp's icon and with no theme of
its own; an audit then confirmed 44 defects.

- The launcher icon was SunApp's sun. It is now MoonApp's crescent, drawn from the website's favicon, with a
  themed-icon layer.
- Typed text was unreadable on the dark cards, because the app declared no colour scheme. This included the
  city search and both coordinate fields.
- The header ran underneath the status bar and the last alarm's switch sat in the home-gesture strip. The
  app now applies system-bar insets.
- Alarms have their own screen: any of the five lunar events, an offset, a name, editing and deleting.
- An unanswered alarm now stops after ten minutes, keeps the screen on while it rings, and ignores the back
  gesture so the dismiss button cannot be swiped away.
- Typed coordinates took the phone's time zone instead of the place's; Sydney's coordinates on a Norwegian
  phone showed Oslo times.
- Moon times are computed over the place's own day rather than the UTC day.
- Added: pick any date; phase name, distance to the moon and the dates of the next new and full moon; days
  when the moon stays up or never rises are told apart; a first-run notice that this is a prototype and that
  the figures are computed rather than observed.

## 1.0.0 (versionCode 1), 2026-09-05

First release, on Zapstore. Moonrise, lunar transit and moonset for any location, from a bundled catalogue of
25,007 cities or typed coordinates, and one fixed alarm, 15 minutes before moonrise. It shipped with the
defects that 1.0.1 repaired.
