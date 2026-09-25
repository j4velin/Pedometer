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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
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
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.j4velin.pedometer.ui.MainActivity
import de.j4velin.pedometer.util.Logger
import de.j4velin.pedometer.util.Util
import de.j4velin.pedometer.widget.Widget
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service which keeps the step counter listener alive, to always get the number of
 * steps since boot. The rules for what to save when live in
 * [de.j4velin.pedometer.domain.StepAccounting].
 *
 * This service won't be needed any more if there is a way to read the step value without
 * waiting for a sensor event.
 */
class SensorListener : Service(), SensorEventListener {

    private val shutdownReceiver = ShutdownRecevier()
    private val accounting get() = PedometerApp.get(this).accounting

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
        if (accounting.onStepCounter(value.toInt())) saved()
    }

    /** Shows the saved value in the notification and the widgets */
    private fun saved() {
        showNotification()
        Widget.update(this)
    }

    private fun showNotification() {
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, getNotification(this),
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
        if (accounting.saveIfNecessary()) saved() else showNotification()

        // restart service every hour to save the current step count
        val nextUpdate =
            minOf(Util.getTomorrow(), System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR)
        @Suppress("DEPRECATION")
        if (BuildConfig.DEBUG) Logger.log("next update: " + Date(nextUpdate).toLocaleString())
        val pi = PendingIntent.getService(
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
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (BuildConfig.DEBUG) Logger.log("sensor service task removed")
        // Restart service in 500 ms
        getSystemService(AlarmManager::class.java).set(
            AlarmManager.RTC, System.currentTimeMillis() + 500,
            PendingIntent.getService(
                this, 3, Intent(this, SensorListener::class.java), PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) Logger.log("SensorListener onDestroy")
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
        const val NOTIFICATION_ID = 1
        private const val MICROSECONDS_IN_ONE_MINUTE = 60_000_000L

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

        /** Keeps the id of the earlier versions, so that the user's settings for it stay */
        private const val NOTIFICATION_CHANNEL_ID = "Notification"

        /** Creates the notification's channel, if it does not exist yet */
        private fun notificationChannel(context: Context): String {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_NONE
            ).apply {
                importance = NotificationManager.IMPORTANCE_MIN
                enableLights(false)
                enableVibration(false)
                setBypassDnd(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            return NOTIFICATION_CHANNEL_ID
        }

        /** Opens the system settings of the notification's channel */
        fun openNotificationSettings(context: Context) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "Settings not found - please search for the notification settings in the Android settings manually",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        @JvmStatic
        fun getNotification(context: Context): Notification {
            if (BuildConfig.DEBUG) Logger.log("getNotification")
            val app = PedometerApp.get(context)
            val goal = app.settings.goal
            val stepsToday = app.accounting.stepsTodayForNotification()
            val builder = Notification.Builder(context, notificationChannel(context))
            if (stepsToday != null) {
                val format = NumberFormat.getInstance(Locale.getDefault())
                builder.setProgress(goal, stepsToday, false)
                    .setContentText(
                        if (stepsToday >= goal) {
                            context.getString(
                                R.string.goal_reached_notification, format.format(stepsToday)
                            )
                        } else {
                            context.getString(
                                R.string.notification_text, format.format(goal - stepsToday)
                            )
                        }
                    )
                    .setContentTitle(format.format(stepsToday) + " " + context.getString(R.string.steps))
            } else { // still no step value?
                builder.setContentText(context.getString(R.string.your_progress_will_be_shown_here_soon))
                    .setContentTitle(context.getString(R.string.notification_title))
            }
            builder.setShowWhen(false)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setSmallIcon(R.drawable.ic_notification).setOngoing(true)
            return builder.build()
        }
    }
}
