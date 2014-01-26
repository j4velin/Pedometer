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

package de.j4velin.pedometer.widget;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.MainActivity;
import de.j4velin.pedometer.OverviewFragment;
import de.j4velin.pedometer.R;
import de.j4velin.pedometer.background.SensorListener;
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;

/**
 * Class for providing a DashClock (https://code.google.com/p/dashclock)
 * extension
 * 
 */
public class DashClock extends DashClockExtension {

	@Override
	protected void onUpdateData(int reason) {
		bindService(new Intent(this, SensorListener.class), new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {

			}

			@SuppressLint("HandlerLeak")
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				Messenger messenger = new Messenger(service);
				try {
					final ServiceConnection conn = this;
					Messenger incoming = new Messenger(new Handler() {
						public void handleMessage(Message msg) {
							if (Logger.LOG)
								Logger.log("SensorListener.steps for dashclock: " + msg.arg1);
							ExtensionData data = new ExtensionData();
							Database db = new Database(DashClock.this);
							db.open();
							int steps = msg.arg1 + db.getSteps(Util.getToday());
							data.visible(true).status(OverviewFragment.formatter.format(steps)).icon(R.drawable.ic_dashclock)
									.clickIntent(new Intent(DashClock.this, MainActivity.class));
							db.close();
							publishUpdate(data);
							unbindService(conn);
						}
					});
					Message msg = Message.obtain();
					msg.replyTo = incoming;
					messenger.send(msg);
				} catch (RemoteException e) {
					if (Logger.LOG)
						Logger.log(e);
					e.printStackTrace();
				}
			}
		}, 0);
	}

}
