package de.j4velin.pedometer

import de.j4velin.pedometer.testing.StepsTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The queries behind totals, averages, the record day and the charts */
class DatabaseTest : StepsTest() {

    private val today = day3

    @Before
    fun history() {
        at(today, 9, 0)
        givenRows(
            day1.minusDays(1) to 0,
            day1 to 8000,
            day2 to 12000,
            today to -4000, // today's offset: 4000 steps since boot at the start of the day
        )
        givenSavedSinceBoot(6500)
    }

    @Test
    fun totalCountsOnlyFinishedDaysWithSteps() {
        assertEquals(20000, withDb { it.totalWithoutToday })
    }

    @Test
    fun daysCountFinishedDaysWithStepsPlusToday() {
        assertEquals(2, withDb { it.daysWithoutToday })
        assertEquals(3, withDb { it.days })
    }

    @Test
    fun recordIsTheBestDay() {
        assertEquals(12000, withDb { it.record })
        val (date, steps) = withDb { it.recordData }.let { it.first to it.second }
        assertEquals(millis(day2), date.time)
        assertEquals(12000, steps)
    }

    @Test
    fun todaysOffsetPlusStepsSinceBootIsToday() {
        assertEquals(2500, withDb { it.getSteps(millis(today)) + it.currentSteps })
    }

    @Test
    fun missingDayIsMinValue() {
        assertEquals(Int.MIN_VALUE, withDb { it.getSteps(millis(day1.minusDays(5))) })
    }

    @Test
    fun rangeSumIncludesTodaysNegativeOffset() {
        assertEquals(16000, withDb { it.getSteps(millis(day1), millis(today)) })
        assertEquals(0, withDb { it.getSteps(millis(today.plusDays(1)), millis(today.plusDays(9))) })
    }

    @Test
    fun lastEntriesAreNewestFirstWithoutTheSinceBootRow() {
        val entries = withDb { it.getLastEntries(8) }.map { it.first to it.second }
        assertEquals(
            listOf(millis(today) to -4000, millis(day2) to 12000, millis(day1) to 8000,
                millis(day1.minusDays(1)) to 0),
            entries
        )
        assertEquals(2, withDb { it.getLastEntries(2) }.size)
    }

    @Test
    fun insertNewDayNeverOverwrites() {
        withDb { it.insertNewDay(millis(today), 9999) }
        assertEquals(-4000, stored(today))
        assertEquals(12000, stored(day2))
    }

    @Test
    fun insertNewDayRejectsNegativeSteps() {
        val tomorrow = today.plusDays(1)
        withDb { it.insertNewDay(millis(tomorrow), -5) }
        assertEquals(null, stored(tomorrow))
    }

    @Test
    fun backupInsertOverwritesAndReportsIt() {
        assertFalse(withDb { it.insertDayFromBackup(millis(day2), 11000) })
        assertTrue(withDb { it.insertDayFromBackup(millis(day1.minusDays(9)), 3000) })
        assertEquals(11000, stored(day2))
        assertEquals(3000, stored(day1.minusDays(9)))
    }

    @Test
    fun invalidEntriesAreRemoved() {
        givenRows(day1.minusDays(3) to 200000, day1.minusDays(4) to 199999)
        withDb { it.removeInvalidEntries() }
        assertEquals(null, stored(day1.minusDays(3)))
        assertEquals(199999, stored(day1.minusDays(4)))
    }

    @Test
    fun savedSinceBootDefaultsToZero() {
        withDb { it.writableDatabase.delete("steps", "date = -1", null) }
        assertEquals(0, withDb { it.currentSteps })
    }
}
