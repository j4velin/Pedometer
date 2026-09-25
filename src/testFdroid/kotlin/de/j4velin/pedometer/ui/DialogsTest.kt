package de.j4velin.pedometer.ui

import android.app.Dialog
import android.view.View
import android.widget.TextView
import de.j4velin.pedometer.R
import java.text.DateFormat
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.robolectric.shadows.ShadowDialog

/** The statistics and split count dialogs */
class DialogsTest : ScreenTest() {

    private fun latestDialog(): Dialog = ShadowDialog.getLatestDialog()

    private fun Dialog.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun statistics() {
        // a Saturday: the week is the last seven days, the month started 15 days ago
        at(day3, 9, 0)
        val monthStart = day3.withDayOfMonth(1)
        givenRows(
            monthStart.minusDays(1) to 20000, // the record, last month
            monthStart to 1000,
            day1 to 8000,
            day2 to 12000,
            day3 to -4000,
        )
        givenSavedSinceBoot(6500)

        val overview = overview()
        overview.openStatistics()
        val dialog = latestDialog()

        assertEquals(
            format(20000) + " @ " +
                DateFormat.getDateInstance().format(Date(millis(monthStart.minusDays(1)))),
            dialog.text(R.id.record)
        )
        // today counts with its steps so far: 6500 - 4000
        assertEquals(format(22500), dialog.text(R.id.totalthisweek))
        assertEquals(format(22500 / 7), dialog.text(R.id.averagethisweek))
        assertEquals(format(23500), dialog.text(R.id.totalthismonth))
        assertEquals(format(23500 / 16), dialog.text(R.id.averagethismonth))
    }

    @Test
    fun splitCount() {
        at(day2, 9, 0)
        givenRows(day1 to 8000, day2 to -4000)
        givenSavedSinceBoot(6500)

        val overview = overview()
        overview.menu(R.id.action_split_count)
        latestDialog().findViewById<View>(R.id.start).performClick()
        assertEquals(10500, prefs.getInt("split_steps", -1))
        // the looper runs the chart animations, which moves the clock by a few milliseconds
        assertEquals(millis(day2, 9).toDouble(), prefs.getLong("split_date", -1).toDouble(), 1000.0)

        at(day2, 10, 0)
        overview.sensor(9000)
        overview.menu(R.id.action_split_count)
        val dialog = latestDialog()
        assertEquals(format(2500), dialog.text(R.id.steps))
        assertEquals(format(2500 * 75f / 100000), dialog.text(R.id.distance))
        assertEquals("km", dialog.text(R.id.distanceunit))

        dialog.findViewById<View>(R.id.start).performClick()
        assertFalse(prefs.contains("split_steps"))
        assertFalse(prefs.contains("split_date"))
    }
}
