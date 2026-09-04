# MoonApp

Sovereign, privacy-first lunar calculator and astronomical alarm system. A direct sibling to [SunApp](https://sun.stormberry.as), covering moonrise, moonset, lunar phase, distance, upcoming lunations, and exact offline lunar alarms on Android.

**Live Web App:** [moon.stormberry.as](https://moon.stormberry.as)  
**Suggested CNAME:** `moon.stormberry.as`

---

## Android APK Distribution & Packages

MoonApp ships in two distinct package editions:

* **Zapstore / Sovereign APK:** `no.stormberry.moonapp`  
  Direct, sovereign release signed with the Stormberry AS release key. Zero trackers, zero analytics, zero Google Play Services dependencies.
* **Google Play Store Build:** `no.stormberry.moonapp.play`  
  Separate package name specifically for Google Play distribution (`-PplayBuild=true`).

---

## Features

### Web Application (`index.html`)
* **Moonrise, Lunar Transit, Moonset:** Computed offline in the correct local timezone.
* **Phase Disc:** Rendered canvas graphic showing the exact illuminated lunar face.
* **Moon Distance:** Kilometres from Earth at the selected date.
* **Lunation Calculator:** Next new moon and full moon dates calculated client-side.
* **Offline City Search:** Autocomplete for global cities.

### Android APK Specific (`moonapp.apk`)
* **Lunar Alarm Engine:** Wake up or receive alerts based on celestial lunar movements:
  * **Moonrise Alarms:** Set alerts relative to moonrise (e.g. 15 minutes before moonrise).
  * **Moonset Alarms:** Alerts relative to moonset.
  * **Lunar Meridian Transit / Peak Alarms:** Trigger when the moon reaches its highest point.
  * **Full Moon & New Moon Night Alarms:** Automatic alerts on lunation nights.
* **Daily Auto-Recompute:** Recalculates exact alarm trigger timestamps every day as the moon's schedule shifts (~50 mins per day).
* **System Event Recovery:** Automatically re-arms alarms on device reboot (`BOOT_COMPLETED`), timezone change, or clock adjustment.

---

## Android Permission Inventory

| Permission | Purpose |
|---|---|
| `USE_EXACT_ALARM` | Fires exact alarms at scheduled minute on Android 13+ |
| `SCHEDULE_EXACT_ALARM` | Supports exact alarm scheduling on Android 12 (API 31–32) |
| `POST_NOTIFICATIONS` | Displays alarm notifications on Android 13+ |
| `RECEIVE_BOOT_COMPLETED` | Re-arms lunar alarms after device reboot |
| `WAKE_LOCK` | Holds CPU awake briefly during alarm fire transition |
| `VIBRATE` | Haptic feedback during alarm ringing |
| `USE_FULL_SCREEN_INTENT` | Displays full-screen ring activity over lock screen |
| `FOREGROUND_SERVICE` | Keeps alarm audio service running without OS termination |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ media playback foreground service type |

> **Sovereign Privacy Guarantee:** `INTERNET`, `ACCESS_FINE_LOCATION`, and `ACCESS_COARSE_LOCATION` are explicitly removed from `moonapp.apk`.

---

## Architecture & Stack

* **Web Front-End:** Vanilla HTML, CSS, JS with zero build step.
* **Android Native App:** Kotlin with Jetpack Compose UI and native `AlarmManager` integration.
* **Calculation Engine:** Astronomical moon algorithms following the published SunCalc 1.8.0
  formulae. The web app uses SunCalc 1.8.0 itself (`suncalc.js`); the Android app carries an
  independent Kotlin implementation of the same formulae in `lunar/MoonCalc.kt`. They are not
  yet parity-tested against each other: a golden-vector test in the shape of SunApp's
  `SunCalcGoldenTest` is still to be written, and the claim of parity should not be made until
  it exists.

---

## Credits
Built by [Stormberry AS](https://stormberry.as). Proudly powered by sovereign AI agents.

## Disclaimer
Supplied free of charge, **as is**, with no warranty of any kind. Full terms in [DISCLAIMER.md](DISCLAIMER.md).
