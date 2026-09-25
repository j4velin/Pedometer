/*
 * Copyright 2013 Thomas Hoffmann
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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import java.util.Date

/**
 * The step history: one table `steps (date INTEGER, steps INTEGER)` without a primary key.
 *
 * - a past day's row holds the steps taken on that day
 * - today's row holds the negative "steps since boot" value at the start of the day, so that
 *   adding the current sensor value gives today's steps
 * - the row with date -1 holds the last saved "steps since boot" value
 *
 * One instance lives as long as the process, see [de.j4velin.pedometer.PedometerApp].
 */
class StepsDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $DB_NAME (date INTEGER, steps INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1) {
            // drop PRIMARY KEY constraint
            db.execSQL("CREATE TABLE ${DB_NAME}2 (date INTEGER, steps INTEGER)")
            db.execSQL("INSERT INTO ${DB_NAME}2 (date, steps) SELECT date, steps FROM $DB_NAME")
            db.execSQL("DROP TABLE $DB_NAME")
            db.execSQL("ALTER TABLE ${DB_NAME}2 RENAME TO $DB_NAME")
        }
    }

    /** Queries the 'steps' table. Remember to close the cursor! */
    fun query(
        columns: Array<String>?, selection: String?, selectionArgs: Array<String>?,
        groupBy: String?, having: String?, orderBy: String?, limit: String?
    ): Cursor = readableDatabase
        .query(DB_NAME, columns, selection, selectionArgs, groupBy, having, orderBy, limit)

    /**
     * Inserts a new entry for [date], if there is none yet. [steps] is the current "steps since
     * boot" value: its negative becomes the offset of the new day, and it is added to the
     * previous day's entry.
     *
     * Does nothing if there already is an entry for [date] - use [addToLastEntry] in that case.
     * To restore data from a backup, use [insertDayFromBackup].
     *
     * @param date  the date in ms since 1970
     * @param steps the current step value; must be >= 0
     */
    fun insertNewDay(date: Long, steps: Int) {
        writableDatabase.transaction {
            val exists = DatabaseUtils.queryNumEntries(
                this, DB_NAME, "date = ?", arrayOf(date.toString())
            ) > 0
            if (!exists && steps >= 0) {
                // add 'steps' to yesterday's count
                addToLastEntry(steps)
                // add today, with the negative steps as offset
                insert(DB_NAME, null, ContentValues().apply {
                    put("date", date)
                    put("steps", -steps)
                })
            }
        }
    }

    /** Adds [steps] to the entry with the latest date */
    fun addToLastEntry(steps: Int) {
        writableDatabase.execSQL(
            "UPDATE $DB_NAME SET steps = steps + ? WHERE date = (SELECT MAX(date) FROM $DB_NAME)",
            arrayOf(steps)
        )
    }

    /**
     * Sets the entry for [date] to [steps], overwriting any existing entry. Used for restoring
     * data from a backup.
     *
     * @return true if a new entry was created, false if an existing one was overwritten
     */
    fun insertDayFromBackup(date: Long, steps: Int): Boolean {
        var newEntryCreated = false
        writableDatabase.transaction {
            val values = ContentValues().apply { put("steps", steps) }
            if (update(DB_NAME, values, "date = ?", arrayOf(date.toString())) == 0) {
                values.put("date", date)
                insert(DB_NAME, null, values)
                newEntryCreated = true
            }
        }
        return newEntryCreated
    }

    /** The steps taken on all days before [day] */
    fun totalBefore(day: Long): Int =
        queryInt("SUM(steps)", "steps > 0 AND date > 0 AND date < ?", day.toString())

    /** The number of days before [day] with more than 0 steps */
    fun daysBefore(day: Long): Int =
        queryInt("COUNT(*)", "steps > 0 AND date > 0 AND date < ?", day.toString())

    /** The number of days before [day] with at least [steps] steps */
    fun daysWithAtLeast(steps: Int, day: Long): Int =
        queryInt("COUNT(*)", "steps >= ? AND date > 0 AND date < ?", steps.toString(), day.toString())

    /** The most steps taken on one day */
    val record: Int
        get() = queryInt("MAX(steps)", "date > 0")

    /** The day with the most steps and its step count, or null if there are no days yet */
    val recordData: Pair<Date, Int>?
        get() = query(arrayOf("date", "steps"), "date > 0", null, null, null, "steps DESC", "1")
            .use { if (it.moveToFirst()) Date(it.getLong(0)) to it.getInt(1) else null }

    /**
     * The stored value for [date]. For a past day that is its step count; for today it is the
     * offset, which gives today's steps when added to the "steps since boot" value.
     *
     * @return the value, or Int.MIN_VALUE if there is no entry for [date]
     */
    fun getSteps(date: Long): Int =
        query(arrayOf("steps"), "date = ?", arrayOf(date.toString()), null, null, null, null)
            .use { if (it.moveToFirst()) it.getInt(0) else Int.MIN_VALUE }

    /** The last [num] days before [day] as date to steps, newest first */
    fun lastDaysBefore(day: Long, num: Int): List<Pair<Long, Int>> =
        query(
            arrayOf("date", "steps"), "date > 0 AND date < ?", arrayOf(day.toString()), null, null,
            "date DESC", num.toString()
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getInt(1)) } }

    /**
     * The steps taken from [start] to [end], both included. If the range includes today, the
     * result contains today's (negative) offset instead of today's steps.
     */
    fun getSteps(start: Long, end: Long): Int =
        queryInt("SUM(steps)", "date >= ? AND date <= ?", start.toString(), end.toString())

    /**
     * Removes all entries with negative values. Only call this right after a boot, as today's
     * offset is usually negative.
     */
    fun removeNegativeEntries() {
        writableDatabase.delete(DB_NAME, "steps < ?", arrayOf("0"))
    }

    /** Removes invalid entries, that is those with 200,000 steps or more */
    fun removeInvalidEntries() {
        writableDatabase.delete(DB_NAME, "steps >= ?", arrayOf("200000"))
    }

    /** Saves the current "steps since boot" sensor value */
    fun saveCurrentSteps(steps: Int) {
        val values = ContentValues().apply { put("steps", steps) }
        if (writableDatabase.update(DB_NAME, values, "date = -1", null) == 0) {
            values.put("date", -1)
            writableDatabase.insert(DB_NAME, null, values)
        }
    }

    /** The last saved "steps since boot" value, or 0 if there is none */
    val currentSteps: Int
        get() = getSteps(-1).let { if (it == Int.MIN_VALUE) 0 else it }

    /** The first column of the first row; 0 for NULL, as an aggregate over no rows returns */
    private fun queryInt(column: String, selection: String?, vararg args: String): Int =
        query(arrayOf(column), selection, args.takeIf { it.isNotEmpty() }?.let { arrayOf(*it) },
            null, null, null, null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private companion object {
        const val DB_NAME = "steps"
        const val DB_VERSION = 2
    }
}
