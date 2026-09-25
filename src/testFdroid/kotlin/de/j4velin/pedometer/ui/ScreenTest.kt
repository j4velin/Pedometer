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
import android.view.View
import android.view.Window
import android.widget.TextView
import de.j4velin.pedometer.R
import de.j4velin.pedometer.testing.StepsTest
import java.io.File
import org.eazegraph.lib.charts.BarChart
import org.eazegraph.lib.models.BarModel
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSensor

/**
 * Base for tests that drive the screens. Only in the fdroid flavor: the play flavor's
 * Activity_Main talks to Play Games, and the screens are the same in both.
 */
abstract class ScreenTest : StepsTest() {

    @Before
    fun deviceWithStepCounter() {
        shadowOf(context).grantPermissions(
            Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS
        )
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(sm).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
    }

    protected fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The overview, as it looks after the app was opened */
    protected inner class Overview {
        val controller: ActivityController<Activity_Main> =
            Robolectric.buildActivity(Activity_Main::class.java).setup()
        val activity: Activity_Main get() = controller.get()
        private val fragment: Fragment_Overview
            get() = activity.fragmentManager.findFragmentById(android.R.id.content) as Fragment_Overview

        init {
            idle()
        }

        /** The step counter reports [stepsSinceBoot] while the overview is open */
        fun sensor(stepsSinceBoot: Int) {
            fragment.onSensorChanged(stepEvent(stepsSinceBoot.toFloat()))
            idle()
        }

        fun text(id: Int): String = activity.findViewById<TextView>(id).text.toString()
        val steps get() = text(R.id.steps)
        val total get() = text(R.id.total)
        val average get() = text(R.id.average)
        val unit get() = text(R.id.unit)

        /** The week chart's bars, oldest first */
        val bars: List<BarModel>
            get() = activity.findViewById<BarChart>(R.id.bargraph).let {
                if (it.visibility == View.VISIBLE) it.data else emptyList()
            }

        fun toggleStepsAndDistance() {
            activity.findViewById<View>(R.id.graph).performClick()
            idle()
        }

        fun leave() {
            controller.pause().stop()
            idle()
        }

        fun menu(id: Int) {
            activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, RoboMenuItem(id))
            activity.fragmentManager.executePendingTransactions()
            idle()
        }

        fun settings(): Fragment_Settings {
            menu(R.id.action_settings)
            return activity.fragmentManager.findFragmentById(android.R.id.content) as Fragment_Settings
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

    protected fun format(value: Number): String = Fragment_Overview.formatter.format(value)
}
