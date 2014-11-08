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

import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import de.j4velin.pedometer.util.Logger;

public class BootReceiver extends BroadcastReceiver {

    private final static int ERROR_NOTIFICATION_ID = 2;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (BuildConfig.DEBUG) Logger.log("booted");

        SharedPreferences prefs =
                context.getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS);

        Database db = Database.getInstance(context);

        if (!prefs.getBoolean("correctShutdown", false)) {
            if (BuildConfig.DEBUG) Logger.log("Incorrect shutdown");
            // DEVICE_SHUTDOWN was not sent on shutdown -> display error message
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                    .notify(ERROR_NOTIFICATION_ID,
                            new Builder(context).setContentTitle("Incorrect shutdown")
                                    .setContentText(
                                            "Use the power button to shutdown the device, otherwise the app might not be able to save your steps!")
                                    .setSubText("Click for more information").setAutoCancel(true)
                                    .setContentIntent(PendingIntent.getActivity(context, 0,
                                            new Intent(Intent.ACTION_VIEW, Uri.parse(
                                                    "http://j4velin.de/faq/index.php?app=pm"))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0))
                                    .setShowWhen(false).setSmallIcon(R.drawable.ic_notification)
                                    .build());
            // can we at least recover some steps?
            int steps = db.getCurrentSteps();
            long date = db.getLastDay();
            if (BuildConfig.DEBUG)
                Logger.log("Trying to recover " + (db.getSteps(date) + steps) + " steps");
            db.updateSteps(date, steps);
        }
        // last entry might still have a negative step value, so remove that
        // row if that's the case
        db.removeNegativeEntries();
        db.saveCurrentSteps(0);
        db.close();
        prefs.edit().remove("correctShutdown").apply();

        context.startService(new Intent(context, SensorListener.class));
    }
}
