package de.j4velin.pedometer.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.j4velin.pedometer.ui.theme.MissingRed
import de.j4velin.pedometer.ui.theme.StepsGreen

/**
 * Today's steps as a ring: green for the steps taken, red for what is still missing to [goal].
 * Replaces EazeGraph's PieChart on the overview.
 */
@Composable
fun StepsRing(steps: Int, goal: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension / 8
        val inset = stroke / 2
        val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
        val topLeft = Offset(inset, inset)
        val done = if (goal <= 0) 1f else (steps.toFloat() / goal).coerceIn(0f, 1f)
        drawArc(StepsGreen, -90f, 360f * done, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(MissingRed, -90f + 360f * done, 360f * (1 - done), false, topLeft, arcSize, style = Stroke(stroke))
    }
}

@Preview
@Composable
private fun StepsRingPreview() {
    StepsRing(steps = 6800, goal = 10000, modifier = Modifier.size(200.dp))
}
