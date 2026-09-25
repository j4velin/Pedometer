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

package de.j4velin.pedometer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.j4velin.pedometer.R
import de.j4velin.pedometer.ui.ColorPickerDialog
import de.j4velin.pedometer.ui.ColorSwatch
import de.j4velin.pedometer.ui.theme.PedometerTheme

object WidgetConfigTags {
    const val TEXT_COLOR = "widget_text_color"
    const val BACKGROUND = "widget_background"
    const val DONE = "widget_done"
}

/**
 * Sets the colours of a widget: shown by the launcher when the widget is placed. Keeps the class
 * name of earlier versions, which the widget's provider info names.
 */
class WidgetConfig : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // the widget is placed with the default colours even if the user just leaves
        setResult(
            RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        val settings = WidgetSettings(this)
        setContent {
            PedometerTheme {
                WidgetConfigDialog(
                    textColor = settings.textColor(widgetId),
                    background = settings.background(widgetId),
                    onTextColor = {
                        settings.setTextColor(widgetId, it)
                        Widget.update(this)
                    },
                    onBackground = {
                        settings.setBackground(widgetId, it)
                        Widget.update(this)
                    },
                    onDone = ::finish,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Widget.update(this)
    }
}

private enum class Picking { TEXT_COLOR, BACKGROUND }

@Composable
private fun WidgetConfigDialog(
    @ColorInt textColor: Int,
    @ColorInt background: Int,
    onTextColor: (Int) -> Unit,
    onBackground: (Int) -> Unit,
    onDone: () -> Unit,
) {
    var text by rememberSaveable { mutableIntStateOf(textColor) }
    var bg by rememberSaveable { mutableIntStateOf(background) }
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = {
            TextButton(onClick = onDone, Modifier.testTag(WidgetConfigTags.DONE)) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column {
                ColorRow(R.string.text_color, text, WidgetConfigTags.TEXT_COLOR) {
                    picking = Picking.TEXT_COLOR
                }
                ColorRow(R.string.background_color, bg, WidgetConfigTags.BACKGROUND) {
                    picking = Picking.BACKGROUND
                }
            }
        },
    )

    when (picking) {
        Picking.TEXT_COLOR -> ColorPickerDialog(text, onDismiss = { picking = null }) {
            text = it
            picking = null
            onTextColor(it)
        }
        Picking.BACKGROUND -> ColorPickerDialog(bg, onDismiss = { picking = null }) {
            bg = it
            picking = null
            onBackground(it)
        }
        null -> Unit
    }
}

@Composable
private fun ColorRow(@StringRes label: Int, @ColorInt color: Int, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label))
        ColorSwatch(color)
    }
}
