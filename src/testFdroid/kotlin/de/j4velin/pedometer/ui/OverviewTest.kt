package de.j4velin.pedometer.ui

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the overview shows, and what it writes when it gets a sensor event itself */
class OverviewTest : ScreenTest() {

    @Test
    fun firstStartFromTheUi() {
        val overview = overview()
        assertEquals("0", overview.steps)

        overview.sensor(800)
        assertEquals(mapOf(millis(day1) to -800), rows())
        assertEquals("0", overview.steps)

        overview.sensor(900)
        assertEquals("100", overview.steps)

        overview.leave()
        assertEquals("leaving saves the latest sensor value", 900, savedSinceBoot())
    }

    @Test
    fun zeroFromTheSensorIsIgnored() {
        val overview = overview()
        overview.sensor(0)
        assertEquals(emptyMap<Long, Int>(), rows())
    }

    @Test
    fun todayTotalAndAverage() {
        at(day2, 9, 0)
        givenRows(day1 to 8000, day2 to -4000)
        givenSavedSinceBoot(6500)

        val overview = overview()
        assertEquals(format(2500), overview.steps)
        assertEquals(format(10500), overview.total)
        assertEquals("the average counts today as a day", format(5250), overview.average)

        overview.sensor(7000)
        assertEquals(format(3000), overview.steps)
        assertEquals(format(11000), overview.total)
    }

    @Test
    fun distanceInMetric() {
        at(day2, 9, 0)
        givenRows(day1 to 8000, day2 to -4000)
        givenSavedSinceBoot(6500)

        val overview = overview()
        overview.toggleStepsAndDistance()
        assertEquals("km", overview.unit)
        // 75 cm per step outside the US
        assertEquals(format(2500 * 75f / 100000), overview.steps)
        assertEquals(format(10500 * 75f / 100000), overview.total)
        assertEquals(format(10500 * 75f / 100000 / 2), overview.average)

        overview.toggleStepsAndDistance()
        assertEquals(format(2500), overview.steps)
    }

    @Test
    fun distanceInImperial() {
        at(day2, 9, 0)
        givenRows(day1 to 8000, day2 to -4000)
        givenSavedSinceBoot(6500)
        prefs.edit().putString("stepsize_unit", "ft").putFloat("stepsize_value", 2.5f).commit()

        val overview = overview()
        overview.toggleStepsAndDistance()
        assertEquals("mi", overview.unit)
        assertEquals(format(2500 * 2.5f / 5280), overview.steps)
    }

    @Test
    fun barsShowTheLastSevenFinishedDays() {
        val today = day1.plusDays(8)
        at(today, 9, 0)
        givenRows(
            today.minusDays(8) to 1000, // too old
            today.minusDays(7) to 0, // no steps: no bar
            today.minusDays(6) to 3000,
            today.minusDays(5) to 11000, // above the goal
            today.minusDays(4) to 5000,
            today.minusDays(3) to 6000,
            today.minusDays(2) to 7000,
            today.minusDays(1) to 8000,
            today to -500,
        )
        givenSavedSinceBoot(600)

        val bars = overview().bars
        assertEquals(listOf(3000f, 11000f, 5000f, 6000f, 7000f, 8000f), bars.map { it.value })
        val blue = Color.parseColor("#0099cc")
        val green = Color.parseColor("#99CC00")
        assertEquals(listOf(blue, green, blue, blue, blue, blue), bars.map { it.color })
    }

    @Test
    fun openAcrossMidnightAfterTheServiceStartedTheDay() {
        givenRows(day1 to -1000)
        givenSavedSinceBoot(1000)
        val overview = overview()
        at(day1, 22, 0)
        overview.sensor(3000)
        assertEquals(format(2000), overview.steps)

        at(day2, 0, 10)
        service().sensor(5200)
        overview.sensor(5250)

        assertEquals(4200, stored(day1))
        assertEquals(-5200, stored(day2))
        assertEquals(format(50), overview.steps)
        assertEquals(format(4250), overview.total)
        assertEquals(format(2125), overview.average)
        assertEquals("yesterday moved into the chart", listOf(4200f), overview.bars.map { it.value })
    }

    @Test
    fun openAcrossMidnightStartsTheDayItself() {
        givenRows(day1 to -1000)
        givenSavedSinceBoot(1000)
        val overview = overview()
        at(day1, 22, 0)
        overview.sensor(3000)

        at(day2, 0, 10)
        overview.sensor(5200)

        assertEquals(4200, stored(day1))
        assertEquals(-5200, stored(day2))
        assertEquals("0", overview.steps)
        assertEquals(format(4200), overview.total)
    }
}
