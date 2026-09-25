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

package de.j4velin.pedometer.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.currentState
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasStartActivityClickAction
import de.j4velin.pedometer.testing.StepsTest
import de.j4velin.pedometer.ui.ColorPickerTags
import de.j4velin.pedometer.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

class WidgetTest : StepsTest() {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val widgetPrefs
        get() = context.getSharedPreferences("Widgets", Context.MODE_PRIVATE)

    @Test
    fun todaysStepsFromTheLastSavedValue() {
        givenRows(day1 to -4000)
        givenSavedSinceBoot(6500)
        assertEquals(2500, WidgetSettings(context).data(WIDGET_ID).steps)
    }

    @Test
    fun noStepsBeforeTodayStarted() {
        givenRows(day1.minusDays(1) to 8000)
        givenSavedSinceBoot(6500)
        assertEquals(0, WidgetSettings(context).data(WIDGET_ID).steps)
    }

    @Test
    fun coloursOfAWidgetPlacedWithAnEarlierVersion() {
        widgetPrefs.edit().putInt("color_$WIDGET_ID", Color.RED)
            .putInt("background_$WIDGET_ID", 0x80000000.toInt()).commit()
        val data = WidgetSettings(context).data(WIDGET_ID)
        assertEquals(Color.RED, data.textColor)
        assertEquals(0x80000000.toInt(), data.background)
        // other widgets have their own colours
        assertEquals(Color.WHITE, WidgetSettings(context).textColor(WIDGET_ID + 1))
        assertEquals(Color.TRANSPARENT, WidgetSettings(context).background(WIDGET_ID + 1))
    }

    @Test
    fun content() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(80.dp, 80.dp))
        setState(preferencesOf(StepsWidget.STEPS to 1234))
        provideComposable { StepsWidgetContent(currentState()) }

        onNode(hasContentDescription("1234")).assertExists()
        onNode(hasContentDescription("steps")).assertExists()
        onNode(hasStartActivityClickAction<MainActivity>()).assertExists()
    }

    @Test
    fun configureColours() {
        val controller = Robolectric.buildActivity(
            WidgetConfig::class.java,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID),
        ).setup()
        idle()

        compose.onNodeWithTag(WidgetConfigTags.TEXT_COLOR).performClick()
        compose.onNodeWithTag(ColorPickerTags.HEX).performTextReplacement("#FF112233")
        compose.onNodeWithTag(ColorPickerTags.OK).performClick()
        idle()
        compose.onNodeWithTag(WidgetConfigTags.BACKGROUND).performClick()
        // without alpha: opaque
        compose.onNodeWithTag(ColorPickerTags.HEX).performTextReplacement("445566")
        compose.onNodeWithTag(ColorPickerTags.OK).performClick()
        idle()

        assertEquals(0xFF112233.toInt(), widgetPrefs.getInt("color_$WIDGET_ID", 0))
        assertEquals(0xFF445566.toInt(), widgetPrefs.getInt("background_$WIDGET_ID", 0))

        compose.onNodeWithTag(WidgetConfigTags.DONE).performClick()
        idle()
        val activity = controller.get()
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(
            WIDGET_ID,
            shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun configurationWithoutWidget() {
        val controller = Robolectric.buildActivity(WidgetConfig::class.java).setup()
        val activity = controller.get()
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        controller.pause().stop().destroy()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private companion object {
        const val WIDGET_ID = 7
    }
}
