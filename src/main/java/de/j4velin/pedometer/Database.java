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

package de.j4velin.pedometer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;

public class Database extends SQLiteOpenHelper {

    private final static String DB_NAME = "steps";
    private final static int DB_VERSION = 2;

    private static Database instance;
    private static final AtomicInteger openCounter = new AtomicInteger();

    private Database(final Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public static synchronized Database getInstance(final Context c) {
        if (instance == null) {
            instance = new Database(c.getApplicationContext());
        }
        openCounter.incrementAndGet();
        return instance;
    }

    @Override
    public void close() {
        if (openCounter.decrementAndGet() == 0) {
            super.close();
        }
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + DB_NAME + " (date INTEGER, steps INTEGER)");
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            // drop PRIMARY KEY constraint
            db.execSQL("CREATE TABLE " + DB_NAME + "2 (date INTEGER, steps INTEGER)");
            db.execSQL("INSERT INTO " + DB_NAME + "2 (date, steps) SELECT date, steps FROM " +
                    DB_NAME);
            db.execSQL("DROP TABLE " + DB_NAME);
            db.execSQL("ALTER TABLE " + DB_NAME + "2 RENAME TO " + DB_NAME + "");
        }
    }

    /**
     * Query the 'steps' table. Remember to close the cursor!
     *
     * @param columns       the colums
     * @param selection     the selection
     * @param selectionArgs the selction arguments
     * @param groupBy       the group by statement
     * @param having        the having statement
     * @param orderBy       the order by statement
     * @return the cursor
     */
    public Cursor query(final String[] columns, final String selection,
                        final String[] selectionArgs, final String groupBy, final String having,
                        final String orderBy, final String limit) {
        return getReadableDatabase()
                .query(DB_NAME, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    }

    /**
     * Inserts a new entry in the database, if there is no entry for the given
     * date yet. Steps should be the current number of steps and it's negative
     * value will be used as offset for the new date. Also adds 'steps' steps to
     * the previous day, if there is an entry for that date.
     * <p/>
     * This method does nothing if there is already an entry for 'date' - use
     * {@link #updateSteps} in this case.
     * <p/>
     * To restore data from a backup, use {@link #insertDayFromBackup}
     *
     * @param date  the date in ms since 1970
     * @param steps the current step value to be used as negative offset for the
     *              new day; must be >= 0
     */
    public void insertNewDay(long date, int steps) {
        getWritableDatabase().beginTransaction();
        try {
            Cursor c = getReadableDatabase().query(DB_NAME, new String[]{"date"}, "date = ?",
                    new String[]{String.valueOf(date)}, null, null, null);
            if (c.getCount() == 0 && steps >= 0) {

                // add 'steps' to yesterdays count
                addToLastEntry(steps);

                // add today
                ContentValues values = new ContentValues();
                values.put("date", date);
                // use the negative steps as offset
                values.put("steps", -steps);
                getWritableDatabase().insert(DB_NAME, null, values);
            }
            c.close();
            if (BuildConfig.DEBUG) {
                Logger.log("insertDay " + date + " / " + steps);
                logState();
            }
            getWritableDatabase().setTransactionSuccessful();
        } finally {
            getWritableDatabase().endTransaction();
        }
    }

    /**
     * Adds the given number of steps to the last entry in the database
     *
     * @param steps the number of steps to add
     */
    public void addToLastEntry(int steps) {
        getWritableDatabase().execSQL("UPDATE " + DB_NAME + " SET steps = steps + " + steps +
                " WHERE date = (SELECT MAX(date) FROM " + DB_NAME + ")");
    }

    /**
     * Inserts a new entry in the database, overwriting any existing entry for the given date.
     * Use this method for restoring data from a backup.
     *
     * @param date  the date in ms since 1970
     * @param steps the step value for 'date'; must be >= 0
     * @return true if a new entry was created, false if there was already an
     * entry for 'date' (and it was overwritten)
     */
    public boolean insertDayFromBackup(long date, int steps) {
        getWritableDatabase().beginTransaction();
        boolean newEntryCreated = false;
        try {
            ContentValues values = new ContentValues();
            values.put("steps", steps);
            int updatedRows = getWritableDatabase()
                    .update(DB_NAME, values, "date = ?", new String[]{String.valueOf(date)});
            if (updatedRows == 0) {
                values.put("date", date);
                getWritableDatabase().insert(DB_NAME, null, values);
                newEntryCreated = true;
            }
            getWritableDatabase().setTransactionSuccessful();
        } finally {
            getWritableDatabase().endTransaction();
        }
        return newEntryCreated;
    }

    /**
     * Writes the current steps database to the log
     */
    public void logState() {
        if (BuildConfig.DEBUG) {
            Cursor c = getReadableDatabase()
                    .query(DB_NAME, null, null, null, null, null, "date DESC", "5");
            Logger.log(c);
            c.close();
        }
    }

    /**
     * Get the total of steps taken without today's value
     *
     * @return number of steps taken, ignoring today
     */
    public int getTotalWithoutToday() {
        Cursor c = getReadableDatabase()
                .query(DB_NAME, new String[]{"SUM(steps)"}, "steps > 0 AND date > 0 AND date < ?",
                        new String[]{String.valueOf(Util.getToday())}, null, null, null);
        c.moveToFirst();
        int re = c.getInt(0);
        c.close();
        return re;
    }

    /**
     * Get the maximum of steps walked in one day
     *
     * @return the maximum number of steps walked in one day
     */
    public int getRecord() {
        Cursor c = getReadableDatabase()
                .query(DB_NAME, new String[]{"MAX(steps)"}, "date > 0", null, null, null, null);
        c.moveToFirst();
        int re = c.getInt(0);
        c.close();
        return re;
    }

    /**
     * Get the maximum of steps walked in one day and the date that happend
     *
     * @return a pair containing the date (Date) in millis since 1970 and the
     * step value (Integer)
     */
    public Pair<Date, Integer> getRecordData() {
        Cursor c = getReadableDatabase()
                .query(DB_NAME, new String[]{"date, steps"}, "date > 0", null, null, null,
                        "steps DESC", "1");
        c.moveToFirst();
        Pair<Date, Integer> p = new Pair<Date, Integer>(new Date(c.getLong(0)), c.getInt(1));
        c.close();
        return p;
    }

    /**
     * Get the number of steps taken for a specific date.
     * <p/>
     * If date is Util.getToday(), this method returns the offset which needs to
     * be added to the value returned by getCurrentSteps() to get todays steps.
     *
     * @param date the date in millis since 1970
     * @return the steps taken on this date or Integer.MIN_VALUE if date doesn't
     * exist in the database
     */
    public int getSteps(final long date) {
        Cursor c = getReadableDatabase().query(DB_NAME, new String[]{"steps"}, "date = ?",
                new String[]{String.valueOf(date)}, null, null, null);
        c.moveToFirst();
        int re;
        if (c.getCount() == 0) re = Integer.MIN_VALUE;
        else re = c.getInt(0);
        c.close();
        return re;
    }

    /**
     * Gets the last num entries in descending order of date (newest first)
     *
     * @param num the number of entries to get
     * @return a list of long,integer pair - the first being the date, the second the number of steps
     */
    public List<Pair<Long, Integer>> getLastEntries(int num) {
        Cursor c = getReadableDatabase()
                .query(DB_NAME, new String[]{"date", "steps"}, "date > 0", null, null, null,
                        "date DESC", String.valueOf(num));
        int max = c.getCount();
        List<Pair<Long, Integer>> result = new ArrayList<>(max);
        if (c.moveToFirst()) {
            do {
                result.add(new Pair<>(c.getLong(0), c.getInt(1)));
            } while (c.moveToNext());
        }
        return result;
    }

    /**
     * Get the number of steps taken between 'start' and 'end' date
     * <p/>
     * Note that todays entry might have a negative value, so take care of that
     * if 'end' >= Util.getToday()!
     *
     * @param start start date in ms since 1970 (steps for this date included)
     * @param end   end date in ms since 1970 (steps for this date included)
     * @return the number of steps from 'start' to 'end'. Can be < 0 as todays
     * entry might have negative value
     */
    public int getSteps(final long start, final long end) {
        Cursor c = getReadableDatabase()
                .query(DB_NAME, new String[]{"SUM(steps)"}, "date >= ? AND date <= ?",
                        new String[]{String.valueOf(start), String.valueOf(end)}, null, null, null);
        int re;
        if (c.getCount() == 0) {
            re = 0;
        } else {
            c.moveToFirst();
            re = c.getInt(0);
        }
        c.close();
        return re;
    }

    /**
     * Removes all entries with negative values.
     * <p/>
     * Only call this directly after boot, otherwise it might remove the current
     * day as the current offset is likely to be negative
     */
    void removeNegativeEntries() {
        getWritableDatabase().delete(DB_NAME, "steps < ?", new String[]{"0"});
    }

    /**
     * Removes invalid entries from the database.
     * <p/>
     * Currently, an invalid input is such with steps >= 200,000
     */
    public void removeInvalidEntries() {
        getWritableDatabase().delete(DB_NAME, "steps >= ?", new String[]{"200000"});
    }

    /**
     * Get the number of 'valid' days (= days with a step value > 0).
     * <p/>
     * The current day is not added to this number.
     *
     * @return the number of days with a step value > 0, return will be >= 0
     */
    public int getDaysWithoutToday() {
        Cursor c = getReadableDatabase()
                .query(DB_NAME, new String[]{"COUNT(*)"}, "steps > ? AND date < ? AND date > 0",
                        new String[]{String.valueOf(0), String.valueOf(Util.getToday())}, null,
                        null, null);
        c.moveToFirst();
        int re = c.getInt(0);
        c.close();
        return re < 0 ? 0 : re;
    }

    /**
     * Get the number of 'valid' days (= days with a step value > 0).
     * <p/>
     * The current day is also added to this number, even if the value in the
     * database might still be < 0.
     * <p/>
     * It is safe to divide by the return value as this will be at least 1 (and
     * not 0).
     *
     * @return the number of days with a step value > 0, return will be >= 1
     */
    public int getDays() {
        // todays is not counted yet
        int re = this.getDaysWithoutToday() + 1;
        return re;
    }

    /**
     * Saves the current 'steps since boot' sensor value in the database.
     *
     * @param steps since boot
     */
    public void saveCurrentSteps(int steps) {
        ContentValues values = new ContentValues();
        values.put("steps", steps);
        if (getWritableDatabase().update(DB_NAME, values, "date = -1", null) == 0) {
            values.put("date", -1);
            getWritableDatabase().insert(DB_NAME, null, values);
        }
        if (BuildConfig.DEBUG) {
            Logger.log("saving steps in db: " + steps);
        }
    }

    /**
     * Reads the latest saved value for the 'steps since boot' sensor value.
     *
     * @return the current number of steps saved in the database or 0 if there
     * is no entry
     */
    public int getCurrentSteps() {
        int re = getSteps(-1);
        return re == Integer.MIN_VALUE ? 0 : re;
    }
}
