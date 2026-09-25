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

package de.j4velin.pedometer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import de.j4velin.pedometer.domain.TodaySteps
import de.j4velin.pedometer.ui.Formats
import de.j4velin.pedometer.ui.MainActivity

/** The step counter service's ongoing notification: today's progress towards the goal */
object StepsNotification {

    const val ID = 1

    /** Keeps the id of the earlier versions, so that the user's settings for it stay */
    private const val CHANNEL_ID = "Notification"

    fun build(context: Context, today: TodaySteps): Notification {
        val builder = Notification.Builder(context, channel(context))
        val steps = today.steps
        if (steps != null) {
            val format = Formats.number()
            builder.setProgress(today.goal, steps, false)
                .setContentText(
                    if (steps >= today.goal) {
                        context.getString(R.string.goal_reached_notification, format.format(steps))
                    } else {
                        context.getString(
                            R.string.notification_text, format.format(today.goal - steps)
                        )
                    }
                )
                .setContentTitle(format.format(steps) + " " + context.getString(R.string.steps))
        } else { // still no step value?
            builder.setContentText(context.getString(R.string.your_progress_will_be_shown_here_soon))
                .setContentTitle(context.getString(R.string.notification_title))
        }
        return builder.setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    /** Shows [today] in the notification, which the service shows already */
    fun update(context: Context, today: TodaySteps) {
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context, today))
    }

    /** Opens the system settings of the notification's channel */
    fun openSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
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

    /** Creates the notification's channel, if it does not exist yet */
    private fun channel(context: Context): String {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_NONE
        ).apply {
            importance = NotificationManager.IMPORTANCE_MIN
            enableLights(false)
            enableVibration(false)
            setBypassDnd(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return CHANNEL_ID
    }
}
