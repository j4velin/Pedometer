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
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Android backs up the database and the preferences, and restores them on a new device: the
 * history is valid there, the step counter values are not
 */
class RestoreTest : StepsTest() {

    private val accounting get() = PedometerApp.get(context).accounting
    private val localId get() = File(context.noBackupFilesDir, "install_id")

    @Before
    fun dataOfTheOldDevice() {
        givenRows(day1.minusDays(1) to 8000, day1 to -4000)
        givenSavedSinceBoot(6500)
        prefs.edit().putInt("bootCount", 17).commit()
    }

    /** Android restored the backup: the files it never backs up are gone */
    private fun restoredFromBackup() {
        localId.delete()
        newProcess()
    }

    @Test
    fun keepsTheHistoryButNotTheStepCounterValues() {
        restoredFromBackup()
        assertEquals(mapOf(-1L to 0, millis(day1.minusDays(1)) to 8000), rows())
        assertEquals(-1, prefs.getInt("bootCount", 0))
        assertEquals(null, accounting.today.value.steps)
    }

    @Test
    fun theFirstValueStartsTodayAtZero() {
        restoredFromBackup()
        val service = service()
        // this device has been running for a while
        service.sensor(50000)
        assertEquals(8000, stored(day1.minusDays(1)))
        assertEquals(-50000, stored(day1))
        assertEquals(0, accounting.today.value.steps)

        service.sensor(50100)
        assertEquals(100, accounting.today.value.steps)
    }

    @Test
    fun theFirstValueMayComeFromTheOverview() {
        restoredFromBackup()
        accounting.onLiveStepCounter(50000)
        assertEquals(8000, stored(day1.minusDays(1)))
        assertEquals(-50000, stored(day1))
    }

    @Test
    fun bootCompletedOnTheFirstStartIsNoReboot() {
        restoredFromBackup()
        // Android 15 sends it when the app starts for the first time after being installed
        givenBootCount(3)
        bootCompleted()
        service().sensor(50000)
        assertEquals(8000, stored(day1.minusDays(1)))
        assertEquals(-50000, stored(day1))
    }

    @Test
    fun aRebootBeforeTheFirstValueCountsNormally() {
        restoredFromBackup()
        givenBootCount(3)
        bootCompleted()
        reboot(bootCount = 4)
        at(day1, 9, 0)
        service().sensor(300)
        assertEquals("the steps since the reboot are this device's", 8300, stored(day1.minusDays(1)))
        assertEquals(-300, stored(day1))
    }

    @Test
    fun theSameInstallationKeepsEverything() {
        newProcess()
        assertEquals(
            mapOf(-1L to 6500, millis(day1.minusDays(1)) to 8000, millis(day1) to -4000), rows()
        )
        assertEquals(17, prefs.getInt("bootCount", 0))
    }

    @Test
    fun anUpdateFromAVersionWithoutIdKeepsEverything() {
        localId.delete()
        prefs.edit().remove("install_id").commit()
        newProcess()
        assertEquals(
            mapOf(-1L to 6500, millis(day1.minusDays(1)) to 8000, millis(day1) to -4000), rows()
        )
        assertTrue(localId.exists())
        assertNotNull(prefs.getString("install_id", null))
        assertEquals(localId.readText(), prefs.getString("install_id", null))
    }
}
