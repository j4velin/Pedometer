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

package de.j4velin.pedometer.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import de.j4velin.pedometer.R;

abstract class Dialog_Split {

    private static boolean split_active;

    public static Dialog getDialog(final Context c, final int totalSteps) {
        final Dialog d = new Dialog(c);
        d.setTitle(R.string.split_count);
        d.setContentView(R.layout.dialog_split);

        final SharedPreferences prefs =
                c.getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS);
        long split_date = prefs.getLong("split_date", -1);
        int split_steps = prefs.getInt("split_steps", totalSteps);
        ((TextView) d.findViewById(R.id.steps))
                .setText(Fragment_Overview.formatter.format(totalSteps - split_steps));
        float stepsize = prefs.getFloat("stepsize_value", Fragment_Settings.DEFAULT_STEP_SIZE);
        float distance = (totalSteps - split_steps) * stepsize;
        if (prefs.getString("stepsize_unit", Fragment_Settings.DEFAULT_STEP_UNIT).equals("cm")) {
            distance /= 100000;
            ((TextView) d.findViewById(R.id.distanceunit)).setText("km");
        } else {
            distance /= 5280;
            ((TextView) d.findViewById(R.id.distanceunit)).setText("mi");
        }
        ((TextView) d.findViewById(R.id.distance))
                .setText(Fragment_Overview.formatter.format(distance));
        ((TextView) d.findViewById(R.id.date)).setText(c.getString(R.string.since,
                java.text.DateFormat.getDateTimeInstance().format(split_date)));

        final View started = d.findViewById(R.id.started);
        final View stopped = d.findViewById(R.id.stopped);

        split_active = split_date > 0;

        started.setVisibility(split_active ? View.VISIBLE : View.GONE);
        stopped.setVisibility(split_active ? View.GONE : View.VISIBLE);

        final Button startstop = (Button) d.findViewById(R.id.start);
        startstop.setText(split_active ? R.string.stop : R.string.start);
        startstop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (!split_active) {
                    prefs.edit().putLong("split_date", System.currentTimeMillis())
                            .putInt("split_steps", totalSteps).apply();
                    split_active = true;
                    d.dismiss();
                } else {
                    started.setVisibility(View.GONE);
                    stopped.setVisibility(View.VISIBLE);
                    prefs.edit().remove("split_date").remove("split_steps").apply();
                    split_active = false;
                }
                startstop.setText(split_active ? R.string.stop : R.string.start);
            }
        });

        d.findViewById(R.id.close).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                d.dismiss();
            }
        });

        return d;
    }
}
