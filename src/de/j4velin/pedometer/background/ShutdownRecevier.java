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

import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.Logger;
import de.j4velin.pedometer.Util;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

public class ShutdownRecevier extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (Logger.LOG)
			Logger.log("shutting down");

		// sensor stores steps since boot, so this value will be reset upon the
		// next boot and therefore has to be saved now
		Database db = new Database(context);
		db.open();
		db.insertSteps(Util.getToday(), SensorListener.steps);
		db.close();
		if (Logger.LOG)
			Logger.log("last step value before shutdown: " + SensorListener.steps);

		// if the user used a root script for shutdown, the DEVICE_SHUTDOWN
		// broadcast might not be send. Therefore, the app will check this
		// setting on the next boot and displays an error message if it's not
		// set to true
		PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("correctShutdown", true).commit();
	}

}
