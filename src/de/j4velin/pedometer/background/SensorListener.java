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

import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.MainActivity;
import de.j4velin.pedometer.R;
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;

/**
 * Background service which keeps the step-sensor listener alive to always get
 * the number of steps since boot.
 * 
 * This service won't be needed any more if there is a way to read the
 * step-value without waiting for a sensor event
 * 
 */
public class SensorListener extends Service implements SensorEventListener {

	/**
	 * The steps since boot as returned by the step-sensor
	 */
	static int steps;

	/*
	 * The notification builder to create and update the progress-notification
	 */
	private static Notification.Builder notificationBuilder;

	/*
	 * The goal & offset, needed for the notification progress
	 */
	private static int goal, today_offset;

	private static Messenger messenger = new Messenger(new Handler() {
		// received a message, reply with the current step value
		public void handleMessage(Message msg) {
			Message m = Message.obtain();
			m.arg1 = steps;
			try {
				msg.replyTo.send(m);
			} catch (RemoteException e) {
				if (Logger.LOG)
					Logger.log(e);
				e.printStackTrace();
			}
		};
	});

	@Override
	public void onAccuracyChanged(final Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(final SensorEvent event) {
		if (event.values[0] == 0) // unlikly to be a real value
			return;
		steps = (int) event.values[0];

		// update only every 100 steps
		if (notificationBuilder != null && steps % 100 == 0) {
			if (today_offset == Integer.MIN_VALUE)
				today_offset = -steps;
			if (today_offset + steps < goal) {
				notificationBuilder.setProgress(goal, today_offset + steps, false).setContentText(
						NumberFormat.getInstance(Locale.getDefault()).format((goal - today_offset - steps)) + " steps to go");
			} else {
				notificationBuilder.setContentText("Goal reached! "
						+ NumberFormat.getInstance(Locale.getDefault()).format((today_offset + steps)) + " steps and counting");
			}
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, notificationBuilder.build());
		}

	}

	@Override
	public IBinder onBind(final Intent intent) {
		return messenger.getBinder();
	}

	@Override
	public int onStartCommand(final Intent intent, int flags, int startId) {
		if (Logger.LOG)
			Logger.log("service started. steps: " + steps + " intent=null? " + (intent == null) + " flags: " + flags
					+ " startid: " + startId);
		if (intent.getBooleanExtra("updateNotificationState", false)) {
			updateNotificationState();
		}

		NewDayReceiver.sheduleAlarmForNextDay(this);
		
		// check if NewDayReceiver was called for the current day
		Database db = new Database(this);
		db.open();
		int steps_today = db.getSteps(Util.getToday());
		db.close();
		if (steps_today == Integer.MIN_VALUE) {
			// no entry for today yet
			sendBroadcast(new Intent(this, NewDayReceiver.class));
		}

		return START_STICKY;
	}

	/**
	 * Creates/cancels the progress notification. Is also called to update the
	 * goal and today_offset values (for example at midnight)
	 */
	private void updateNotificationState() {
		if (getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS).getBoolean("notification", true)) {
			goal = PreferenceManager.getDefaultSharedPreferences(this).getInt("goal", 10000);
			Database db = new Database(this);
			db.open();
			today_offset = db.getSteps(Util.getToday());
			db.close();
			notificationBuilder = new Notification.Builder(this);
			if (steps > 0) {
				if (today_offset == Integer.MIN_VALUE)
					today_offset = -steps;
				notificationBuilder.setProgress(goal, today_offset + steps, false).setContentText(
						NumberFormat.getInstance(Locale.getDefault()).format((goal - today_offset - steps)) + " steps to go");
			} else {
				notificationBuilder.setContentText("Your progress will be shown here soon");
			}
			notificationBuilder.setPriority(Notification.PRIORITY_MIN).setShowWhen(false)
					.setContentTitle("Pedometer is counting")
					.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0))
					.setSmallIcon(R.drawable.ic_launcher).build();

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

	@Override
	public void onCreate() {
		super.onCreate();
		if (Logger.LOG)
			Logger.log("service created");
		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
		sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);

		updateNotificationState();
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
			Logger.log("service destroyed. steps: " + steps);
		try {
			SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
			sm.unregisterListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}

		stopForeground(true);
	}
}
