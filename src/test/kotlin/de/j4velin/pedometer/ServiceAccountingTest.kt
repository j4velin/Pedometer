package de.j4velin.pedometer

import de.j4velin.pedometer.testing.StepsTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the step counter service writes, from the first sensor event to the day rollover */
class ServiceAccountingTest : StepsTest() {

    @Test
    fun firstEventEverStartsTodayAtZero() {
        val service = service()
        service.sensor(1000)

        // today's row holds the negative sensor value, so today's steps are 0
        assertEquals(mapOf(-1L to 1000, millis(day1) to -1000), rows())
    }

    @Test
    fun startingTheServiceWithoutAnEventWritesNothing() {
        service()
        assertEquals(emptyMap<Long, Int>(), rows())
    }

    @Test
    fun zeroStepsSinceBootWritesNothing() {
        service().sensor(0)
        assertEquals(emptyMap<Long, Int>(), rows())
    }

    @Test
    fun savesAfterMoreThan500Steps() {
        val service = service()
        service.sensor(1000)
        service.sensor(1500)
        assertEquals("exactly 500 more is not enough", 1000, savedSinceBoot())
        service.sensor(1501)
        assertEquals(1501, savedSinceBoot())
        assertEquals("the offset stays", -1000, stored(day1))
    }

    @Test
    fun savesWhenAnHourHasPassed() {
        val service = service()
        service.sensor(1000)
        at(day1, 8, 59)
        service.sensor(1010)
        assertEquals(1000, savedSinceBoot())
        at(day1, 9, 1)
        service.sensor(1020)
        assertEquals(1020, savedSinceBoot())
    }

    @Test
    fun hourlyRestartSavesTheLatestValue() {
        val service = service()
        service.sensor(1000)
        service.sensor(1100)
        at(day1, 9, 1)
        service.restart()
        assertEquals(1100, savedSinceBoot())
    }

    @Test
    fun firstEventAfterMidnightClosesYesterday() {
        val service = service()
        service.sensor(1000)
        at(day1, 23, 30)
        service.sensor(5000)
        at(day2, 0, 5)
        service.sensor(5200)

        // every step until the first save of the new day belongs to yesterday: 5200 - 1000
        assertEquals(4200, stored(day1))
        assertEquals(-5200, stored(day2))
        assertEquals(5200, savedSinceBoot())
    }

    @Test
    fun firstEventAfterMidnightSavesEvenWithFewNewSteps() {
        val service = service()
        service.sensor(1000)
        at(day1, 23, 50)
        service.sensor(1100)
        at(day2, 0, 1)
        service.sensor(1110)
        assertEquals(110, stored(day1))
        assertEquals(-1110, stored(day2))
    }

    @Test
    fun daysWithoutAnyEventGetNoRow() {
        val service = service()
        service.sensor(1000)
        at(day3, 9, 0)
        service.sensor(3000)

        // the steps of the day in between are counted for the last day that has a row
        assertEquals(2000, stored(day1))
        assertNull(stored(day2))
        assertEquals(-3000, stored(day3))
    }

    @Test
    fun newProcessOnTheSameDayKeepsTheOffset() {
        service().sensor(1000)
        service().sensor(1600)
        newProcess()
        val service = service()
        service.sensor(1650)

        // the first event of a new process always saves, but never touches today's offset
        assertEquals(-1000, stored(day1))
        assertEquals(1650, savedSinceBoot())
    }

    @Test
    fun implausibleSensorValuesAreIgnored() {
        val service = service()
        service.sensor(1000)
        at(day1, 10, 0)
        service.sensorValue(Float.MAX_VALUE)
        assertEquals(1000, savedSinceBoot())
    }
}
