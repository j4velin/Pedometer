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

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.j4velin.pedometer.util.Logger
import de.j4velin.pedometer.util.Util
import de.j4velin.pedometer.widget.Widget
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service which keeps the step counter listener alive, to always get the number of
 * steps since boot. The rules for what to save when live in
 * [de.j4velin.pedometer.domain.StepAccounting]; the service passes the values on and shows
 * [de.j4velin.pedometer.domain.StepAccounting.today] in its notification and the widgets.
 *
 * This service won't be needed any more if there is a way to read the step value without
 * waiting for a sensor event.
 */
class SensorListener : Service(), SensorEventListener {

    private val shutdownReceiver = ShutdownRecevier()
    private val accounting get() = PedometerApp.get(this).accounting
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // nobody knows what happens here: step value might magically decrease
        // when this method is called...
        if (BuildConfig.DEBUG) Logger.log("${sensor.name} accuracy changed: $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values[0]
        if (value > Int.MAX_VALUE) {
            if (BuildConfig.DEBUG) Logger.log("probably not a real value: $value")
            return
        }
        accounting.onStepCounter(value.toInt())
    }

    /** Shows the notification and makes this a foreground service */
    private fun startForeground() {
        try {
            ServiceCompat.startForeground(
                this, StepsNotification.ID, StepsNotification.build(this, accounting.today.value),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
            )
        } catch (e: SecurityException) {
            // permission got revoked
            if (BuildConfig.DEBUG) Logger.log(e)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        reRegisterSensor()
        accounting.onServiceStarted()
        startForeground()

        // restart service every hour to save the current step count
        val nextUpdate =
            minOf(Util.getTomorrow(), System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR)
        @Suppress("DEPRECATION")
        if (BuildConfig.DEBUG) Logger.log("next update: " + Date(nextUpdate).toLocaleString())
        // a foreground service start, as Android 8+ allows no plain service start from the
        // background. It works while the service runs, and before Android 12 also if it was
        // stopped in the meantime.
        val pi = PendingIntent.getForegroundService(
            applicationContext, 2, Intent(this, SensorListener::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        applicationContext.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC, nextUpdate, pi)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Logger.log("SensorListener onCreate")
        registerBroadcastReceiver()
        showToday()
    }

    /**
     * Keeps the notification and the widgets up to date with today's steps. While the overview
     * is open, new values arrive with every step: the notification then changes at most every
     * [NOTIFICATION_INTERVAL] ms and the widgets every [WIDGET_INTERVAL] ms.
     */
    private fun showToday() {
        // the first value is the one startForeground shows
        scope.launch {
            accounting.today.drop(1).conflate().collect {
                StepsNotification.update(this@SensorListener, it)
                delay(NOTIFICATION_INTERVAL)
            }
        }
        scope.launch {
            accounting.today.map { it.day to it.steps }.distinctUntilChanged().conflate().collect {
                Widget.update(this@SensorListener)
                delay(WIDGET_INTERVAL)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (BuildConfig.DEBUG) Logger.log("sensor service task removed")
        // Restart service in 500 ms
        getSystemService(AlarmManager::class.java).set(
            AlarmManager.RTC, System.currentTimeMillis() + 500,
            PendingIntent.getForegroundService(
                this, 3, Intent(this, SensorListener::class.java), PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) Logger.log("SensorListener onDestroy")
        scope.cancel()
        unregisterReceiver(shutdownReceiver)
        try {
            getSystemService(SensorManager::class.java).unregisterListener(this)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Logger.log(e)
            e.printStackTrace()
        }
    }

    private fun registerBroadcastReceiver() {
        if (BuildConfig.DEBUG) Logger.log("register broadcastreceiver")
        ContextCompat.registerReceiver(
            this, shutdownReceiver, IntentFilter(Intent.ACTION_SHUTDOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun reRegisterSensor() {
        if (BuildConfig.DEBUG) Logger.log("re-register sensor listener")
        val sm = getSystemService(SensorManager::class.java)
        try {
            sm.unregisterListener(this)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Logger.log(e)
            e.printStackTrace()
        }

        if (BuildConfig.DEBUG) {
            Logger.log("step sensors: " + sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size)
            if (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).isEmpty()) return // emulator
            Logger.log("default: " + sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!!.name)
        }

        // enable batching with delay of max 5 min
        sm.registerListener(
            this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
            SensorManager.SENSOR_DELAY_NORMAL, (5 * MICROSECONDS_IN_ONE_MINUTE).toInt()
        )
    }

    companion object {
        private const val MICROSECONDS_IN_ONE_MINUTE = 60_000_000L
        private const val NOTIFICATION_INTERVAL = 2_000L
        private const val WIDGET_INTERVAL = 60_000L

        /**
         * @return true, if the step counter may be read. Since Android 10, this requires the
         * ACTIVITY_RECOGNITION runtime permission, which is also needed to run the service as a
         * 'health' foreground service
         */
        @JvmStatic
        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED

        /** Starts the service, if the required permission is granted */
        @JvmStatic
        fun start(context: Context) {
            if (!hasPermission(context)) {
                if (BuildConfig.DEBUG) Logger.log("can not start SensorListener: permission missing")
                return
            }
            context.startForegroundService(Intent(context, SensorListener::class.java))
        }
    }
}
