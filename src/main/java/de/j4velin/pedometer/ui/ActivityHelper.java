/*
 * Copyright 2026 Thomas Hoffmann
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

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

import de.j4velin.pedometer.R;
import de.j4velin.pedometer.SensorListener;

/**
 * Setup shared by the Activity_Main of all flavors
 */
abstract class ActivityHelper {

    private final static int REQUEST_PERMISSIONS = 100;

    /**
     * Requests the runtime permissions the step counter service needs (or starts the service if
     * they are already granted) and keeps the content out of the system bars, which the app draws
     * behind since targeting Android 15
     *
     * @param savedInstanceState the activity's saved state: the permissions are only requested
     *                           when the activity is created for the first time, not when it is
     *                           recreated (for example after a rotation)
     */
    static void onCreate(final ComponentActivity activity, final Bundle savedInstanceState) {
        // the fragments are framework fragments, so their back stack is handled here
        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (activity.getFragmentManager().getBackStackEntryCount() > 0) {
                    activity.getFragmentManager().popBackStackImmediate();
                } else {
                    activity.finish();
                }
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
                    // the top inset includes the height of the action bar
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        List<String> missing = new ArrayList<>();
        if (!SensorListener.hasPermission(activity)) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (missing.isEmpty()) {
            SensorListener.start(activity);
        } else if (savedInstanceState == null) {
            activity.requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    static void onRequestPermissionsResult(final ComponentActivity activity, int requestCode) {
        if (requestCode != REQUEST_PERMISSIONS) return;
        if (SensorListener.hasPermission(activity)) {
            SensorListener.start(activity);
        } else {
            Toast.makeText(activity, R.string.permission_activity_recognition, Toast.LENGTH_LONG)
                    .show();
        }
    }
}
