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

package de.j4velin.pedometer.background;

import java.util.Calendar;

import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.Logger;
import de.j4velin.pedometer.Util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NewDayReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {

		// Save the steps made yesterday and create a new
		// row for today
		if (Logger.LOG) {
			Logger.log("date changed, today is: " + Util.getToday());
			Logger.log("Steps: " + SensorListener.steps);
		}
		Database db = new Database(context);
		db.open();
		final Calendar yesterday = Calendar.getInstance();
		yesterday.setTimeInMillis(Util.getToday()); // today
		yesterday.add(Calendar.DAY_OF_YEAR, -1); // yesterday
		db.updateSteps(yesterday.getTimeInMillis(), SensorListener.steps);

		// if - for whatever reason - there still is a
		// negative value in
		// yesterdays steps, set it to 0 instead
		if (db.getSteps(yesterday.getTimeInMillis()) < 0) {
			db.updateSteps(yesterday.getTimeInMillis(),
					-db.getSteps(yesterday.getTimeInMillis()));
		}

		// start the new days step with the offset of the
		// current step-value
		// example: current steps since boot = 5.000
		// --> offset for the following day = -5.000
		// --> step-value of 5.001 then means there was 1
		// step taken today
		db.insertDay(Util.getToday(), -SensorListener.steps);
		if (Logger.LOG) {
			Logger.log("offset for new day: " + (-SensorListener.steps));
			db.logState();
		}
		db.close();

		// to update the notification
		context.startService(new Intent(context, SensorListener.class)
				.putExtra("updateNotificationState", true));

		sheduleAlarmForNextDay(context);
	}

	/**
	 * Shedules an alarm for tomorrow at midnight, to fire the NewDayReceiver
	 * broadcast again
	 * 
	 * @param context
	 *            the Context
	 */
	@SuppressWarnings("deprecation")
	public static void sheduleAlarmForNextDay(final Context context) {
		final Calendar tomorrow = Calendar.getInstance();
		tomorrow.setTimeInMillis(Util.getToday()); // today
		tomorrow.add(Calendar.DAY_OF_YEAR, 1); // tomorrow
		tomorrow.add(Calendar.SECOND, 1); // tomorrow at 0:00:01
		((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setExact(
				AlarmManager.RTC_WAKEUP, tomorrow.getTimeInMillis(), PendingIntent
						.getBroadcast(context, 1, new Intent(context,
								NewDayReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT));
		if (Logger.LOG)
			Logger.log("newDayAlarm sheduled for "
					+ tomorrow.getTime().toLocaleString());
	}

}
