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

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import de.j4velin.pedometer.BuildConfig
import de.j4velin.pedometer.PedometerApp
import de.j4velin.pedometer.R
import de.j4velin.pedometer.ui.Formats
import de.j4velin.pedometer.ui.theme.HistoryBlue
import de.j4velin.pedometer.ui.theme.StepsGreen
import de.j4velin.pedometer.util.Logger
import de.j4velin.pedometer.util.Util
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One day in the week chart */
data class Bar(val label: String, val value: Float, val color: Color, val decimal: Boolean) {
    /** The value as the chart shows it above the bar: "8000" steps, "6.0" km */
    val text: String get() = if (decimal) value.toString() else value.toInt().toString()
}

/** The record day, and the steps of the last seven days and of this month */
data class Statistics(
    val recordSteps: Int,
    val recordDate: Date,
    val thisWeek: Int,
    val thisMonth: Int,
    val daysThisMonth: Int,
)

data class OverviewState(
    /** Today's steps, for the ring */
    val stepsToday: Int = 0,
    val goal: Int = 0,
    /** The number in the ring: today's steps or distance */
    val today: String = "",
    /** "steps", "km" or "mi" */
    val unit: String = "",
    val average: String = "",
    val total: String = "",
    /** The last seven finished days with steps, oldest first */
    val bars: List<Bar> = emptyList(),
    val showSteps: Boolean = true,
    /** The device has no step counter */
    val noSensor: Boolean = false,
)

/**
 * The overview: today's steps, the average and total, and the last week. While the screen is
 * resumed, it listens to the step counter itself to update live, and starts today's entry if the
 * service did not yet.
 */
class OverviewViewModel(private val app: PedometerApp) : ViewModel(), SensorEventListener {
    private val db get() = app.database
    private val settings get() = app.settings

    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    private var today = 0L
    private var todayOffset = Int.MIN_VALUE
    private var totalStart = 0
    private var totalDays = 1
    private var goal = 0
    private var showSteps = true

    /** The latest step counter value, for the statistics dialog */
    var sinceBoot = 0
        private set

    /** All steps taken so far, for the split counter */
    val totalSteps: Int get() = totalStart + stepsToday

    private val stepsToday: Int
        // todayOffset might still be Int.MIN_VALUE on first start
        get() = maxOf(todayOffset + sinceBoot, 0)

    private val sensorManager get() = app.getSystemService(SensorManager::class.java)

    /** The screen is shown: reads everything anew and starts listening to the step counter */
    fun resume() {
        if (BuildConfig.DEBUG) db.logState()
        today = Util.getToday()
        todayOffset = db.getSteps(today)
        goal = settings.goal
        sinceBoot = db.currentSteps

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            _state.value = _state.value.copy(noSensor = true)
        } else {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, 0)
        }

        totalStart = db.totalWithoutToday
        totalDays = db.days
        update(bars = true)
    }

    /** The screen is hidden: stops listening and saves the latest step counter value */
    fun pause() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Logger.log(e)
        }
        db.saveCurrentSteps(sinceBoot)
    }

    fun toggleStepsAndDistance() {
        showSteps = !showSteps
        update(bars = true)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // won't happen
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values[0]
        if (BuildConfig.DEBUG) Logger.log(
            "UI - sensorChanged | todayOffset: $todayOffset since boot: $value"
        )
        if (value > Int.MAX_VALUE || value == 0f) return
        val dayChanged = Util.getToday() != today
        if (dayChanged) {
            // the app was open at midnight: today's entry might already have been created by
            // the SensorListener
            today = Util.getToday()
            todayOffset = db.getSteps(today)
        }
        if (todayOffset == Int.MIN_VALUE) {
            // no values for today: we don't know when the reboot was, so today starts at 0 steps
            todayOffset = app.accounting.startToday(value.toInt())
        }
        sinceBoot = value.toInt()
        if (dayChanged) {
            // yesterday is now part of the total and the bar chart
            totalStart = db.totalWithoutToday
            totalDays = db.days
        }
        update(bars = dayChanged)
    }

    /** The statistics dialog's figures. Today counts with its steps so far. */
    fun statistics(): Statistics {
        val (recordDate, recordSteps) = db.recordData
        val date = Calendar.getInstance()
        date.timeInMillis = Util.getToday()
        val daysThisMonth = date.get(Calendar.DAY_OF_MONTH)
        date.add(Calendar.DATE, -6)
        val thisWeek = db.getSteps(date.timeInMillis, System.currentTimeMillis()) + sinceBoot
        date.timeInMillis = Util.getToday()
        date.set(Calendar.DAY_OF_MONTH, 1)
        val thisMonth = db.getSteps(date.timeInMillis, System.currentTimeMillis()) + sinceBoot
        return Statistics(recordSteps, recordDate, thisWeek, thisMonth, daysThisMonth)
    }

    override fun onCleared() {
        sensorManager.unregisterListener(this)
    }

    private fun update(bars: Boolean) {
        val format = Formats.number()
        val steps = stepsToday
        val metric = settings.stepUnit == "cm"
        val stepSize = settings.stepSize
        _state.value = _state.value.copy(
            stepsToday = steps,
            goal = goal,
            showSteps = showSteps,
            unit = when {
                showSteps -> app.getString(R.string.steps)
                metric -> "km"
                else -> "mi"
            },
            bars = if (bars) loadBars(metric, stepSize) else _state.value.bars,
        ).let {
            if (showSteps) {
                val total = totalStart + steps
                it.copy(
                    today = format.format(steps),
                    total = format.format(total),
                    average = format.format(total / totalDays),
                )
            } else {
                // cm -> km, or ft -> mi
                val divisor = if (metric) 100000 else 5280
                val distanceToday = steps * stepSize / divisor
                val distanceTotal = (totalStart + steps) * stepSize / divisor
                it.copy(
                    today = format.format(distanceToday),
                    total = format.format(distanceTotal),
                    average = format.format(distanceTotal / totalDays),
                )
            }
        }
    }

    /**
     * The days before today with steps, oldest first. The newest entry is left out, as that is
     * today's.
     */
    private fun loadBars(metric: Boolean, stepSize: Float): List<Bar> {
        val dayName = SimpleDateFormat("E", Locale.getDefault())
        return db.getLastEntries(8).drop(1).reversed()
            .filter { (_, steps) -> steps > 0 }
            .map { (date, steps) ->
                val value = if (showSteps) {
                    steps.toFloat()
                } else {
                    val distance = steps * stepSize / if (metric) 100000 else 5280
                    (distance * 1000).roundToInt() / 1000f // 3 decimals
                }
                Bar(
                    dayName.format(Date(date)), value,
                    if (steps > goal) StepsGreen else HistoryBlue, decimal = !showSteps
                )
            }
    }
}
