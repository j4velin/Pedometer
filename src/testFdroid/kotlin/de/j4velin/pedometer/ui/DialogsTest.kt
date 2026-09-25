package de.j4velin.pedometer.ui

import de.j4velin.pedometer.ui.MainActivity.MenuEntry
import de.j4velin.pedometer.ui.dialogs.DialogTags
import java.text.DateFormat
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The statistics and split count dialogs */
class DialogsTest : ScreenTest() {

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

        assertEquals(
            format(20000) + " @ " +
                DateFormat.getDateInstance().format(Date(millis(monthStart.minusDays(1)))),
            dialogText(DialogTags.RECORD)
        )
        // today counts with its steps so far: 6500 - 4000
        assertEquals(format(22500), dialogText(DialogTags.TOTAL_WEEK))
        assertEquals(format(22500 / 7), dialogText(DialogTags.AVERAGE_WEEK))
        assertEquals(format(23500), dialogText(DialogTags.TOTAL_MONTH))
        assertEquals(format(23500 / 16), dialogText(DialogTags.AVERAGE_MONTH))
    }

    @Test
    fun splitCount() {
        at(day2, 9, 0)
        givenRows(day1 to 8000, day2 to -4000)
        givenSavedSinceBoot(6500)

        val overview = overview()
        overview.menu(MenuEntry.SPLIT_COUNT)
        click(DialogTags.SPLIT_START_STOP)
        assertEquals(10500, prefs.getInt("split_steps", -1))
        // the looper runs the chart animations, which moves the clock by a few milliseconds
        assertEquals(millis(day2, 9).toDouble(), prefs.getLong("split_date", -1).toDouble(), 1000.0)

        at(day2, 10, 0)
        overview.sensor(9000)
        overview.menu(MenuEntry.SPLIT_COUNT)
        assertEquals(format(2500), dialogText(DialogTags.SPLIT_STEPS))
        assertEquals(format(2500 * 75f / 100000), dialogText(DialogTags.SPLIT_DISTANCE))
        assertEquals("km", dialogText(DialogTags.SPLIT_UNIT))

        click(DialogTags.SPLIT_START_STOP)
        assertFalse(prefs.contains("split_steps"))
        assertFalse(prefs.contains("split_date"))
    }
}
