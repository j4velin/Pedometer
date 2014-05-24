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

import java.util.Date;

import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;

public class Database extends SQLiteOpenHelper {

	private SQLiteDatabase database;

	private final static String DB_NAME = "steps";
	private final static int DB_VERSION = 1;

	public Database(final Context context, final String name, final CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	public Database(final Context context) {
		super(context, DB_NAME, null, DB_VERSION);
		database = getWritableDatabase();
	}

	@Override
	public void onCreate(final SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + DB_NAME + " (date INTEGER PRIMARY KEY, steps INTEGER)");
	}

	@Override
	public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	/**
	 * Inserts a new entry in the database, if there is no entry for the given
	 * date yet. Steps should be the current number of steps and it's negative
	 * value will be used as offset for the new date. Also adds 'steps' steps to
	 * the previous day, if there is an entry for that date.
	 * 
	 * This method does nothing if there is already an entry for 'date'- use
	 * {@link #updateSteps} in this case.
	 * 
	 * To restore data from a backup, use {@link #insertDayFromBackup}
	 * 
	 * @param date
	 *            the date in ms since 1970
	 * @param steps
	 *            the current step value to be used as negative offset for the
	 *            new day; must be >= 0
	 */
	public void insertNewDay(long date, int steps) {
		database.beginTransaction();
		try {
			Cursor c = database.query(DB_NAME, new String[] { "date" }, "date = ?", new String[] { String.valueOf(date) }, null,
					null, null);
			if (c.getCount() == 0 && steps >= 0) {
				ContentValues values = new ContentValues();
				values.put("date", date);
				// use the negative steps as offset
				values.put("steps", -steps);
				database.insert(DB_NAME, null, values);

				// add 'steps' to yesterdays count
				date -= 24 * 60 * 60 * 1000;
				updateSteps(date, steps);
			}
			c.close();
			if (Logger.LOG) {
				Logger.log("insertDay " + date + " / " + steps);
				logState();
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	/**
	 * Inserts a new entry in the database, if there is no entry for the given
	 * date yet. Use this method for restoring data from a backup.
	 * 
	 * This method does nothing if there is already an entry for 'date'.
	 * 
	 * @param date
	 *            the date in ms since 1970
	 * @param steps
	 *            the step value for 'date'; must be >= 0
	 * 
	 * @return true if a new entry was created, false if there was already an
	 *         entry for 'date'
	 */
	public boolean insertDayFromBackup(long date, int steps) {
		database.beginTransaction();
		boolean re;
		try {
			Cursor c = database.query(DB_NAME, new String[] { "date" }, "date = ?", new String[] { String.valueOf(date) }, null,
					null, null);
			re = c.getCount() == 0 && steps >= 0;
			if (re) {
				ContentValues values = new ContentValues();
				values.put("date", date);
				values.put("steps", steps);
				database.insert(DB_NAME, null, values);
			}
			c.close();
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
		return re;
	}

	/**
	 * Writes the current steps database to the log
	 */
	public void logState() {
		if (Logger.LOG) {
			Cursor c = database.query(DB_NAME, null, null, null, null, null, "date DESC", "5");
			Logger.log(c);
			c.close();
		}
	}

	/**
	 * Query the 'steps' table. Remember to close the cursor!
	 * 
	 * @param columns
	 * @param selection
	 * @param selectionArgs
	 * @param groupBy
	 * @param having
	 * @param orderBy
	 * @return the cursor
	 */
	Cursor query(final String[] columns, final String selection, final String[] selectionArgs, final String groupBy,
			final String having, final String orderBy, final String limit) {
		return database.query(DB_NAME, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
	}

	/**
	 * Adds 'steps' steps to the row for the date 'date'. Won't do anything if
	 * there isn't a row for the given date
	 * 
	 * @param date
	 *            the date to update the steps for in millis since 1970
	 * @param steps
	 *            the steps to add to the current steps-value for the date
	 */
	public void updateSteps(final long date, int steps) {
		database.execSQL("UPDATE " + DB_NAME + " SET steps = steps + " + steps + " WHERE date = " + date);
		if (Logger.LOG) {
			Logger.log("updateSteps " + date + " / " + steps);
			logState();
		}
	}

	/**
	 * Get the total of steps taken without today's value
	 * 
	 * @return number of steps taken, ignoring today
	 */
	int getTotalWithoutToday() {
		Cursor c = database
				.rawQuery("SELECT SUM(steps) FROM " + DB_NAME + " WHERE steps > 0 AND date < " + Util.getToday(), null);
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
	int getRecord() {
		Cursor c = database.rawQuery("SELECT MAX(steps) FROM " + DB_NAME, null);
		c.moveToFirst();
		int re = c.getInt(0);
		c.close();
		return re;
	}

	/**
	 * Get the maximum of steps walked in one day and the date that happend
	 * 
	 * @return a pair containing the date (Date) in millis since 1970 and the
	 *         step value (Integer)
	 */
	Pair<Date, Integer> getRecordData() {
		Cursor c = database.query(DB_NAME, new String[] { "date, steps" }, null, null, null, null, "steps DESC", "1");
		c.moveToFirst();
		Pair<Date, Integer> p = new Pair<Date, Integer>(new Date(c.getLong(0)), c.getInt(1));
		c.close();
		return p;
	}

	/**
	 * Get the number of steps taken for a specific date
	 * 
	 * @param date
	 *            the date in millis since 1970
	 * @return the steps taken on this date or Integer.MIN_VALUE if date doesn't
	 *         exist in the database
	 */
	public int getSteps(final long date) {
		Cursor c = database.query(DB_NAME, new String[] { "steps" }, "date = ?", new String[] { String.valueOf(date) }, null,
				null, null);
		c.moveToFirst();
		int re;
		if (c.getCount() == 0)
			re = Integer.MIN_VALUE;
		else
			re = c.getInt(0);
		c.close();
		return re;
	}

	/**
	 * Get the number of steps taken between 'start' and 'end' date
	 * 
	 * Note that todays entry might have a negative value, so take care of that
	 * if 'end' >= Util.getToday()!
	 * 
	 * @param start
	 *            start date in ms since 1970 (steps for this date included)
	 * @param end
	 *            end date in ms since 1970 (steps for this date included)
	 * @return the number of steps from 'start' to 'end'. Can be < 0 as todays
	 *         entry might have negative value
	 */
	public int getSteps(final long start, final long end) {
		Cursor c = database.query(DB_NAME, new String[] { "SUM(steps)" }, "date >= ? AND date <= ?",
				new String[] { String.valueOf(start), String.valueOf(end) }, null, null, null);
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
	 * 
	 * Only call this directly after boot, otherwise it might remove the current
	 * day as the current offset is likely to be negative
	 */
	void removeNegativeEntries() {
		database.delete(DB_NAME, "steps < ?", new String[] { "0" });
	}

	/**
	 * Removes invalid entries from the database.
	 * 
	 * Currently, an invalid input is such with steps >= 200,000
	 */
	void removeInvalidEntries() {
		database.delete(DB_NAME, "steps >= ?", new String[] { "200000" });
	}

	/**
	 * Get the number of 'valid' days (= days with a step value > 0).
	 * 
	 * The current day is also added to this number, even if the value in the
	 * database might still be < 0.
	 * 
	 * It is safe to divide by the return value as this will be at least 1 (and
	 * not 0).
	 * 
	 * @return the number of days with a step value > 0, return will be >= 1
	 */
	int getDays() {
		Cursor c = database.query(DB_NAME, new String[] { "COUNT(*)" }, "steps > ? AND date < ?",
				new String[] { String.valueOf(0), String.valueOf(Util.getToday()) }, null, null, null);
		c.moveToFirst();
		// todays is not counted yet
		int re = c.getInt(0) + 1;
		c.close();
		return re <= 0 ? 1 : re;
	}

}
