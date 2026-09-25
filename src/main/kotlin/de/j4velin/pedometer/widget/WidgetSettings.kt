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

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.edit
import de.j4velin.pedometer.PedometerApp

/** What a widget shows */
data class WidgetData(val steps: Int, @param:ColorInt val textColor: Int, @param:ColorInt val background: Int)

/**
 * The colours of each widget, in the preference file and under the keys the widget has always
 * used, so that widgets placed with an earlier version keep their colours.
 */
class WidgetSettings(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @ColorInt
    fun textColor(appWidgetId: Int): Int = prefs.getInt(TEXT_COLOR + appWidgetId, DEFAULT_TEXT_COLOR)

    fun setTextColor(appWidgetId: Int, @ColorInt color: Int) =
        prefs.edit { putInt(TEXT_COLOR + appWidgetId, color) }

    @ColorInt
    fun background(appWidgetId: Int): Int = prefs.getInt(BACKGROUND + appWidgetId, DEFAULT_BACKGROUND)

    fun setBackground(appWidgetId: Int, @ColorInt color: Int) =
        prefs.edit { putInt(BACKGROUND + appWidgetId, color) }

    /** Reads the database: not on the main thread */
    fun data(appWidgetId: Int) = WidgetData(stepsToday(), textColor(appWidgetId), background(appWidgetId))

    private fun stepsToday(): Int = PedometerApp.get(context).accounting.refresh().steps ?: 0

    private companion object {
        const val PREFS = "Widgets"
        const val TEXT_COLOR = "color_"
        const val BACKGROUND = "background_"
        const val DEFAULT_TEXT_COLOR = Color.WHITE
        const val DEFAULT_BACKGROUND = Color.TRANSPARENT
    }
}
