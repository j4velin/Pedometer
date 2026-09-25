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

import android.app.NotificationManager
import android.os.Looper
import de.j4velin.pedometer.domain.TodaySteps
import de.j4velin.pedometer.testing.StepsTest
import de.j4velin.pedometer.widget.WidgetSettings
import java.text.NumberFormat
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/** Today's steps as the app publishes them, and the notification that shows them */
class TodayTest : StepsTest() {

    private val accounting get() = PedometerApp.get(context).accounting
    private val today: TodaySteps get() = accounting.today.value

    @Test
    fun noStepsBeforeTheFirstValue() {
        assertEquals(TodaySteps(millis(day1), null, 10000, started = false), accounting.refresh())
    }

    @Test
    fun followsTheService() {
        givenRows(day1 to -1000)
        givenSavedSinceBoot(1000)
        val service = service()
        assertEquals(0, today.steps)

        service.sensor(1300)
        assertEquals(300, today.steps)
        service.sensor(1400)
        assertEquals("published even without a save", 400, today.steps)
        assertEquals(1300, savedSinceBoot())
    }

    @Test
    fun followsTheOverviewToo() {
        givenRows(day1 to -1000)
        givenSavedSinceBoot(1000)
        val service = service()
        service.sensor(1300)

        accounting.onLiveStepCounter(1450)
        assertEquals(450, today.steps)
        // the service's batch arrives later, with the value at its time
        service.sensor(1500)
        assertEquals(500, today.steps)
    }

    @Test
    fun aNewDayStartsWithTheFirstSave() {
        givenRows(day1 to -1000)
        givenSavedSinceBoot(3000)
        val service = service()
        assertEquals(2000, today.steps)

        at(day2, 0, 10)
        accounting.refresh()
        assertEquals(TodaySteps(millis(day2), 0, 10000, started = false), today)

        service.sensor(3100)
        assertEquals(TodaySteps(millis(day2), 0, 10000, started = true), today)
        assertEquals(2100, stored(day1))
    }

    @Test
    fun goalChange() {
        givenRows(day1 to -1000)
        givenSavedSinceBoot(1000)
        prefs.edit().putInt("goal", 5000).commit()
        assertEquals(5000, accounting.refresh().goal)
    }

    @Test
    fun widgetShowsTheLatestValueNotOnlyTheSavedOne() {
        givenRows(day1 to -1000)
        givenSavedSinceBoot(1000)
        service().sensor(1300)
        assertEquals(300, WidgetSettings(context).data(1).steps)
    }

    @Test
    fun notificationFollowsTodaysSteps() {
        givenRows(day1 to -1000)
        givenSavedSinceBoot(1000)
        val service = service()
        idle()
        assertEquals(format(0), notificationSteps())

        // at most one update every two seconds, but always the latest value
        service.sensor(1300)
        accounting.onLiveStepCounter(1310)
        idle()
        assertEquals(format(0), notificationSteps())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(format(310), notificationSteps())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun format(steps: Int) = NumberFormat.getInstance().format(steps)

    /** The step count in the notification's title */
    private fun notificationSteps(): String {
        val notification = shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(StepsNotification.ID)
        return notification.extras.getCharSequence("android.title").toString().substringBefore(" ")
    }
}
