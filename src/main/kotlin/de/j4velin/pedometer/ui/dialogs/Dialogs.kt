/*
 * Copyright 2026 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.pedometer.ui.dialogs

import android.text.Spanned
import android.text.style.URLSpan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.j4velin.pedometer.R
import de.j4velin.pedometer.data.Settings
import de.j4velin.pedometer.ui.Formats
import de.j4velin.pedometer.ui.overview.Statistics
import java.text.DateFormat

/** Test tags of the dialogs' parts */
object DialogTags {
    const val MESSAGE = "message"
    const val RECORD = "record"
    const val TOTAL_WEEK = "totalthisweek"
    const val AVERAGE_WEEK = "averagethisweek"
    const val TOTAL_MONTH = "totalthismonth"
    const val AVERAGE_MONTH = "averagethismonth"
    const val SPLIT_STEPS = "split_steps"
    const val SPLIT_DISTANCE = "split_distance"
    const val SPLIT_UNIT = "split_unit"
    const val SPLIT_START_STOP = "split_start"
}

/** A message with an OK button */
@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        text = { Text(message, Modifier.testTag(DialogTags.MESSAGE)) },
    )
}

@Composable
fun StatisticsDialog(statistics: Statistics, onDismiss: () -> Unit) {
    val format = Formats.number()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Figure(
                    format.format(statistics.recordSteps) + " @ " +
                        DateFormat.getDateInstance().format(statistics.recordDate),
                    stringResource(R.string.record), DialogTags.RECORD
                )
                Figure(
                    format.format(statistics.thisWeek),
                    stringResource(R.string.total_last_7_days), DialogTags.TOTAL_WEEK
                )
                Figure(
                    format.format(statistics.thisWeek / 7),
                    stringResource(R.string.average_last_7_days), DialogTags.AVERAGE_WEEK
                )
                Figure(
                    format.format(statistics.thisMonth),
                    stringResource(R.string.total_this_month), DialogTags.TOTAL_MONTH
                )
                Figure(
                    format.format(statistics.thisMonth / statistics.daysThisMonth),
                    stringResource(R.string.average_this_month), DialogTags.AVERAGE_MONTH
                )
            }
        },
    )
}

@Composable
private fun Figure(value: String, label: String, tag: String) {
    Column {
        Text(
            value, Modifier.testTag(tag), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Counts the steps from when the user started it. Starting closes the dialog; stopping keeps it
 * open and shows that no split counter is running.
 *
 * @param totalSteps all steps taken so far
 */
@Composable
fun SplitDialog(settings: Settings, totalSteps: Int, onDismiss: () -> Unit) {
    var active by remember { mutableStateOf(settings.splitDate > 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_count)) },
        text = {
            if (active) {
                val steps = totalSteps - (settings.splitSteps ?: totalSteps)
                val metric = settings.stepUnit == "cm"
                val distance = steps * settings.stepSize / if (metric) 100000 else 5280
                val format = Formats.number()
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(format.format(steps), Modifier.testTag(DialogTags.SPLIT_STEPS), fontSize = 50.sp)
                    Text(stringResource(R.string.steps))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            format.format(distance), Modifier.testTag(DialogTags.SPLIT_DISTANCE),
                            fontSize = 50.sp
                        )
                        Text(
                            if (metric) "km" else "mi",
                            Modifier.padding(start = 8.dp, bottom = 12.dp).testTag(DialogTags.SPLIT_UNIT)
                        )
                    }
                    Text(
                        stringResource(
                            R.string.since,
                            DateFormat.getDateTimeInstance().format(settings.splitDate)
                        ),
                        Modifier.padding(top = 15.dp)
                    )
                }
            } else {
                Text(stringResource(R.string.no_split_active))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (active) {
                        settings.stopSplit()
                        active = false
                    } else {
                        settings.startSplit(System.currentTimeMillis(), totalSteps)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag(DialogTags.SPLIT_START_STOP),
            ) {
                Text(stringResource(if (active) R.string.stop else R.string.start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
    val versionText = stringResource(R.string.about_app_version, version ?: "")
    val text = remember(resources, versionText) {
        buildAnnotatedString {
            append(linkified(resources.getText(R.string.about_text_links)))
            append(versionText)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about)) },
        text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

/** A string resource with <a href> links, as tappable links */
private fun linkified(text: CharSequence): AnnotatedString = buildAnnotatedString {
    append(text.toString())
    if (text is Spanned) {
        text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
            addLink(
                LinkAnnotation.Url(
                    span.url,
                    TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
                ),
                text.getSpanStart(span), text.getSpanEnd(span)
            )
        }
    }
}
