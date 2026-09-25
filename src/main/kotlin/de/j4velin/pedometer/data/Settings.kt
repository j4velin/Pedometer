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

package de.j4velin.pedometer.data

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/**
 * Typed access to the "pedometer" preferences. The file name and the keys are the ones every
 * earlier version used, so existing settings carry over.
 *
 * The values are read on every access rather than cached: the file is written from the service,
 * the receivers and the UI, and the preferences keep it cached in memory anyway.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var goal: Int
        get() = prefs.getInt(GOAL, DEFAULT_GOAL)
        set(value) = prefs.edit { putInt(GOAL, value) }

    /** The step length, in [stepUnit]s */
    var stepSize: Float
        get() = prefs.getFloat(STEP_SIZE, defaultStepSize)
        set(value) = prefs.edit { putFloat(STEP_SIZE, value) }

    /** "cm" or "ft" */
    var stepUnit: String
        get() = prefs.getString(STEP_UNIT, defaultStepUnit) ?: defaultStepUnit
        set(value) = prefs.edit { putString(STEP_UNIT, value) }

    /** When the split counter was started, or -1 if it is not running */
    val splitDate: Long
        get() = prefs.getLong(SPLIT_DATE, -1)

    /** The total step count when the split counter was started, or null if it is not running */
    val splitSteps: Int?
        get() = if (prefs.contains(SPLIT_STEPS)) prefs.getInt(SPLIT_STEPS, 0) else null

    fun startSplit(date: Long, totalSteps: Int) =
        prefs.edit { putLong(SPLIT_DATE, date).putInt(SPLIT_STEPS, totalSteps) }

    fun stopSplit() = prefs.edit { remove(SPLIT_DATE).remove(SPLIT_STEPS) }

    /**
     * Whether the last shutdown sent the shutdown broadcast. Written synchronously, as the
     * process might not live long enough for an asynchronous write.
     */
    var correctShutdown: Boolean
        get() = prefs.getBoolean(CORRECT_SHUTDOWN, false)
        set(value) = prefs.edit(commit = true) {
            if (value) putBoolean(CORRECT_SHUTDOWN, true) else remove(CORRECT_SHUTDOWN)
        }

    /** The boot count the app last saw, or -1 if it never saw one */
    var bootCount: Int
        get() = prefs.getInt(BOOT_COUNT, -1)
        set(value) = prefs.edit { putInt(BOOT_COUNT, value) }

    /** Removes settings of features that no longer exist */
    fun removeObsolete() = prefs.edit { remove(PAUSE_COUNT) }

    companion object {
        const val FILE = "pedometer"
        const val DEFAULT_GOAL = 10000

        private const val GOAL = "goal"
        private const val STEP_SIZE = "stepsize_value"
        private const val STEP_UNIT = "stepsize_unit"
        private const val SPLIT_DATE = "split_date"
        private const val SPLIT_STEPS = "split_steps"
        private const val CORRECT_SHUTDOWN = "correctShutdown"
        private const val BOOT_COUNT = "bootCount"
        /** left over from the removed "pause" feature */
        private const val PAUSE_COUNT = "pauseCount"

        private val isUS: Boolean get() = Locale.getDefault().country == "US"

        @JvmStatic
        val defaultStepSize: Float get() = if (isUS) 2.5f else 75f

        @JvmStatic
        val defaultStepUnit: String get() = if (isUS) "ft" else "cm"
    }
}
