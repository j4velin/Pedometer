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
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NewDayReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (Logger.LOG) {
			Logger.log("date changed, today is: " + Util.getToday());
			Logger.log("Steps: " + SensorListener.steps);
		}

		if (SensorListener.steps > 0) {
			// Save the steps made yesterday and create a new
			// row for today
			Database db = new Database(context);
			// start the new days step with the offset of the
			// current step-value.
			//
			// insertNewDay also updates the step value for yesterday
			db.insertNewDay(Util.getToday(), SensorListener.steps);
			db.close();

			sheduleAlarmForNextDay(context);

			// to update the notification
			context.startService(new Intent(context, SensorListener.class).putExtra("updateNotificationState", true));

		} else {
			// start the SensorListener first. SensorListener will then start
			// this Receiver again as it sees that there is no entry for today
			// yet
			context.startService(new Intent(context, SensorListener.class));
		}
	}

	/**
	 * Shedules an alarm for tomorrow at midnight, to fire the NewDayReceiver
	 * broadcast again
	 * 
	 * @param context
	 *            the Context
	 */
	@SuppressWarnings("deprecation")
	static void sheduleAlarmForNextDay(final Context context) {
		final Calendar tomorrow = Calendar.getInstance();
		tomorrow.setTimeInMillis(Util.getToday()); // today
		tomorrow.add(Calendar.DAY_OF_YEAR, 1); // tomorrow
		tomorrow.add(Calendar.SECOND, 1); // tomorrow at 0:00:01

		PendingIntent pi = PendingIntent.getBroadcast(context.getApplicationContext(), 10,
				new Intent(context.getApplicationContext(), NewDayReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
		AlarmManager am = ((AlarmManager) context.getApplicationContext().getSystemService(Context.ALARM_SERVICE));
		am.cancel(pi);
		am.setExact(AlarmManager.RTC_WAKEUP, tomorrow.getTimeInMillis(), pi);
		if (Logger.LOG)
			Logger.log("newDayAlarm sheduled for " + tomorrow.getTime().toLocaleString());
	}
}
