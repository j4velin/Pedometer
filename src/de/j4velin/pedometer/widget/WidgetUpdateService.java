package de.j4velin.pedometer.widget;

import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.Logger;
import de.j4velin.pedometer.Util;
import de.j4velin.pedometer.background.SensorListener;
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
							int steps = msg.arg1 + db.getSteps(Util.getToday());
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
