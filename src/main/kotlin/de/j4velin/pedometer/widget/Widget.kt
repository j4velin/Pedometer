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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The home screen widget's receiver. It keeps the class name of earlier versions, so that the
 * widgets users already placed keep working after an update.
 */
class Widget : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = StepsWidget()

    companion object {

        // a failed update must not crash the app: the next one will try again
        private val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
                e.printStackTrace()
            }
        )

        /** Shows the latest saved steps and the configured colours in all widgets */
        @JvmStatic
        fun update(context: Context) {
            val app = context.applicationContext
            scope.launch { updateAll(app) }
        }

        suspend fun updateAll(context: Context) {
            val widget = StepsWidget()
            GlanceAppWidgetManager(context).getGlanceIds(StepsWidget::class.java).forEach {
                widget.refresh(context, it)
                widget.update(context, it)
            }
        }
    }
}
