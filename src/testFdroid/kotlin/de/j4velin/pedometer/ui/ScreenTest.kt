package de.j4velin.pedometer.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import de.j4velin.pedometer.R
import de.j4velin.pedometer.testing.StepsTest
import de.j4velin.pedometer.ui.overview.OverviewTags
import de.j4velin.pedometer.ui.overview.OverviewViewModel
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSensor

/**
 * Base for tests that drive the screens. Only in the fdroid flavor: the play flavor's
 * Activity_Main talks to Play Games, and the screens are the same in both.
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

    private val opened = mutableListOf<ActivityController<Activity_Main>>()

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
        val controller: ActivityController<Activity_Main> =
            Robolectric.buildActivity(Activity_Main::class.java).setup()
        val activity: Activity_Main get() = controller.get()
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

        fun menu(id: Int) {
            compose.onNodeWithTag(PedometerActivity.MENU_TAG).performClick()
            val entry = PedometerActivity.MENU.first { it.id == id }
            compose.onNodeWithText(context.getString(entry.title)).performClick()
            idle()
            @Suppress("DEPRECATION")
            activity.fragmentManager.executePendingTransactions()
            idle()
        }

        fun settings(): Fragment_Settings {
            menu(R.id.action_settings)
            @Suppress("DEPRECATION")
            return activity.fragmentManager.findFragmentByTag(PedometerActivity.SETTINGS_TAG) as Fragment_Settings
        }
    }

    protected fun overview() = Overview()

    /** Hands [file] to the settings screen as the document picked for export (1) or import (2) */
    protected fun Fragment_Settings.documentPicked(requestCode: Int, file: File) {
        onActivityResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        idle()
    }

    protected fun latestMessage(): String? {
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog? ?: return null
        return shadowOf(dialog).message?.toString()
    }

    protected fun format(value: Number): String = Formats.number().format(value)
}
