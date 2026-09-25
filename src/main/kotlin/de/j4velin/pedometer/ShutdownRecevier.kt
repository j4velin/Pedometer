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

/** Registered by [SensorListener] while it runs, as ACTION_SHUTDOWN can't be received otherwise */
class ShutdownRecevier : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) Logger.log("shutting down")
        context.startService(Intent(context, SensorListener::class.java))
        PedometerApp.get(context).accounting.onShutdown()
    }
}
