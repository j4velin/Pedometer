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
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {

	private SQLiteDatabase database;

	private final static String DB_NAME = "steps";
	private final static int DB_VERSION = 1;

	public Database(final Context context, final String name, final CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	public Database(final Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	public void open() {
		database = getWritableDatabase();
	}

	public void close() {
		database.close();
	}

	@Override
	public void onCreate(final SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + DB_NAME + " (date INTEGER PRIMARY KEY, steps INTEGER)");
	}

	@Override
	public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	/**
	 * Inserts a new entry in the database, if there is no entry for the given date yet.
	 * Use updateSteps(long date, int steps) if an entry for this date already exists.
	 * 
	 * @param date the date in ms since 1970
	 * @param steps the steps for this date
	 */
	public void insertDay(final long date, int steps) {
		Cursor c = database.query(DB_NAME, new String[] { "date" }, "date = ?", new String[] { String.valueOf(date) }, null,
				null, null);
		if (c.getCount() == 0) {
			ContentValues values = new ContentValues();
			values.put("date", date);
			values.put("steps", steps);
			database.insert(DB_NAME, null, values);
		}
		c.close();
		if (Logger.LOG) {
			Logger.log("insertDay " + date + " / " + steps);
			logState();
		}
	}

	/**
	 * Writes the current steps database to the log
	 */
	public void logState() {
		if (Logger.LOG) {
			Cursor c = database.query(DB_NAME, null, null, null, null, null, null);
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
	 * Adds 'steps' steps to the row for the date 'date'
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
	 * Get the number of steps taken for a specific date
	 *  
	 * @param date
	 *            the date in millis since 1970
	 * @return the steps taken on this date or Integer.MIN_VALUE if date doesn't
	 *         exist in the database
	 */
	public int getSteps(final long date) {
		Cursor c = database.query("steps", new String[] { "steps" }, "date = ?", new String[] { String.valueOf(date) }, null,
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
	 * Removes all entries with negative values.
	 * 
	 * Only call this directly after boot, otherwise it might remove the current
	 * day as the current offset is likely to be negative
	 */
	void removeNegativeEntries() {
		database.delete("steps", "steps < ?", new String[] { "0" });
	}

	/**
	 * Removes invalid entries from the database.
	 * 
	 * Currently, an invalid input is such with steps >= 2,000,000,000
	 */
	void removeInvalidEntries() {
		database.delete("steps", "steps >= ?", new String[] { "2000000000" });
	}
}
