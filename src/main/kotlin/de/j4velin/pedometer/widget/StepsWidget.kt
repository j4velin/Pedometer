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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import de.j4velin.pedometer.R
import de.j4velin.pedometer.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Today's steps on the home screen, in the colours chosen in [WidgetConfig]. Opens the app when
 * tapped.
 *
 * The widget's state holds what it shows: [refresh] copies it from the database and the
 * widget settings, and the content only reads the state, so that an update of a running
 * widget always recomposes it.
 */
class StepsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        refresh(context, id)
        provideContent { StepsWidgetContent(currentState()) }
    }

    /** Copies the current steps and colours into the state of the widget [id] */
    suspend fun refresh(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = withContext(Dispatchers.IO) { WidgetSettings(context).data(appWidgetId) }
        updateAppWidgetState(context, id) {
            it[STEPS] = data.steps
            it[TEXT_COLOR] = data.textColor
            it[BACKGROUND] = data.background
        }
    }

    internal companion object {
        val STEPS = intPreferencesKey("steps")
        val TEXT_COLOR = intPreferencesKey("text_color")
        val BACKGROUND = intPreferencesKey("background")
    }
}

/** What [StepsWidget] shows for its [state] */
@Composable
internal fun StepsWidgetContent(state: Preferences) {
    val context = LocalContext.current
    val textColor = state[StepsWidget.TEXT_COLOR] ?: Color.WHITE
    Column(
        GlanceModifier.fillMaxSize()
            .background(ComposeColor(state[StepsWidget.BACKGROUND] ?: Color.TRANSPARENT))
            .clickable(actionStartActivity<MainActivity>())
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShadowedText((state[StepsWidget.STEPS] ?: 0).toString(), 20f, textColor)
        ShadowedText(context.getString(R.string.steps), 12f, textColor)
    }
}

// the shadow of the TextViews in the earlier layout, in pixels as there
private const val SHADOW_RADIUS = 3f
private const val SHADOW_DY = 2f

/**
 * Text with the dark shadow the widget always had, which keeps white text readable on a light
 * wallpaper. Glance's Text has no shadow, so the text is drawn into a bitmap.
 */
@Composable
private fun ShadowedText(text: String, sizeSp: Float, @ColorInt color: Int) {
    val context = LocalContext.current
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics
        )
        this.color = color
        setShadowLayer(SHADOW_RADIUS, 0f, SHADOW_DY, Color.BLACK)
    }
    val metrics = paint.fontMetrics
    val margin = SHADOW_RADIUS + SHADOW_DY
    val bitmap = createBitmap(
        (paint.measureText(text) + 2 * margin).toInt().coerceAtLeast(1),
        (metrics.descent - metrics.ascent + 2 * margin).toInt().coerceAtLeast(1),
    )
    Canvas(bitmap).drawText(text, margin, margin - metrics.ascent, paint)
    Image(ImageProvider(bitmap), contentDescription = text)
}
