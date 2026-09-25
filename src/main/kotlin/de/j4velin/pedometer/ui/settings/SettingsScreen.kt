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

package de.j4velin.pedometer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.j4velin.pedometer.R
import de.j4velin.pedometer.SensorListener
import de.j4velin.pedometer.StepsNotification
import de.j4velin.pedometer.data.Settings
import de.j4velin.pedometer.games.GamesState
import de.j4velin.pedometer.ui.dialogs.MessageDialog

private const val CSV_MIME_TYPE = "text/csv"

private enum class SettingsDialog { GOAL, STEP_SIZE }

/**
 * @param games the Play Games state, or null if this build has no games
 * @param onSignIn starts the Play Games sign in
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    games: GamesState?,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    // the notification settings might have changed
    LifecycleResumeEffect(Unit) {
        SensorListener.start(context)
        onPauseOrDispose { }
    }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME_TYPE)
    ) { uri -> uri?.let(viewModel::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Setting(
            stringResource(R.string.goal), pluralStringResource(R.plurals.goal_summary, state.goal, state.goal)
        ) { dialog = SettingsDialog.GOAL }
        Setting(
            stringResource(R.string.step_size),
            stringResource(R.string.step_size_summary, state.stepSize, state.stepUnit)
        ) { dialog = SettingsDialog.STEP_SIZE }
        if (games != null) {
            Setting(
                stringResource(R.string.account),
                if (games.signedIn) {
                    stringResource(R.string.signed_in, games.playerName ?: "")
                } else {
                    stringResource(R.string.sign_in)
                },
                // Play Games v2 has no sign out: players manage that in the Play Games app
                onClick = if (games.signedIn) null else onSignIn,
            )
        }
        Setting(stringResource(R.string.notification_settings)) {
            StepsNotification.openSettings(context)
        }
        Setting(stringResource(R.string.export_title), stringResource(R.string.export_summary)) {
            export.launch("Pedometer.csv")
        }
        Setting(stringResource(R.string.import_title), stringResource(R.string.import_summary)) {
            import.launch(
                arrayOf(
                    CSV_MIME_TYPE, "text/comma-separated-values", "text/plain",
                    "application/octet-stream"
                )
            )
        }
    }

    when (dialog) {
        SettingsDialog.GOAL -> GoalDialog(state.goal, onDismiss = { dialog = null }) {
            viewModel.setGoal(it)
            dialog = null
        }
        SettingsDialog.STEP_SIZE -> StepSizeDialog(
            state.stepSize, state.stepUnit, onDismiss = { dialog = null }
        ) { size, unit ->
            viewModel.setStepSize(size, unit)
            dialog = null
        }
        null -> Unit
    }
    state.message?.let { MessageDialog(it, onDismiss = viewModel::dismissMessage) }
}

@Composable
private fun Setting(title: String, summary: String? = null, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
    HorizontalDivider()
}

@Composable
private fun GoalDialog(goal: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(goal.toString()) }
    val value = text.trim().toIntOrNull()?.takeIf { it in 1..100000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_goal)) },
        text = {
            OutlinedTextField(
                text, { text = it }, Modifier.testTag("goal"), singleLine = true,
                isError = value == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun StepSizeDialog(
    size: Float,
    unit: String,
    onDismiss: () -> Unit,
    onConfirm: (Float, String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(size.toString()) }
    var selectedUnit by rememberSaveable { mutableStateOf(unit) }
    // the keyboard might use the locale's decimal separator
    val value = text.trim().replace(',', '.').toFloatOrNull()
        ?.takeIf { Settings.isValidStepSize(it, selectedUnit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_step_size)) },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it }, Modifier.testTag("stepsize"), singleLine = true,
                    isError = value == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row {
                    listOf("cm", "ft").forEach { option ->
                        Row(
                            Modifier.selectable(selectedUnit == option, role = Role.RadioButton) {
                                selectedUnit = option
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selectedUnit == option, null)
                            Text(option)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let { onConfirm(it, selectedUnit) } }, enabled = value != null
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
