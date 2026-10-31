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
  A separate package for Google Play (`-PplayBuild=true`), **without alarms**: the moon times, phase, calendar and home screen widgets, with no alarm screen, no alarm permission and no alarm component. The full version, with lunar alarms, is the sovereign package.

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
* **Home Screen Widgets:** three sizes (1x1, 2x1, 4x2), in both editions, with no new permission: today's moon with the lit percentage; today's moonrise, transit and moonset for the place chosen in the app; and the dates of the next new moon, first quarter, full moon and last quarter.
* **Stormberry Labs Carousel:** the website's carousel above the footer; each card opens its website in the browser, so the app still needs no internet permission.
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

### Google Play build (no alarms)

Alarms need `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT` and a foreground service of type `systemExempted`. Each is a Google Play review gate of its own, so the Play package ships without them. Two switches do it, and there are no product flavours (a flavour-level signing config is overridden by the build type's, and a flavour dimension renames every task and breaks the release workflow's APK paths):

* `android/app/src/play/AndroidManifest.xml` is merged over the main manifest under `-PplayBuild=true` and only removes: every alarm permission, the five alarm components, and androidx's private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. `PlayManifestTest` fails when main names a permission or component the overlay does not remove.
* `BuildConfig.PLAY_BUILD` is a compile-time constant. It hides the Alarms tab and every alarm line of text, and R8 drops the alarm code that nothing then reaches. The Play footer says "No permissions. No internet. No tracking."

The Play bundle has no `uses-permission` at all. The Play switch leaves the sovereign APK unchanged: the same permissions, components and screens, and `BuildConfig.PLAY_BUILD` is `false`.

```bash
# From android/, in a clean worktree of a sovereign release tag, with the Temurin JDK. The passwords come
# from the keyring and are never written to a file; --no-configuration-cache is required (the build refuses
# to run with an upload password set and the configuration cache on).
export PLAY_UPLOAD_STORE_FILE=$HOME/.android-keys/stormberry-moonapp-play-upload.p12 PLAY_UPLOAD_STORE_TYPE=pkcs12 PLAY_UPLOAD_KEY_ALIAS=upload
PLAY_UPLOAD_STORE_PASSWORD="$(secret-tool lookup service android-keystore app moonapp-play-upload)" \
PLAY_UPLOAD_KEY_PASSWORD="$(secret-tool lookup service android-keystore app moonapp-play-upload)" \
  ./gradlew --no-daemon --no-build-cache --no-configuration-cache :app:bundleRelease -PplayBuild=true
# app/build/outputs/bundle/release/moonapp-play-release.aab
```

Two guards sit on the release tasks, because the failure modes are permanent: `bundleRelease` without the flag fails (that bundle would carry the sovereign application ID signed with the release key, and uploading it would claim that package on Play for good), and `assembleRelease` with the flag fails (Play takes the bundle). A third refuses to configure when the upload keystore and the release keystore are the same file.

**Two number lines, one codebase.** The Play versionName is the sovereign one and the Play versionCode is 1000 plus the sovereign code. Every Play upload is built from a sovereign tag that shipped on GitHub Releases; never from an untagged tree.

| Sovereign tag | Sovereign version | Play version | Notes |
|---|---|---|---|
| `android-v1.0.6` | 1.0.6 (code 7) | not published | the last release before the Play switch |
| `android-v1.0.7` | 1.0.7 (code 8) | 1.0.7 (code 1008) | the first Play bundle: no alarms |
| `android-v1.0.8` | 1.0.8 (code 9) | 1.0.8 (code 1009) | home screen widgets |

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

### Third-party projects, data, icons and fonts
The app and the website credit on screen only the projects and data the moon calculation is built on:
[SunCalc](https://github.com/mourner/suncalc) (BSD 2-Clause), and the city data from
[GeoNames](https://www.geonames.org/) and [Kartverket](https://www.kartverket.no/) (CC BY 4.0).
The footer texts of the two are the same, word for word; `tests/static.test.ts` reads the app's wording from the
Kotlin source and fails if either side is edited alone.
Everything else the project uses is credited here and in [LICENSE](LICENSE), which carries the notices:

| Part | Used for | Licence |
|---|---|---|
| [SunCalc](https://github.com/mourner/suncalc) | The moon formulae (`suncalc.js`, and the Kotlin port in `lunar/MoonCalc.kt`) | BSD 2-Clause |
| [GeoNames](https://www.geonames.org/) and [Kartverket](https://www.kartverket.no/) | The city catalogue (`cities.js`, `data/cities.tsv`) | CC BY 4.0 |
| [Inter](https://rsms.me/inter/) | The typeface of the website and the app | SIL OFL 1.1 |
| [Lucide](https://lucide.dev/) | The LinkedIn and GitHub icons | ISC (portions MIT) |
| [Kotlin](https://kotlinlang.org/) and [Android Jetpack](https://developer.android.com/jetpack) | The Android app: language, Compose, Material 3, AndroidX | Apache 2.0 |
| [desugar_jdk_libs](https://github.com/google/desugar_jdk_libs) | `java.time` on Android 7 and 8 | GPL 2 with Classpath Exception |

## Disclaimer
Supplied free of charge, **as is**, with no warranty of any kind. Full terms in [DISCLAIMER.md](DISCLAIMER.md).
