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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.j4velin.pedometer.ui.theme.HistoryBlue
import de.j4velin.pedometer.ui.theme.PedometerTheme
import de.j4velin.pedometer.ui.theme.StepsGreen

/**
 * The last days as bars, with the value above and the day below each bar. Replaces EazeGraph's
 * BarChart on the overview.
 */
@Composable
fun WeekBars(bars: List<Bar>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    val axisColor = MaterialTheme.colorScheme.outline
    Canvas(modifier.semantics {
        contentDescription = bars.joinToString { "${it.label}: ${it.text}" }
    }) {
        if (bars.isEmpty()) return@Canvas
        val legendHeight = 35.dp.toPx()
        val valueHeight = 20.dp.toPx()
        val spacing = 8.dp.toPx()
        val chartHeight = size.height - legendHeight - valueHeight
        val barWidth = (size.width - spacing * (bars.size + 1)) / bars.size
        val max = bars.maxOf { it.value }.takeIf { it > 0 } ?: 1f

        bars.forEachIndexed { i, bar ->
            val left = spacing + i * (barWidth + spacing)
            val center = left + barWidth / 2
            val height = chartHeight * bar.value / max
            val top = valueHeight + chartHeight - height
            drawRect(bar.color, Offset(left, top), Size(barWidth, height))

            val value = measurer.measure(bar.text, textStyle)
            drawText(value, topLeft = Offset(center - value.size.width / 2, top - value.size.height))

            val tickTop = valueHeight + chartHeight + 4.dp.toPx()
            drawLine(axisColor, Offset(center, tickTop), Offset(center, tickTop + 4.dp.toPx()))
            val label = measurer.measure(bar.label, textStyle)
            drawText(label, topLeft = Offset(center - label.size.width / 2, tickTop + 8.dp.toPx()))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WeekBarsPreview() {
    PedometerTheme {
        WeekBars(
            listOf(
                Bar("Mo", 3000f, HistoryBlue, false), Bar("Di", 11000f, StepsGreen, false),
                Bar("Mi", 5000f, HistoryBlue, false), Bar("Do", 86f, HistoryBlue, false),
            ),
            Modifier.fillMaxWidth().height(150.dp)
        )
    }
}
