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

import de.j4velin.pedometer.Logger;
import android.app.Service;
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

	private static Messenger messenger = new Messenger(new Handler() {
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
		return START_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (Logger.LOG)
			Logger.log("service created");
		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
		sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
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
	}
}
