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

package de.j4velin.pedometer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.j4velin.pedometer.util.Logger

class AppUpdatedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // the receiver is exported, so other apps could send anything to it
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (BuildConfig.DEBUG) Logger.log("app updated")
        PedometerApp.get(context).accounting.onAppUpdated(BootReceiver.getBootCount(context))
        STALE_PREFERENCES.forEach { context.deleteSharedPreferences(it) }
        SensorListener.start(context)
    }

    private companion object {
        /** Preference files of features earlier versions had: the Google Fit sync and Play Games */
        val STALE_PREFERENCES = listOf("GoogleFit", "pedometer_playservices")
    }
}
