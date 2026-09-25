package de.j4velin.pedometer

import android.content.Intent
import de.j4velin.pedometer.testing.StepsTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Shutdowns, reboots, crashes, and BOOT_COMPLETED broadcasts that are not reboots */
class RebootTest : StepsTest() {

    @Before
    fun bootedOnce() {
        givenBootCount(1)
        prefs.edit().putInt("bootCount", 1).commit()
    }

    /** 1000 steps at 8:00 start the day, 3000 are saved at 12:00, 3200 are not saved yet */
    private fun walkUntilNoon(): Service {
        val service = service()
        service.sensor(1000)
        at(day1, 12, 0)
        service.sensor(3000)
        at(day1, 12, 30)
        service.sensor(3200)
        assertEquals(3000, savedSinceBoot())
        return service
    }

    @Test
    fun cleanShutdownKeepsEveryStep() {
        walkUntilNoon()
        at(day1, 12, 31)
        shutdown()

        // today's row turns from the offset into the finished count: 3200 - 1000
        assertEquals(2200, stored(day1))
        assertEquals(true, prefs.getBoolean("correctShutdown", false))

        at(day1, 12, 35)
        reboot(bootCount = 2)
        assertEquals(0, savedSinceBoot())
        assertFalse(prefs.contains("correctShutdown"))

        at(day1, 13, 0)
        service().sensor(50)
        assertEquals(2200, stored(day1))
        assertEquals(50, savedSinceBoot())
    }

    @Test
    fun crashLosesOnlyTheUnsavedSteps() {
        walkUntilNoon()
        at(day1, 12, 35)
        reboot(bootCount = 2)

        // the 200 steps since the last save are gone
        assertEquals(2000, stored(day1))
        assertEquals(0, savedSinceBoot())
    }

    @Test
    fun shutdownOnANewDayBeforeItsFirstEvent() {
        val service = service()
        service.sensor(1000)
        at(day1, 23, 0)
        service.sensor(5000)
        at(day2, 7, 0)
        shutdown()
        assertEquals(4000, stored(day1))
        assertEquals(-5000, stored(day2))

        at(day2, 7, 5)
        reboot(bootCount = 2)
        // the new day's negative row is dropped on boot
        assertNull(stored(day2))

        at(day2, 7, 30)
        service().sensor(50)
        // with no row for today, the first steps after the reboot go to the last day that has one
        assertEquals(4050, stored(day1))
        assertEquals(-50, stored(day2))
    }

    @Test
    fun bootCompletedWithoutRebootChangesNothing() {
        walkUntilNoon()
        newProcess() // Android 15 sends BOOT_COMPLETED after a force stop, too
        at(day1, 12, 40)
        bootCompleted()

        assertEquals(-1000, stored(day1))
        assertEquals(3000, savedSinceBoot())

        at(day1, 12, 45)
        service().sensor(3300)
        assertEquals(-1000, stored(day1))
        assertEquals(3300, savedSinceBoot())
    }

    @Test
    fun firstBootCompletedWithoutStoredBootCountIsNoReboot() {
        prefs.edit().remove("bootCount").commit()
        walkUntilNoon()
        newProcess()
        bootCompleted()

        assertEquals(-1000, stored(day1))
        assertEquals(3000, savedSinceBoot())
        assertEquals(1, prefs.getInt("bootCount", -1))
    }

    @Test
    fun withoutBootCountEveryBootCompletedIsAReboot() {
        givenBootCount(-1)
        walkUntilNoon()
        newProcess()
        bootCompleted()

        assertEquals(2000, stored(day1))
        assertEquals(0, savedSinceBoot())
    }

    @Test
    fun appUpdateRemembersTheBootCount() {
        prefs.edit().remove("bootCount").putInt("pauseCount", 7).commit()
        givenBootCount(4)
        appUpdated()
        assertEquals(4, prefs.getInt("bootCount", -1))
        assertFalse("left over from the removed pause feature", prefs.contains("pauseCount"))

        walkUntilNoon()
        at(day1, 12, 35)
        reboot(bootCount = 5)
        assertEquals("the reboot is recognised as one", 2000, stored(day1))
    }

    @Test
    fun receiversIgnoreForeignActions() {
        walkUntilNoon()
        newProcess()
        givenBootCount(2)
        BootReceiver().onReceive(context, Intent("com.example.FAKE_BOOT"))
        AppUpdatedReceiver().onReceive(context, Intent("com.example.FAKE_UPDATE"))

        assertEquals(-1000, stored(day1))
        assertEquals(3000, savedSinceBoot())
        assertEquals(1, prefs.getInt("bootCount", -1))
    }
}
