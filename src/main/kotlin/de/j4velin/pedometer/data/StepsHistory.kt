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

import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The history before a day, as the screens and the achievements need it */
data class HistorySummary(
    /** The steps of all days before */
    val total: Int,
    /** The number of days with steps before */
    val days: Int,
    /** The last seven days before, oldest first, with their steps */
    val lastWeek: List<Pair<Long, Int>>,
)

/**
 * Reads the finished days of the step history, off the main thread. Today's steps are not part
 * of it: they come from [de.j4velin.pedometer.domain.StepAccounting.today].
 *
 * @param io the dispatcher the queries run on
 */
class StepsHistory(private val db: StepsDatabase, private val io: CoroutineDispatcher) {

    suspend fun summary(today: Long): HistorySummary = withContext(io) {
        HistorySummary(
            total = db.totalBefore(today),
            days = db.daysBefore(today),
            lastWeek = db.lastDaysBefore(today, 7).reversed(),
        )
    }

    /** The day with the most steps and its step count, or null if there are no days yet */
    suspend fun record(): Pair<Date, Int>? = withContext(io) { db.recordData }

    /** The steps of the days from [start] up to [today], without today */
    suspend fun stepsSince(start: Long, today: Long): Int =
        withContext(io) { db.getSteps(start, today - 1) }

    /** The number of days before [today] with at least [steps] steps */
    suspend fun daysWithAtLeast(steps: Int, today: Long): Int =
        withContext(io) { db.daysWithAtLeast(steps, today) }

    /** The most steps taken on one day */
    suspend fun recordSteps(): Int = withContext(io) { db.record }

    /** Removes entries of 200,000 steps or more, which can not be real */
    suspend fun removeInvalidEntries() = withContext(io) { db.removeInvalidEntries() }
}
