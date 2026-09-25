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

package de.j4velin.pedometer.ui.overview

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.j4velin.pedometer.R
import de.j4velin.pedometer.ui.theme.HistoryBlue
import de.j4velin.pedometer.ui.theme.PedometerTheme
import de.j4velin.pedometer.ui.theme.StepsGreen

/** Test tags of the overview's parts */
object OverviewTags {
    const val RING = "ring"
    const val STEPS = "steps"
    const val UNIT = "unit"
    const val AVERAGE = "average"
    const val TOTAL = "total"
    const val BARS = "bars"
}

/**
 * The overview, updating live while resumed.
 *
 * @param onBarsClick opens the statistics
 * @param onNoSensor the device has no step counter and the user closed the explanation
 */
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onBarsClick: () -> Unit,
    onNoSensor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleResumeEffect(viewModel) {
        viewModel.resume()
        onPauseOrDispose { viewModel.pause() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Overview(state, viewModel::toggleStepsAndDistance, onBarsClick, modifier)

    if (state.noSensor) {
        AlertDialog(
            onDismissRequest = onNoSensor,
            confirmButton = {
                TextButton(onClick = onNoSensor) { Text(stringResource(android.R.string.ok)) }
            },
            title = { Text(stringResource(R.string.no_sensor)) },
            text = { Text(stringResource(R.string.no_sensor_explain)) },
        )
    }
}

@Composable
private fun Overview(
    state: OverviewState,
    onRingClick: () -> Unit,
    onBarsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(modifier.fillMaxSize().padding(5.dp)) {
            Ring(state, onRingClick, Modifier.padding(top = 10.dp).size(200.dp))
            Column(Modifier.weight(1f).fillMaxSize()) {
                AverageAndTotal(state, Modifier.padding(top = 10.dp))
                Bars(state, onBarsClick, Modifier.weight(1f))
            }
        }
    } else {
        Column(modifier.fillMaxSize().padding(5.dp)) {
            Ring(
                state, onRingClick,
                Modifier.padding(top = 10.dp).size(200.dp).align(Alignment.CenterHorizontally)
            )
            AverageAndTotal(state, Modifier.padding(top = 20.dp))
            Spacer(Modifier.height(50.dp))
            Bars(state, onBarsClick, Modifier.height(150.dp))
        }
    }
}

/** Today's steps or distance in the ring; a click switches between the two */
@Composable
private fun Ring(state: OverviewState, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.clickable(onClick = onClick).testTag(OverviewTags.RING), Alignment.Center) {
        StepsRing(state.stepsToday, state.goal, Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.today, Modifier.testTag(OverviewTags.STEPS), fontSize = 45.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                state.unit, Modifier.testTag(OverviewTags.UNIT), fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AverageAndTotal(state: OverviewState, modifier: Modifier) {
    Row(modifier.fillMaxWidth()) {
        Figure(state.average, stringResource(R.string.average), OverviewTags.AVERAGE, Modifier.weight(1f))
        Figure(state.total, stringResource(R.string.total), OverviewTags.TOTAL, Modifier.weight(1f))
    }
}

@Composable
private fun Figure(value: String, label: String, tag: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, Modifier.testTag(tag), fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The last week; hidden while there are no days with steps yet */
@Composable
private fun Bars(state: OverviewState, onClick: () -> Unit, modifier: Modifier) {
    if (state.bars.isEmpty()) return
    WeekBars(
        state.bars,
        modifier.fillMaxWidth().clickable(onClick = onClick).testTag(OverviewTags.BARS)
    )
}

private val previewState = OverviewState(
    stepsToday = 2091, goal = 10000, today = "2.091", unit = "Schritte", average = "2.392",
    total = "7.177", bars = listOf(
        Bar("Mo.", 3000f, HistoryBlue, false), Bar("Di.", 11000f, StepsGreen, false),
        Bar("Mi.", 5000f, HistoryBlue, false), Bar("Do.", 86f, HistoryBlue, false),
    )
)

@Preview(showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun OverviewPreview() {
    PedometerTheme { Overview(previewState, {}, {}) }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 380, device = "spec:width=800dp,height=380dp,orientation=landscape")
@Composable
private fun OverviewLandscapePreview() {
    PedometerTheme { Overview(previewState, {}, {}) }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OverviewDarkPreview() {
    PedometerTheme(darkTheme = true) { Overview(previewState, {}, {}) }
}
