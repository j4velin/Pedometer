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

import java.text.NumberFormat;
import java.util.Locale;

import de.j4velin.pedometer.Activity_Main;
import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.R;
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;

/**
 * Background service which keeps the step-sensor listener alive to always get
 * the number of steps since boot.
 * 
 * This service won't be needed any more if there is a way to read the
 * step-value without waiting for a sensor event
 * 
 */
public class SensorListener extends Service implements SensorEventListener {

	private static int steps;

	@Override
	public void onAccuracyChanged(final Sensor sensor, int accuracy) {
		// nobody knows what happens here: step value might magically decrease
		// when this method is called...
		if (Logger.LOG)
			Logger.log(sensor.getName() + " accuracy changed: " + accuracy);
	}

	@Override
	public void onSensorChanged(final SensorEvent event) {
		steps = (int) event.values[0];
	}

	private void insertNewDay(int offset) {
		if (Logger.LOG)
			Logger.log("new day, saving offset: " + offset);
		Database db = new Database(this);
		db.insertNewDay(Util.getToday(), offset);
		db.close();
	}

	@Override
	public IBinder onBind(final Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(final Intent intent, int flags, int startId) {

		// restart service every hour to get the current step count
		((AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, System
				.currentTimeMillis() + 1000 * 60 * 60, PendingIntent.getService(getApplicationContext(), 2, new Intent(this,
				SensorListener.class), PendingIntent.FLAG_UPDATE_CURRENT));

		if (Logger.LOG)
			Logger.log("saving steps: " + steps);
		SharedPreferences prefs = getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS);
		long today = Util.getToday();
		if (today != prefs.getLong("date", 0)) {
			insertNewDay(steps);
		}
		prefs.edit().putInt("steps", steps).putLong("date", today).apply();

		updateNotificationState();

		return START_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (Logger.LOG)
			Logger.log("SensorListener onCreate");
		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		try {
			sm.unregisterListener(this);
		} catch (Exception e) {
			if (Logger.LOG)
				Logger.log(e);
			e.printStackTrace();
		}

		sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	public void onTaskRemoved(final Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		if (Logger.LOG)
			Logger.log("sensor service task removed");
		// Restart service in 500 ms
		((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, System.currentTimeMillis() + 500,
				PendingIntent.getService(this, 3, new Intent(this, SensorListener.class), 0));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Logger.LOG)
			Logger.log("SensorListener onDestroy");
		try {
			SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
			sm.unregisterListener(this);
		} catch (Exception e) {
			if (Logger.LOG)
				Logger.log(e);
			e.printStackTrace();
		}
	}

	private void updateNotificationState() {
		SharedPreferences prefs = getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS);
		if (prefs.getBoolean("notification", true)) {
			int goal = prefs.getInt("goal", 10000);
			Database db = new Database(this);
			int today_offset = db.getSteps(Util.getToday());
			db.close();
			Notification.Builder notificationBuilder = new Notification.Builder(this);
			if (steps > 0) {
				if (today_offset == Integer.MIN_VALUE)
					today_offset = -steps;
				notificationBuilder.setProgress(goal, today_offset + steps, false).setContentText(
						getString(R.string.notification_text,
								NumberFormat.getInstance(Locale.getDefault()).format((goal - today_offset - steps))));
			} else {
				notificationBuilder.setContentText(getString(R.string.your_progress_will_be_shown_here_soon));
			}
			notificationBuilder
					.setPriority(Notification.PRIORITY_MIN)
					.setShowWhen(false)
					.setContentTitle(getString(R.string.notification_title))
					.setContentIntent(
							PendingIntent.getActivity(this, 0, new Intent(this, Activity_Main.class),
									Intent.FLAG_ACTIVITY_NEW_TASK)).setSmallIcon(R.drawable.ic_launcher).build();

			// Workaround as on Android 4.4.2 START_STICKY has currently no
			// effect
			// -> try keeping the service in memory by making it a foreground
			// service
			startForeground(1, notificationBuilder.build());

			if (Logger.LOG)
				Logger.log("start foreground");

		} else {
			stopForeground(true);
		}
	}
}
