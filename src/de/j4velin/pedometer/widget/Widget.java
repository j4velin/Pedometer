package de.j4velin.pedometer.widget;

import de.j4velin.pedometer.MainActivity;
import de.j4velin.pedometer.R;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.RemoteViews;

public class Widget extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
			context.startService(new Intent(context, WidgetUpdateService.class));
		}
	}

	static RemoteViews updateWidget(final int appWidgetId, final Context context, final int steps) {
		final SharedPreferences prefs = context.getSharedPreferences("Widgets", Context.MODE_PRIVATE);
		final PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, new Intent(context,
				MainActivity.class), Intent.FLAG_ACTIVITY_NEW_TASK);

		final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
		views.setOnClickPendingIntent(R.id.widget, pendingIntent);
		views.setTextColor(R.id.widgetsteps, prefs.getInt("color_" + appWidgetId, Color.WHITE));
		views.setTextViewText(R.id.widgetsteps, String.valueOf(steps));
		views.setTextColor(R.id.widgettext, prefs.getInt("color_" + appWidgetId, Color.WHITE));
		views.setInt(R.id.widget, "setBackgroundColor", prefs.getInt("background_" + appWidgetId, Color.TRANSPARENT));
		return views;
	}

}
