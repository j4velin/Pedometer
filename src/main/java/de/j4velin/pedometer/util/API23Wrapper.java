/*
 * Copyright 2016 Thomas Hoffmann
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

package de.j4velin.pedometer.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.M)
public class API23Wrapper {

    public static void requestPermission(final Activity a, final String[] permissions) {
        a.requestPermissions(permissions, 42);
    }

    public static void setAlarmWhileIdle(AlarmManager am, int type, long time,
                                         PendingIntent intent) {
        am.setAndAllowWhileIdle(type, time, intent);
    }

}
