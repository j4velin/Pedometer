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
import androidx.lifecycle.viewModelScope
import de.j4velin.pedometer.BuildConfig
import de.j4velin.pedometer.PedometerApp
import de.j4velin.pedometer.R
import de.j4velin.pedometer.data.HistorySummary
import de.j4velin.pedometer.domain.TodaySteps
import de.j4velin.pedometer.ui.Formats
import de.j4velin.pedometer.ui.theme.HistoryBlue
import de.j4velin.pedometer.ui.theme.StepsGreen
import de.j4velin.pedometer.util.Logger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One day in the week chart */
data class Bar(val label: String, val value: Float, val color: Color, val decimal: Boolean) {
    /** The value as the chart shows it above the bar: "8000" steps, "6.0" km */
    val text: String get() = if (decimal) value.toString() else value.toInt().toString()
}

/** The record day, and the steps of the last seven days and of this month */
data class Statistics(
    val recordSteps: Int,
    /** null if there is no finished day yet */
    val recordDate: Date?,
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
 * The overview: today's steps, the average and total, and the last week.
 *
 * Today's steps come from [de.j4velin.pedometer.domain.StepAccounting.today], the finished days
 * from [de.j4velin.pedometer.data.StepsHistory]. While the screen is resumed, it also listens to
 * the step counter itself and passes the values on, as the service only gets them every few
 * minutes.
 */
class OverviewViewModel(private val app: PedometerApp) : ViewModel(), SensorEventListener {
    private val accounting = app.accounting
    private val settings get() = app.settings

    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    private var today: TodaySteps = accounting.today.value
    /** The finished days before [historyDay], or null while they are loading */
    private var history: HistorySummary? = null
    private var historyDay = 0L
    /** Whether today had its entry when the history was read: the day before was finished */
    private var historyComplete = false
    private var historyJob: Job? = null
    private var showSteps = true

    /** All steps taken so far, for the split counter */
    val totalSteps: Int get() = (history?.total ?: 0) + stepsToday

    private val stepsToday: Int get() = today.steps ?: 0

    private val sensorManager get() = app.getSystemService(SensorManager::class.java)

    init {
        viewModelScope.launch { accounting.today.collect(::onToday) }
    }

    /** The screen is shown: reads everything anew and starts listening to the step counter */
    fun resume() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            _state.value = _state.value.copy(noSensor = true)
        } else {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, 0)
        }
        // the history or the settings might have changed while the screen was hidden
        today = accounting.refresh()
        loadHistory()
    }

    /** The screen is hidden: stops listening and saves the latest step counter value */
    fun pause() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Logger.log(e)
        }
        accounting.saveLatest()
    }

    fun toggleStepsAndDistance() {
        showSteps = !showSteps
        update()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // won't happen
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values[0]
        if (BuildConfig.DEBUG) Logger.log("UI - sensorChanged | since boot: $value")
        if (value > Int.MAX_VALUE) return
        accounting.onLiveStepCounter(value.toInt())
    }

    private fun onToday(today: TodaySteps) {
        this.today = today
        // after midnight, yesterday becomes part of the total and the bar chart - once today has
        // its entry, as until then the new steps still go to yesterday
        if (today.day != historyDay || today.started != historyComplete) loadHistory() else update()
    }

    private fun loadHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val day = today.day
            val complete = today.started
            history = app.history.summary(day)
            historyDay = day
            historyComplete = complete
            update()
        }
    }

    /** The statistics dialog's figures. Today counts with its steps so far. */
    suspend fun statistics(): Statistics {
        val record = app.history.record()
        val today = accounting.today()
        val date = Calendar.getInstance()
        date.timeInMillis = today
        val daysThisMonth = date.get(Calendar.DAY_OF_MONTH)
        date.add(Calendar.DATE, -6)
        val thisWeek = app.history.stepsSince(date.timeInMillis, today) + stepsToday
        date.timeInMillis = today
        date.set(Calendar.DAY_OF_MONTH, 1)
        val thisMonth = app.history.stepsSince(date.timeInMillis, today) + stepsToday
        return Statistics(
            record?.second ?: 0, record?.first, thisWeek, thisMonth, daysThisMonth
        )
    }

    override fun onCleared() {
        sensorManager.unregisterListener(this)
    }

    private fun update() {
        val history = history ?: return
        val format = Formats.number()
        val steps = stepsToday
        val metric = settings.stepUnit == "cm"
        val stepSize = settings.stepSize
        // today counts as a day, so this is never 0
        val days = history.days + 1
        val total = history.total + steps
        _state.value = _state.value.copy(
            stepsToday = steps,
            goal = today.goal,
            showSteps = showSteps,
            unit = when {
                showSteps -> app.getString(R.string.steps)
                metric -> "km"
                else -> "mi"
            },
            bars = bars(history, metric, stepSize),
        ).let {
            if (showSteps) {
                it.copy(
                    today = format.format(steps),
                    total = format.format(total),
                    average = format.format(total / days),
                )
            } else {
                // cm -> km, or ft -> mi
                val divisor = if (metric) 100000 else 5280
                val distanceTotal = total * stepSize / divisor
                it.copy(
                    today = format.format(steps * stepSize / divisor),
                    total = format.format(distanceTotal),
                    average = format.format(distanceTotal / days),
                )
            }
        }
    }

    /** The last week's days with steps, oldest first */
    private fun bars(history: HistorySummary, metric: Boolean, stepSize: Float): List<Bar> {
        val dayName = SimpleDateFormat("E", Locale.getDefault())
        return history.lastWeek
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
                    if (steps > today.goal) StepsGreen else HistoryBlue, decimal = !showSteps
                )
            }
    }
}
