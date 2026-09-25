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

package de.j4velin.pedometer.domain

import de.j4velin.pedometer.data.Settings
import de.j4velin.pedometer.data.StepsDatabase
import de.j4velin.pedometer.util.Util
import java.io.IOException
import java.io.Reader
import java.io.Writer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Today's steps, as far as the app knows them
 *
 * @param day the day, as [Util.getToday] returns it
 * @param steps the steps taken today, or null while there is no step counter value yet
 * @param goal the daily goal
 * @param started whether today has its entry yet. Until it has, the steps since the last save
 * still go to the day before, so that day is not finished yet.
 */
data class TodaySteps(val day: Long, val steps: Int?, val goal: Int, val started: Boolean)

/**
 * The rules for turning the step counter's "steps since boot" into a history of steps per day.
 *
 * The step counter only counts up from the last boot. Today's entry therefore stores the negative
 * counter value at the start of the day (the offset), and today's steps are offset + counter. The
 * last counter value is saved every [SAVE_OFFSET_STEPS] steps or [SAVE_OFFSET_TIME] ms, so a
 * reboot or a new day loses as little as possible.
 *
 * One instance lives as long as the process: it keeps the latest step counter values, which the
 * shutdown needs, and publishes today's steps as [today] for the notification, the widget and the
 * overview. Two listeners feed it: the service, which gets batched values every few minutes, and
 * the overview, which gets them live while it is shown.
 *
 * @param clock the current time in ms since 1970
 */
class StepAccounting(
    private val db: StepsDatabase,
    private val settings: Settings,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** The latest step counter value the service received, or 0 if there was none yet */
    private var lastSensorValue = 0

    /** The latest step counter value the overview received, or 0 if there was none yet */
    private var lastLiveValue = 0

    /** The value received last, from either listener */
    private var latestValue = 0

    private var lastSaveSteps = 0
    private var lastSaveTime = 0L

    private val _today = MutableStateFlow(computeToday())

    /** Today's steps. Updated with every step counter value and every change of the day. */
    val today: StateFlow<TodaySteps> = _today.asStateFlow()

    /** The current time in ms since 1970 */
    fun now(): Long = clock()

    /**
     * The current day, as its entry stores it: the local midnight - or, if the time zone changed
     * during the day, the midnight of the zone the day started in, so that the day keeps its
     * one entry. See [StepsDatabase.dayNear].
     */
    fun today(): Long = db.dayNear(Util.getToday(clock()))

    /**
     * Reads today's steps anew and publishes them: for a new day, a changed goal, or when the
     * history might have changed
     */
    fun refresh(): TodaySteps = computeToday().also { _today.value = it }

    /**
     * Today's steps: today's offset plus the latest step counter value. Before any value arrived,
     * the last saved one is the latest.
     */
    private fun computeToday(): TodaySteps {
        val today = today()
        val sinceBoot = latestValue.takeIf { it > 0 } ?: db.currentSteps
        val offset = db.getSteps(today)
        val started = offset != Int.MIN_VALUE
        val steps = when {
            sinceBoot <= 0 -> null
            // no entry yet: today starts with the next save
            !started -> 0
            else -> maxOf(offset + sinceBoot, 0)
        }
        return TodaySteps(today, steps, settings.goal, started)
    }

    /**
     * The step counter service received [stepsSinceBoot].
     *
     * @return true, if the value was saved
     */
    fun onStepCounter(stepsSinceBoot: Int): Boolean {
        lastSensorValue = stepsSinceBoot
        latestValue = stepsSinceBoot
        return saveIfNecessary().also { refresh() }
    }

    /**
     * The service was started, by the app or by its hourly alarm: saves if necessary. Until the
     * step counter reports, the last saved value is taken as the latest one, so that the first
     * save after midnight starts the new day even without new steps.
     *
     * @return true, if a value was saved
     */
    fun onServiceStarted(): Boolean {
        val saved = saveIfNecessary()
        if (lastSensorValue == 0) lastSensorValue = db.currentSteps
        refresh()
        return saved
    }

    /**
     * The overview received [stepsSinceBoot] while it is shown. Starts today at 0 steps if today
     * has no entry yet, but saves nothing else: [saveLatest] does when the overview is left.
     */
    fun onLiveStepCounter(stepsSinceBoot: Int) {
        if (stepsSinceBoot <= 0) return
        lastLiveValue = stepsSinceBoot
        latestValue = stepsSinceBoot
        val today = today()
        if (db.getSteps(today) == Int.MIN_VALUE) {
            // we don't know when the reboot was, so today starts at 0 steps
            db.insertNewDay(today, stepsSinceBoot)
        }
        refresh()
    }

    /**
     * Saves the latest step counter value either listener received - unless the saved one is
     * newer, which it is if the service saved while the overview got no new value.
     */
    fun saveLatest() {
        val steps = maxOf(lastSensorValue, lastLiveValue)
        if (steps <= db.currentSteps) return
        db.saveCurrentSteps(steps)
        lastSaveSteps = steps
        lastSaveTime = clock()
    }

    /**
     * Saves the latest step counter value, if it is more than [SAVE_OFFSET_STEPS] steps or
     * [SAVE_OFFSET_TIME] ms newer than the last save, or if today has no entry yet.
     *
     * @return true, if the value was saved
     */
    fun saveIfNecessary(): Boolean {
        val steps = lastSensorValue
        val now = clock()
        val today = today()
        // save as soon as today has no entry, after midnight or a flight into the next date:
        // until it has one, all new steps are attributed to the day before
        val started = db.getSteps(today) != Int.MIN_VALUE
        val necessary = steps > lastSaveSteps + SAVE_OFFSET_STEPS ||
                (steps > 0 && (now > lastSaveTime + SAVE_OFFSET_TIME || !started))
        if (!necessary) return false
        if (!started) db.insertNewDay(today, steps)
        db.saveCurrentSteps(steps)
        lastSaveSteps = steps
        lastSaveTime = now
        return true
    }

    /**
     * The device shuts down: moves the steps since the day started into the history, as the
     * step counter starts at 0 again after the reboot.
     */
    fun onShutdown() {
        // if the user used a root script for shutdown, the shutdown broadcast might not be sent.
        // Therefore the next boot checks this setting
        settings.correctShutdown = true

        // the saved value might be up to an hour old, while the listeners have received newer
        // values in the meantime
        val steps = maxOf(db.currentSteps, lastSensorValue, lastLiveValue)
        val today = today()
        if (db.getSteps(today) == Int.MIN_VALUE) {
            // already a new day: the steps belong to the last one
            db.insertNewDay(today, steps)
        } else {
            db.addToLastEntry(steps)
        }
        // the "steps since boot" value is reset on boot, see onBootCompleted
    }

    /**
     * BOOT_COMPLETED arrived. Since Android 15, it is also sent when the app starts for the first
     * time after being installed or force stopped. The step counter is only reset by an actual
     * reboot though, and treating anything else as one would drop today's offset.
     *
     * @param bootCount the system's boot count, or -1 if not available
     * @return true, if it was a reboot
     */
    fun onBootCompleted(bootCount: Int): Boolean {
        val lastBootCount = settings.bootCount
        settings.bootCount = bootCount
        if (bootCount != -1 && (lastBootCount == -1 || bootCount == lastBootCount)) {
            return false
        }

        if (!settings.correctShutdown) {
            // can we at least recover some steps?
            val steps = maxOf(0, db.currentSteps)
            db.addToLastEntry(steps)
        }
        // the last entry might still be a negative offset, which is meaningless after a reboot
        db.removeNegativeEntries()
        db.saveCurrentSteps(0)
        settings.correctShutdown = false
        // values this process might have from before the reboot are meaningless now
        lastSensorValue = 0
        lastLiveValue = 0
        latestValue = 0
        lastSaveSteps = 0
        refresh()
        return true
    }

    /**
     * The app was updated. Older versions did not store the boot count, so the first real reboot
     * after the update would otherwise not be recognized as one.
     */
    fun onAppUpdated(bootCount: Int) {
        settings.bootCount = bootCount
        settings.removeObsolete()
    }

    /**
     * Writes the finished days as "date;steps" lines. Today is left out: its entry is not a step
     * count yet but the offset - exported (as 0) it would reset today's steps on import.
     */
    fun exportCsv(out: Writer) {
        db.query(
            arrayOf("date", "steps"), "date > 0 AND date < ?", arrayOf(today().toString()),
            null, null, "date", null
        ).use { c ->
            while (c.moveToNext()) {
                out.append(c.getString(0)).append(";")
                    .append(maxOf(0, c.getInt(1)).toString()).append("\n")
            }
        }
        out.flush()
    }

    /**
     * The day an imported [date] belongs to. Exported in another time zone, it is the midnight of
     * that zone: this is the closest local midnight, or an entry for that date already there.
     */
    private fun day(date: Long): Long = db.dayNear(Util.getToday(date + HALF_DAY))

    class ImportResult(val inserted: Int, val overwritten: Int, val ignored: Int)

    /**
     * Imports "date;steps" lines as written by [exportCsv], overwriting existing days. Today and
     * later days are skipped: files exported by older versions contain today's entry (as 0),
     * which would overwrite today's offset and with it the steps taken so far.
     */
    @Throws(IOException::class)
    fun importCsv(input: Reader): ImportResult {
        var inserted = 0
        var overwritten = 0
        var ignored = 0
        val today = today()
        input.buffered().lineSequence().forEach { line ->
            try {
                val data = line.split(";")
                val date = day(data[0].toLong())
                if (date >= today) return@forEach
                if (db.insertDayFromBackup(date, data[1].toInt())) inserted++ else overwritten++
            } catch (e: Exception) {
                ignored++
            }
        }
        return ImportResult(inserted, overwritten, ignored)
    }

    companion object {
        private const val SAVE_OFFSET_TIME = 60 * 60 * 1000L
        private const val SAVE_OFFSET_STEPS = 500
        private const val HALF_DAY = 12 * 60 * 60 * 1000L
    }
}
