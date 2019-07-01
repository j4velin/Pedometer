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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.RemoteViews;

import de.j4velin.pedometer.R;
import de.j4velin.pedometer.ui.Activity_Main;

public class Widget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdateService.enqueueUpdate(context);
    }

    static RemoteViews updateWidget(final int appWidgetId, final Context context, final int steps) {
        final SharedPreferences prefs =
                context.getSharedPreferences("Widgets", Context.MODE_PRIVATE);
        final PendingIntent pendingIntent = PendingIntent
                .getActivity(context, appWidgetId, new Intent(context, Activity_Main.class), 0);

        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        views.setOnClickPendingIntent(R.id.widget, pendingIntent);
        views.setTextColor(R.id.widgetsteps, prefs.getInt("color_" + appWidgetId, Color.WHITE));
        views.setTextViewText(R.id.widgetsteps, String.valueOf(steps));
        views.setTextColor(R.id.widgettext, prefs.getInt("color_" + appWidgetId, Color.WHITE));
        views.setInt(R.id.widget, "setBackgroundColor",
                prefs.getInt("background_" + appWidgetId, Color.TRANSPARENT));
        return views;
    }

}
