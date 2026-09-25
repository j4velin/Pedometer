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

import de.j4velin.pedometer.BuildConfig
import de.j4velin.pedometer.data.Settings
import de.j4velin.pedometer.data.StepsDatabase
import de.j4velin.pedometer.util.Logger
import de.j4velin.pedometer.util.Util
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.util.Date

/**
 * The rules for turning the step counter's "steps since boot" into a history of steps per day.
 *
 * The step counter only counts up from the last boot. Today's entry therefore stores the negative
 * counter value at the start of the day (the offset), and today's steps are offset + counter. The
 * last counter value is saved every [SAVE_OFFSET_STEPS] steps or [SAVE_OFFSET_TIME] ms, so a
 * reboot or a new day loses as little as possible.
 *
 * One instance lives as long as the process: it keeps the step counter service's latest values,
 * which the shutdown and the notification need.
 *
 * @param clock the current time in ms since 1970
 */
class StepAccounting(
    private val db: StepsDatabase,
    private val settings: Settings,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** The latest step counter value the service received, or 0 if there was none yet */
    var lastSensorValue = 0
        private set
    private var lastSaveSteps = 0
    private var lastSaveTime = 0L

    private fun today() = Util.getToday(clock())

    /**
     * The step counter service received [stepsSinceBoot].
     *
     * @return true, if the value was saved
     */
    fun onStepCounter(stepsSinceBoot: Int): Boolean {
        lastSensorValue = stepsSinceBoot
        return saveIfNecessary()
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
        // save right after midnight too: until today's entry exists, all new steps are
        // attributed to yesterday
        val necessary = steps > lastSaveSteps + SAVE_OFFSET_STEPS ||
                (steps > 0 && (now > lastSaveTime + SAVE_OFFSET_TIME || lastSaveTime < today()))
        if (!necessary) return false
        if (BuildConfig.DEBUG) Logger.log(
            "saving steps: steps=$steps lastSave=$lastSaveSteps lastSaveTime=${Date(lastSaveTime)}"
        )
        val today = today()
        if (db.getSteps(today) == Int.MIN_VALUE) {
            db.insertNewDay(today, steps)
        }
        db.saveCurrentSteps(steps)
        lastSaveSteps = steps
        lastSaveTime = now
        return true
    }

    /**
     * Today's steps as the notification shows them, or null while there is no step counter
     * value yet. Before the service got its first value, the last saved one is used - and from
     * then on treated as the latest value.
     */
    fun stepsTodayForNotification(): Int? {
        val offset = db.getSteps(today())
        if (lastSensorValue == 0) lastSensorValue = db.currentSteps
        val steps = lastSensorValue
        if (steps <= 0) return null
        return (if (offset == Int.MIN_VALUE) -steps else offset) + steps
    }

    /**
     * The overview received [stepsSinceBoot] while today has no entry yet: starts today at 0
     * steps, unless the service started it in the meantime.
     *
     * @return today's offset
     */
    fun startToday(stepsSinceBoot: Int): Int {
        val today = today()
        db.insertNewDay(today, stepsSinceBoot)
        return db.getSteps(today)
    }

    /**
     * The device shuts down: moves the steps since the day started into the history, as the
     * step counter starts at 0 again after the reboot.
     */
    fun onShutdown() {
        // if the user used a root script for shutdown, the shutdown broadcast might not be sent.
        // Therefore the next boot checks this setting
        settings.correctShutdown = true

        // the saved value might be up to an hour old, while the service has received newer
        // values in the meantime
        val steps = maxOf(db.currentSteps, lastSensorValue)
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
            if (BuildConfig.DEBUG) Logger.log("not a reboot, boot count: $bootCount")
            return false
        }

        if (!settings.correctShutdown) {
            if (BuildConfig.DEBUG) Logger.log("Incorrect shutdown")
            // can we at least recover some steps?
            val steps = maxOf(0, db.currentSteps)
            if (BuildConfig.DEBUG) Logger.log("Trying to recover $steps steps")
            db.addToLastEntry(steps)
        }
        // the last entry might still be a negative offset, which is meaningless after a reboot
        db.removeNegativeEntries()
        db.saveCurrentSteps(0)
        settings.correctShutdown = false
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
                val date = data[0].toLong()
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
    }
}
