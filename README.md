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

Every released version, and what changed in it, is in [CHANGELOG.md](CHANGELOG.md).

---

## Features

### Web Application (`index.html`)
* **Moonrise, Lunar Transit, Moonset:** Computed offline in the correct local timezone.
* **Phase Disc:** Rendered canvas graphic showing the exact illuminated lunar face.
* **Moon Distance:** Kilometres from Earth at the selected date.
* **Lunation Calculator:** Next new moon and full moon dates calculated client-side.
* **Offline City Search:** Autocomplete for global cities.
* **Typed Coordinates:** Any point on the globe by latitude and longitude. A decimal comma works as well as a point (`60,39` or `60.39`), and so does a typographic minus; anything out of range is refused with a message rather than computed.
* **No Location Request:** The page never asks for the device's location. A place comes from city search or from typed coordinates.

### Android APK Specific (`moonapp.apk`)
* **Lunar Alarm Engine:** Wake up or receive alerts based on celestial lunar movements:
  * **Moonrise Alarms:** Set alerts relative to moonrise (e.g. 15 minutes before moonrise).
  * **Moonset Alarms:** Alerts relative to moonset.
  * **Lunar Meridian Transit / Peak Alarms:** Trigger when the moon reaches its highest point.
  * **Full Moon & New Moon Night Alarms:** Automatic alerts on lunation nights.
* **Daily Auto-Recompute:** Recalculates exact alarm trigger timestamps every day as the moon's schedule shifts (~50 mins per day).
* **System Event Recovery:** Automatically re-arms alarms on device reboot (`BOOT_COMPLETED`), timezone change, or clock adjustment.
* **Alarms that are hard to lose:** an alarm rings even with MoonApp's notifications switched off or turned down, and opening MoonApp then shows the screen that stops it; the alarms screen says which Android setting is limiting them. On Android 12 with exact alarms switched off, alarms still ring, perhaps late, through a backup notification with the phone's alarm sound. If notifications are also off, the alarm cannot sound until they are on again. One alarm rings at a time, and setting the clock back does not ring the same alarm twice.

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
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Android 14+ foreground service type for an alarm clock |

> **Sovereign Privacy Guarantee:** `INTERNET`, `ACCESS_FINE_LOCATION`, and `ACCESS_COARSE_LOCATION` are explicitly removed from `moonapp.apk`.

---

## Architecture & Stack

* **Web Front-End:** Vanilla HTML, CSS and TypeScript, no frameworks and no runtime dependencies. The page code is written in TypeScript (`ts/app.ts`) and compiled to the plain `app.js` the page loads, which is committed, so GitHub Pages serves the repository as it is with no build step of its own.
* **Android Native App:** Kotlin with Jetpack Compose UI and native `AlarmManager` integration.
* **Calculation Engine:** Astronomical moon algorithms following the published SunCalc 1.8.0
  formulae. The web app uses SunCalc 1.8.0 itself (`suncalc.js`); the Android app carries an
  independent Kotlin implementation of the same formulae in `lunar/MoonCalc.kt`. The Kotlin
  side is held to the library by golden corpora written from `suncalc.js` itself
  (`MoonCalcGoldenTest`, `MoonTimesGoldenTest`, and `MoonTimesWebParityTest` for the page's
  city-local day), and `tools/gen-*.ts --check` proves the corpora still match the vendored file.
  The next new and full moon, the lunar transit, the polar day labels and city search
  are also held to the page itself, by corpora written from the compiled `app.js`
  (`LunationParityTest`, `TransitParityTest`, `LunarDayKindParityTest`, `CitySearchParityTest`).

---

## TypeScript, build and tests

The page code is TypeScript: edit `ts/app.ts`, never `app.js`. `tools/build-ts.sh` compiles it
with the pinned TypeScript 6.0.3 (strict) into `app.js`, a classic script like before, and then
runs `bump_assets.py` so the page loads the new file under a fresh `?v=` stamp. `app.js` is
committed, because GitHub Pages publishes the repository as it is; `.gitattributes` marks it as
generated. The build refuses to overwrite an `app.js` edited by hand. `suncalc.js` (SunCalc 1.8.0,
vendored, byte for byte) and `cities.js` (written by SunApp's `update_cities.py`) are not compiled;
their declarations live in `ts/`.

Needs Node 24 and Python 3. The tests are TypeScript too, run by Node directly.

```sh
tools/build-ts.sh            # npm ci (frozen, install scripts off), type-check, compile ts/ to app.js, stamp the page
tools/build-ts.sh --check    # change nothing; fail if app.js or a stamp is stale, the city data disagree,
                             # or an Android test corpus is not what its generator writes
npm test                     # unit tests: page helpers, city search, golden corpora, city parity, the TypeScript rule
npm run smoke                # the page in headless Chromium, against snapshots of the 90d11dd page with its fixes
npm run sw-smoke             # the offline pilot's service worker in headless Chromium, under the real host name
node tools/gen-golden.ts [--check]             # (re)write the Android illumination corpus
node tools/gen-moontimes-golden.ts [--check]   # (re)write the Android moonrise and moonset corpus (UTC day)
node tools/gen-webparity-golden.ts [--check]   # (re)write the corpus of the page's city-local day
node tools/gen-lunar-parity.ts [--check]       # (re)write the page's lunation, transit and polar-day corpora
node tools/gen-search-parity.ts [--check]      # (re)write the page's city search corpus
```

`package.json`, `package-lock.json`, `.npmrc`, `tsconfig.json`, `tsconfig.sw.json`, `ts/`, `tests/`, `tools/` and
`node_modules/` are development files only: `_config.yml` keeps them off the site, and the Android
build copies only `data/cities.tsv`.

### Offline pilot (iPhone Home Screen)
A service worker (`ts/sw.ts`, compiled to `sw.js`) lets the site open with no network after one online visit, for the Home Screen web app on iPhone. It registers only on `https://moon.stormberry.as`, never on the onion mirror, on `localhost` or on any other host, and the Android app never loads the web page at all. While `OFFLINE_PILOT` in `ts/app.ts` is `'opt-in'`, only a visit to `https://moon.stormberry.as/?offline=1` installs it. Pages are fetched from the network first and fall back to the stored copy only when there is no network; scripts and stylesheets are stored under their `?v=` stamps, which `bump_assets.py` also writes into `sw.js`, so every release replaces the stored files. To switch it off, set `OFFLINE_PILOT` to `'off'` (every device drops it on its next visit) or `KILL` in `ts/sw.ts` to `true`, then build and deploy. Once the worker has shipped, never delete `sw.js` and never revert the pilot as a whole: a device keeps its worker while `sw.js` answers 404, and with `offlinePilot()` gone from `app.js` neither switch can reach it. To end the pilot, deploy `OFFLINE_PILOT = 'off'` and `KILL = true` together and keep both served for at least three months (a Home Screen app can go unopened for weeks); only then remove the code. If `sw.js` was removed by mistake, serving a `sw.js` with `KILL = true` again clears the worker at each device's next visit.

---

## Credits
Built by [Stormberry AS](https://stormberry.as). Proudly powered by sovereign AI agents.

## Disclaimer
Supplied free of charge, **as is**, with no warranty of any kind. Full terms in [DISCLAIMER.md](DISCLAIMER.md).
