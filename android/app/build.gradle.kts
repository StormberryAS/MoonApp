import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Signing credentials configuration for release vs debug builds.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

fun signingValue(propKey: String, gradleKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: providers.gradleProperty(gradleKey).orNull

val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
val canSignRelease = releaseStoreFile != null && file(releaseStoreFile).exists()

// Google Play is a SEPARATE package, deliberately (the pattern UsernameGenerator opened on
// 2026-09-03 and MetadataScrubber followed). `no.stormberry.moonapp` stays the sovereign build:
// our key, our certificate, shipped through GitHub Releases and Zapstore, with the NIP-C1
// certificate link vouching for it. `-PplayBuild=true` builds `no.stormberry.moonapp.play`
// instead, which Google re-signs with an app signing key it holds. Two package names are two
// apps to Android: they install side by side and neither can ever update the other, so nothing
// that happens on Play can reach a Zapstore install.
//
// THE PLAY BUILD HAS NO ALARMS (Marcos, 2026-10-25: "SunApp and MoonApp without alarms").
// Alarms need USE_EXACT_ALARM, USE_FULL_SCREEN_INTENT and a foreground service of type
// systemExempted, each of which is a Play review gate of its own (a declaration form, a video,
// an "up to several weeks" extended review). The Play package therefore ships without every
// alarm permission, component and screen. Two switches do it, and there are no product flavours
// (a flavour-level signingConfig is overridden by the build type's, and a flavour dimension
// renames every task and breaks the workflow's APK paths and the Zapstore regex):
//   1. src/play/AndroidManifest.xml, merged over the main manifest under -PplayBuild=true, removes
//      the alarm permissions and components with tools:node="remove";
//   2. BuildConfig.PLAY_BUILD, a compile-time constant, hides the Alarms tab and every alarm
//      line of text, and R8 then drops the alarm code it can no longer reach.
// PlayManifestTest pins the first against the main manifest, so an alarm permission added later
// cannot reach Play unnoticed.
val playBuild = when (providers.gradleProperty("playBuild").orNull?.lowercase()) {
    null -> false
    // A bare `-PplayBuild` arrives as an empty string. Treating that as false would hand back
    // a sovereign artefact from a command that plainly meant Play.
    "", "true", "1", "yes" -> true
    "false", "0", "no" -> false
    else -> error(
        "-PplayBuild=${providers.gradleProperty("playBuild").orNull} is not a recognised " +
            "value. Use -PplayBuild=true, or omit the property for the sovereign build.",
    )
}

// The Play upload key is a separate key with a separate job: it proves who uploaded the bundle
// and never reaches a device, so it must never be the release key. If it is ever compromised it
// can be reset from the Play Console without touching a single install. keystore.properties (or
// the PLAY_UPLOAD_* Gradle properties, or the environment) names the file, type and alias; the two
// passwords come from the keyring and are never written into a file.
fun playSigningValue(propKey: String, gradleKey: String): String? =
    keystoreProperties.getProperty(propKey)
        ?: providers.gradleProperty(gradleKey).orNull
        ?: providers.environmentVariable(gradleKey).orNull

val uploadStoreFile = playSigningValue("playUploadStoreFile", "PLAY_UPLOAD_STORE_FILE")
val uploadStoreType = playSigningValue("playUploadStoreType", "PLAY_UPLOAD_STORE_TYPE")
val uploadStorePassword = playSigningValue("playUploadStorePassword", "PLAY_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = playSigningValue("playUploadKeyAlias", "PLAY_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = playSigningValue("playUploadKeyPassword", "PLAY_UPLOAD_KEY_PASSWORD")
val canSignPlayUpload = uploadStoreFile != null && file(uploadStoreFile).exists() &&
    uploadStorePassword != null && uploadKeyAlias != null && uploadKeyPassword != null

// The two keys must be two different files. A Play upload config pointing at the release
// keystore (or a byte-for-byte copy of it) would enrol the sovereign certificate in Play App
// Signing, which cannot be undone. Refused before anything is built.
if (releaseStoreFile != null && uploadStoreFile != null) {
    val rel = file(releaseStoreFile)
    val upl = file(uploadStoreFile)
    if (rel.exists() && upl.exists() &&
        (rel.canonicalFile == upl.canonicalFile || rel.readBytes().contentEquals(upl.readBytes()))
    ) {
        error(
            "The Play upload keystore and the release keystore are the same key " +
                "($uploadStoreFile). The release key signs the Zapstore APK only and must never " +
                "reach Play; the upload key signs the Play bundle only.",
        )
    }
}

// The upload passwords are read while Gradle configures the build, and gradle.properties turns
// the configuration cache on. A cached configuration stores those values under
// android/.gradle/configuration-cache, which would write the password to disk. So the build
// refuses to configure, before any cache entry exists, whenever an upload password is set and
// the cache is in use. (The release passwords are not covered: they keep their existing flow.)
abstract class BuildFeaturesHolder @Inject constructor(val features: org.gradle.api.configuration.BuildFeatures)

val configurationCacheRequested =
    objects.newInstance<BuildFeaturesHolder>().features.configurationCache.requested.getOrElse(false)
if (configurationCacheRequested && (uploadStorePassword != null || uploadKeyPassword != null)) {
    error(
        "A Play upload password is set, so this build must run with --no-configuration-cache; " +
            "otherwise Gradle would store it in its configuration cache.",
    )
}

// Guards on the RESOLVED TASK GRAPH, not on the command line, because the command line lies:
// `build`, `assemble` and Gradle's `bR` abbreviation reach a release artefact without containing
// "bundleRelease" or "assembleRelease". packageRelease (APK) and signReleaseBundle (AAB) are the
// two tasks that consume a signing config, so the checks belong on them.
//
// The dangerous mistake is the one with no flag: `bundleRelease` on its own would write an AAB
// carrying the SOVEREIGN application ID signed with the release key. Uploading it would
// permanently claim no.stormberry.moonapp on Play and enrol the release certificate in Play App
// Signing, which cannot be undone.
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    val isBundle = name == "signReleaseBundle"
    val play = playBuild
    val haveUploadKey = canSignPlayUpload
    doFirst {
        if (isBundle && !play) {
            error(
                "Refusing to build a release bundle without -PplayBuild=true. This AAB would " +
                    "carry the sovereign application ID signed with the release key, and " +
                    "uploading it would permanently claim that package on Play. The sovereign " +
                    "channel ships APKs; Play takes the bundle, with -PplayBuild=true.",
            )
        }
        if (!isBundle && play) {
            error(
                "Refusing to build a release APK with -PplayBuild=true. Play takes the app " +
                    "bundle: run :app:bundleRelease -PplayBuild=true. An installable .play APK " +
                    "has no destination and must never be mistaken for the Zapstore one.",
            )
        }
        if (isBundle && play && !haveUploadKey) {
            error(
                "playBuild=true but the Play upload keystore is not fully configured. " +
                    "playUploadStoreFile and playUploadKeyAlias (android/keystore.properties, or " +
                    "PLAY_UPLOAD_STORE_FILE and PLAY_UPLOAD_KEY_ALIAS) and the passwords " +
                    "PLAY_UPLOAD_STORE_PASSWORD and PLAY_UPLOAD_KEY_PASSWORD (from the keyring) " +
                    "must all be set, and the store file must exist.",
            )
        }
    }
}

// The city catalogue is generated by SunApp/update_cities.py and shared with the web app
// and every sibling Labs app. It is copied into assets at build time rather than checked
// in twice, because two copies would drift.
val cityAssetsDir = layout.buildDirectory.dir("generated/assets/cities")

kotlin {
    // The box default is a JDK 25 JRE with no compiler. Pin the toolchain so the
    // build does not depend on whatever JAVA_HOME happens to be. Byte reproduction
    // additionally needs Temurin 21.0.12.1+1; see android/README.md.
    jvmToolchain(21)
}

val playVersionCodeOffset = 1000

android {
    namespace = "no.stormberry.moonapp"
    compileSdk = 37

    defaultConfig {
        // Sovereign build: no.stormberry.moonapp
        // Google Play build: no.stormberry.moonapp.play
        applicationId = if (playBuild) "no.stormberry.moonapp.play" else "no.stormberry.moonapp"
        minSdk = 24
        targetSdk = 36

        // Version history of the sovereign line (the Play line is derived from it, see below).
        //
        // Sovereign 1.0.0 (code 1): the first Zapstore release. Nothing has shipped yet, so
        // this is a clean start rather than a continuation.
        //
        // Sovereign 1.0.1 (code 2): the v1.0.0 UI repair. v1.0.0 shipped SunApp's launcher
        // icon, no theme (so Material 3 resolved its baseline LIGHT palette and typed text was
        // unreadable), no window insets, no alarms screen, and an alarm that rang until the
        // battery died. See fastlane changelogs/2.txt.
        //
        // Sovereign 1.0.2 (code 3): the footer. Privacy and Disclaimer links, and the
        // Stormberry lockup made tappable, in step with the same change to SunApp.
        //
        // Sovereign 1.0.3 (code 4), 2026-10-10: Inter. The app is set in the typeface
        // moon.stormberry.as self-hosts, bundled in res/font, plus one layout fix it forced
        // (a long date no longer runs into its label on a 360 dp phone). Code 4 is free
        // here: Play is a different package and has never been released.
        //
        // Sovereign 1.0.4 (code 5), October 2026: alarms that are harder to lose (EclipseTracker
        // fix B as ported to SunApp 1.1.4: a backup ring when Android will not let the service
        // ring, the Android 12 backstop, the ring screen from the app, one tone at a time, the
        // tone looping on Android 7 and 8) and the moon calendar the website fixed with it (the
        // next new and full moon on the right day, the transit as the culmination, one transit
        // alarm per transit). See fastlane changelogs/5.txt.
        //
        // Sovereign 1.0.5 (code 6), October 2026: the times screen opens with a centred hero and
        // ends with CheckLink's footer, in place of the SunApp-style header and small footer.
        // No permission changes. See fastlane changelogs/6.txt.
        //
        // Sovereign 1.0.6 (code 7), October 2026: the footer credits only the projects and data the
        // app's function is built on (SunCalc, GeoNames, Kartverket). The icons, the typeface and
        // the languages and libraries are credited in LICENSE and README only. No permission
        // changes. See fastlane changelogs/7.txt.
        //
        // Sovereign 1.0.7 (code 8), October 2026: the Play switch. `-PplayBuild=true` builds the
        // Google Play package, which has no alarms (see playBuild above). The sovereign APK keeps
        // every permission, component and screen it had; BuildConfig.PLAY_BUILD is false in it.
        // See fastlane changelogs/8.txt.
        //
        // ONE VISIBLE VERSION, TWO CODE LINES (the MetadataScrubber scheme, Marcos, 2026-10-05:
        // "1.0.0 for both"). The sovereign versionCode and versionName on the next lines are the only
        // numbers bumped by hand. The Play build derives its code from them and shows the same name:
        //   Play versionCode = 1000 + sovereign versionCode   (1.0.7 code 8 -> code 1008)
        //   Play versionName = sovereign versionName          (1.0.7 -> 1.0.7)
        // Play needs monotonic codes within its own package only; the offset keeps them distinct
        // from the sovereign line and rising with it. The package name and the signing key are what
        // keep the two apart, not the version. Every Play upload is built from a sovereign release
        // tag that shipped on GitHub Releases (the mapping table is in README.md): never
        // from an untagged tree.
        versionCode = 8
        versionName = "1.0.7"
        if (playBuild) {
            versionCode = playVersionCodeOffset + versionCode!!
        }

        // True only under -PplayBuild=true; read by the UI, never by the manifest.
        buildConfigField("boolean", "PLAY_BUILD", playBuild.toString())

        vectorDrawables.generatedDensities()

    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        if (canSignPlayUpload) {
            create("playUpload") {
                storeFile = file(uploadStoreFile!!)
                if (uploadStoreType != null) storeType = uploadStoreType
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                // v1 stays ON here, unlike the release config above. An app bundle is signed
                // like a JAR, and this signature exists only so Play can verify who uploaded it:
                // Play strips it and re-signs the split APKs it generates with the app signing
                // key it holds. Leaving v1 on costs nothing on an artefact no device ever installs.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The Play build never touches the release key and the sovereign build never touches
            // the upload key. Both stay conditional so a clean checkout with no keystore can still
            // run assembleDebug and an unsigned assembleRelease.
            if (playBuild) {
                if (canSignPlayUpload) signingConfig = signingConfigs.getByName("playUpload")
            } else if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    sourceSets["main"].assets.directories.add(cityAssetsDir.get().asFile.absolutePath)

    buildFeatures {
        compose = true
        // BuildConfig.PLAY_BUILD only (the other generated fields are constants nothing reads).
        buildConfig = true
    }

    // The Play manifest overlay (see playBuild above). Attached to both build types so that
    // `assembleDebug -PplayBuild=true` shows the same manifest as the bundle; the release APK with
    // the flag is refused by the guard above, so the bundle is the only Play artefact.
    if (playBuild) {
        sourceSets["release"].manifest.srcFile("src/play/AndroidManifest.xml")
        sourceSets["debug"].manifest.srcFile("src/play/AndroidManifest.xml")
    }

    compileOptions {
        // java.time is API 26 and minSdk is 24, so without desugaring the app throws
        // NoClassDefFoundError on API 24 and 25. assembleRelease succeeds either way;
        // only lint catches it, which is why the release pipeline must run lint.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        // Strips the Google-signed dependency blob from the APK. It is encrypted, it is not
        // reproducible, and it was the one thing in the APK that stopped anyone rebuilding
        // from the android-v1.0.0 tag matching the SHA-256 the release workflow publishes
        // beside it. For an app whose whole claim is "nothing leaves the device", an opaque
        // metadata block is also the wrong look. SunApp has carried this since 2026-08-21.
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // Ship-blocking rather than advisory: the desugaring gap above is only visible here.
        abortOnError = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

// Under -PplayBuild=true the artefact is named for what it is, so a Play bundle can never be
// mistaken for a sovereign one on disk. Deliberately NOT set for the sovereign build, whose
// filename app-release.apk is hardcoded in .github/workflows/android-release.yml.
if (playBuild) {
    base { archivesName = "moonapp-play" }
}

val copyCityData by tasks.registering(Copy::class) {
    description = "Copies the packed city catalogue from the repo root into app assets."
    group = "build"
    from(rootProject.file("../data")) {
        include("cities.tsv")
    }
    into(cityAssetsDir.map { it.dir("data") })
    // Deterministic ordering, so the APK is byte-reproducible across machines.
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.named("preBuild") { dependsOn(copyCityData) }

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    // Plain JUnit only. The lunar maths, the occurrence engine and the city search are all
    // pure functions or take a Reader, so none of them needs a device or Robolectric.
    testImplementation(libs.junit)
}
