package no.stormberry.moonapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * What MoonApp says about itself before the first launch gets going.
 *
 * The disclaimer that governs every Stormberry app is in `DISCLAIMER.md`, and a document
 * nobody opens sets no expectations. MoonApp shipped v1.0.0 with the file in the repository
 * and nothing on screen, so an installed user was never told any of it. This dialog exists so
 * the three things a user would be entitled to be annoyed about later are things they
 * demonstrably saw first: that this is a prototype rather than an instrument, that its figures
 * are computed rather than observed, and that an Android alarm is best effort no matter how
 * correct the arithmetic behind it is.
 *
 * Same shape and same reasoning as SunApp's notice, deliberately. Two Stormberry apps that
 * make the same promise should make it in the same words.
 */

/**
 * Which revision of the notice text this build carries.
 *
 * Bump it only when the wording changes materially, because a bump re-shows the dialog to every
 * existing install, and a notice that reappears after a typo fix teaches people to dismiss it
 * unread. Starts at 1 because 0 is what a fresh install reads back when the key is absent.
 */
const val FIRST_RUN_NOTICE_VERSION = 1

/**
 * `<` rather than `!=` on purpose: an install can hold a version this build has never heard of,
 * by downgrading or restoring a backup, and the honest reading of that is "they have already
 * read a notice at least as complete as mine".
 */
fun shouldShowFirstRunNotice(
    seenVersion: Int,
    currentVersion: Int = FIRST_RUN_NOTICE_VERSION,
): Boolean = seenVersion < currentVersion

@Composable
fun FirstRunNotice(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        containerColor = Moon.Surface,
        titleContentColor = Moon.TextPrimary,
        textContentColor = Moon.TextSecondary,
        title = { Text("Before you start", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "MoonApp is a functioning prototype, published to show what Stormberry AS " +
                        "builds. It is not a certified instrument and not a professional service.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Every figure here is computed from published astronomical algorithms, not " +
                        "observed. The same arithmetic runs the website at moon.stormberry.as, " +
                        "and the Kotlin is tested against it, but a computed moonrise is still a " +
                        "prediction rather than a measurement.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "An Android alarm is best effort. Battery optimisation, Doze and " +
                        "manufacturer task killers can all delay or drop one, however exact the " +
                        "arithmetic behind it is. Do not rely on a lunar alarm for anything that " +
                        "matters on its own.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Check anything that matters against an authoritative source before you act " +
                        "on it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it", color = Moon.Silver) }
        },
    )
}
