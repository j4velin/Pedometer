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

package de.j4velin.pedometer

import de.j4velin.pedometer.testing.StepsTest
import java.io.StringReader
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Travelling: each day is stored as its local midnight, and after a change of the time zone the
 * same date has another one
 */
class TimeZoneTest : StepsTest() {

    private val accounting get() = PedometerApp.get(context).accounting

    private fun travelTo(zone: String) = TimeZone.setDefault(TimeZone.getTimeZone(zone))

    private fun millis(date: LocalDate, zone: String): Long =
        date.atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()

    @Test
    fun theDayKeepsItsEntryAfterFlyingWest() {
        val service = service()
        service.sensor(1000)
        at(day1, 10, 0)
        service.sensor(3000)
        assertEquals(mapOf(-1L to 3000, millis(day1) to -1000), rows())

        // 12:00 in Berlin is 06:00 in New York, still the same date
        travelTo("America/New_York")
        at(day1, 12, 0)
        service.sensor(3600)
        assertEquals(mapOf(-1L to 3600, millis(day1) to -1000), rows())
        assertEquals(2600, accounting.today.value.steps)

        // the next day starts at New York's midnight
        at(day2, 6, 30) // 00:30 in New York
        service.sensor(4000)
        assertEquals(3000, stored(day1))
        assertEquals(-4000, rows()[millis(day2, "America/New_York")])
        assertEquals(0, accounting.today.value.steps)
    }

    @Test
    fun theDayKeepsItsEntryAfterFlyingEast() {
        val service = service()
        service.sensor(1000)
        at(day1, 10, 0)
        service.sensor(3000)

        // 11:00 in Berlin is 14:00 in Dubai: Dubai's midnight was 3 hours before Berlin's
        travelTo("Asia/Dubai")
        at(day1, 11, 0)
        service.sensor(3600)
        assertEquals(mapOf(-1L to 3600, millis(day1) to -1000), rows())
        assertEquals(2600, accounting.today.value.steps)
    }

    @Test
    fun aNewDateAfterALongFlightIsANewDay() {
        val service = service()
        service.sensor(1000)
        at(day1, 20, 0)
        service.sensor(3000)

        // 20:00 in Berlin is 04:00 of the next day in Tokyo
        travelTo("Asia/Tokyo")
        service.sensor(3100)
        assertEquals(2100, stored(day1))
        assertEquals(-3100, rows()[millis(day2, "Asia/Tokyo")])
        assertEquals(0, accounting.today.value.steps)
    }

    @Test
    fun importFromAnotherTimeZone() {
        givenRows(day1 to 5000)
        at(day3, 9, 0)
        val newYork = "America/New_York"
        val csv = "${millis(day1, newYork)};8000\n${millis(day2, newYork)};9000\n"

        val result = accounting.importCsv(StringReader(csv))

        assertEquals(1, result.inserted)
        assertEquals(1, result.overwritten)
        assertEquals(8000, stored(day1))
        assertEquals(9000, stored(day2))
    }
}
