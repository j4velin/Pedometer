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

package de.j4velin.pedometer.ui

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.j4velin.pedometer.R
import android.graphics.Color as AndroidColor

object ColorPickerTags {
    const val HEX = "colorpicker_hex"
    const val OK = "colorpicker_ok"
}

private const val HEX_DIGITS = "0123456789abcdefABCDEF"

/** [color] as #AARRGGBB */
fun formatColor(@ColorInt color: Int): String = "#%08X".format(color)

/**
 * Parses #RRGGBB or #AARRGGBB, with or without the #
 *
 * @return the colour, or null if [text] is neither
 */
@ColorInt
fun parseColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8 || !hex.all { it in HEX_DIGITS }) return null
    val value = hex.toLong(16)
    return if (hex.length == 6) (0xFF000000 or value).toInt() else value.toInt()
}

/**
 * Picks a colour with transparency: sliders for hue, saturation, brightness and opacity, and the
 * hex value to type or paste one.
 *
 * @param onPick called with the colour when the user confirms it
 */
@Composable
fun ColorPickerDialog(@ColorInt initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val initialHsv = FloatArray(3).also { AndroidColor.colorToHSV(initial, it) }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    var alpha by rememberSaveable { mutableFloatStateOf(AndroidColor.alpha(initial) / 255f) }
    var hex by rememberSaveable { mutableStateOf(formatColor(initial)) }

    fun color() = AndroidColor.HSVToColor(
        (alpha * 255).toInt().coerceIn(0, 255), floatArrayOf(hue, saturation, brightness)
    )

    fun slid(set: () -> Unit) {
        set()
        hex = formatColor(color())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(color()) }, Modifier.testTag(ColorPickerTags.OK)) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ColorSwatch(
                    color(),
                    Modifier.fillMaxWidth().height(48.dp),
                )
                ColorSlider(R.string.hue, hue, 0f..360f) { slid { hue = it } }
                ColorSlider(R.string.saturation, saturation) { slid { saturation = it } }
                ColorSlider(R.string.brightness, brightness) { slid { brightness = it } }
                ColorSlider(R.string.opacity, alpha) { slid { alpha = it } }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        parseColor(text)?.let {
                            val hsv = FloatArray(3)
                            AndroidColor.colorToHSV(it, hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                            alpha = AndroidColor.alpha(it) / 255f
                        }
                    },
                    isError = parseColor(hex) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth().testTag(ColorPickerTags.HEX),
                )
            }
        },
    )
}

@Composable
private fun ColorSlider(
    @StringRes label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** [color] over a checkerboard, which shows how transparent it is */
@Composable
fun ColorSwatch(@ColorInt color: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Canvas(
        modifier.defaultMinSize(32.dp, 32.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        val cell = 8.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(Color.LightGray, Offset(x, y), Size(cell, cell))
                x += 2 * cell
            }
            y += cell
            row++
        }
        drawRect(Color(color))
    }
}
