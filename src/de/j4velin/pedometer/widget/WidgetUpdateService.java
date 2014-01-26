/*
 * Copyright 2014 Thomas Hoffmann
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

import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.background.SensorListener;
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;
import android.annotation.SuppressLint;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class WidgetUpdateService extends Service {

	@Override
	public IBinder onBind(final Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
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
								Logger.log("SensorListener.steps for widget: " + msg.arg1);
							Database db = new Database(WidgetUpdateService.this);
							db.open();
							int steps = Math.max(msg.arg1 + db.getSteps(Util.getToday()),0);
							db.close();
							unbindService(conn);
							final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(WidgetUpdateService.this);
							int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(WidgetUpdateService.this,
									Widget.class));
							for (int appWidgetId : appWidgetIds) {
								appWidgetManager.updateAppWidget(appWidgetId,
										Widget.updateWidget(appWidgetId, WidgetUpdateService.this, steps));
							}
							stopSelf();
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
		return START_NOT_STICKY;
	}

}
