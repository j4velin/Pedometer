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

import de.j4velin.pedometer.background.NewDayReceiver;
import de.j4velin.pedometer.background.SensorListener;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (Logger.LOG)
			Logger.log("booted");
		Database db = new Database(context);
		db.open();
		db.insertSteps(Util.getToday(), 0); // device just booted; wont do
											// anything if there is already a
											// row for today
		NewDayReceiver.sheduleAlarmForNextDay(context);

		context.startService(new Intent(context, SensorListener.class));

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (!prefs.getBoolean("correctShutdown", false)) {
			// DEVICE_SHUTDOWN was not sent on shutdown -> display error message
			((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
					1,
					new Builder(context)
							.setContentTitle("Incorrect shutdown")
							.setContentText(
									"Use the power button to shutdown the device, otherwise the app can not save your steps!")
							.setSubText("Click for more information")
							.setAutoCancel(true)
							.setContentIntent(
									PendingIntent.getActivity(
											context,
											0,
											new Intent(Intent.ACTION_VIEW, Uri
													.parse("http://j4velin-systems.de/faq/index.php?app=pm"))
													.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0)).setShowWhen(false)
							.setSmallIcon(R.drawable.ic_launcher).build());

			// last entry might still have a negative step value, so remove that
			// row if that's the case
			db.removeNegativeEntries();
		}
		db.close();
		prefs.edit().remove("correctShutdown").apply();

	}
}
