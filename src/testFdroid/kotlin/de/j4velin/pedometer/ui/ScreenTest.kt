package de.j4velin.pedometer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import de.j4velin.pedometer.R
import de.j4velin.pedometer.testing.StepsTest
import de.j4velin.pedometer.ui.dialogs.DialogTags
import de.j4velin.pedometer.ui.overview.OverviewTags
import de.j4velin.pedometer.ui.overview.OverviewViewModel
import de.j4velin.pedometer.ui.settings.SettingsViewModel
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowSensor

/**
 * Base for tests that drive the screens. Only in the fdroid flavor: the play flavor talks to
 * Play Games, and the screens are the same in both.
 */
abstract class ScreenTest : StepsTest() {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun deviceWithStepCounter() {
        shadowOf(context).grantPermissions(
            Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS
        )
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(sm).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
    }

    private val opened = mutableListOf<ActivityController<MainActivity>>()

    /** Closes the screens a test opened, so that the next test only finds its own */
    @After
    fun closeScreens() {
        opened.forEach { it.pause().stop().destroy() }
        opened.clear()
    }

    protected fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    /** A bar of the week chart: its value and colour */
    data class Bar(val value: Float, val color: Int)

    /** The overview, as it looks after the app was opened */
    protected inner class Overview {
        val controller: ActivityController<MainActivity> =
            Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity: MainActivity get() = controller.get()
        private val viewModel: OverviewViewModel
            get() = ViewModelProvider(activity)[OverviewViewModel::class.java]

        init {
            opened += controller
            idle()
        }

        /** The step counter reports [stepsSinceBoot] while the overview is open */
        fun sensor(stepsSinceBoot: Int) {
            viewModel.onSensorChanged(stepEvent(stepsSinceBoot.toFloat()))
            idle()
        }

        private fun text(tag: String): String =
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsProperties.Text].joinToString("") { it.text }

        val steps get() = text(OverviewTags.STEPS)
        val total get() = text(OverviewTags.TOTAL)
        val average get() = text(OverviewTags.AVERAGE)
        val unit get() = text(OverviewTags.UNIT)

        /** The week chart's bars, oldest first */
        val bars: List<Bar>
            get() = viewModel.state.value.bars.map { Bar(it.value, it.color.toArgb()) }

        fun toggleStepsAndDistance() {
            compose.onNodeWithTag(OverviewTags.RING).performClick()
            idle()
        }

        /** Taps the week chart, which opens the statistics */
        fun openStatistics() {
            compose.onNodeWithTag(OverviewTags.BARS).performClick()
            idle()
        }

        fun leave() {
            controller.pause().stop()
            idle()
        }

        fun menu(entry: MainActivity.MenuEntry) {
            compose.onNodeWithTag(MainActivity.MENU_TAG).performClick()
            compose.onNodeWithText(context.getString(entry.title)).performClick()
            idle()
        }

        fun settings(): SettingsScreen {
            menu(MainActivity.MenuEntry.SETTINGS)
            return SettingsScreen(activity)
        }
    }

    protected inner class SettingsScreen(private val activity: MainActivity) {
        private val viewModel: SettingsViewModel
            get() = ViewModelProvider(activity)[SettingsViewModel::class.java]

        /**
         * Picks [file] as the document to export to (1) or import from (2), and waits for the
         * result message
         */
        fun documentPicked(requestCode: Int, file: File) {
            viewModel.dismissMessage()
            val title = if (requestCode == 1) R.string.export_title else R.string.import_title
            compose.onNodeWithText(context.getString(title)).performClick()
            idle()
            val picker = shadowOf(activity).nextStartedActivityForResult
            shadowOf(activity).receiveResult(
                picker.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))
            )
            // the file is written on a background thread, the result comes back on the main looper
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.state.value.message != null
            }
            idle()
        }
    }

    protected fun overview() = Overview()

    /** The message the settings showed last, if it is still shown */
    protected fun latestMessage(): String? =
        compose.onAllNodesWithTag(DialogTags.MESSAGE).fetchSemanticsNodes().firstOrNull()
            ?.config?.get(SemanticsProperties.Text)?.joinToString("") { it.text }

    /** The text of the dialog element tagged [tag] */
    protected fun dialogText(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    protected fun click(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        idle()
    }

    protected fun format(value: Number): String = Formats.number().format(value)
}
