package de.j4velin.pedometer.testing

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.j4velin.pedometer.AppUpdatedReceiver
import de.j4velin.pedometer.BootReceiver
import de.j4velin.pedometer.Database
import de.j4velin.pedometer.SensorListener
import de.j4velin.pedometer.ShutdownRecevier
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

/**
 * Base class for the characterization tests of the step accounting.
 *
 * The tests describe *what happens to the step history* in a scenario: sensor events, midnight,
 * shutdowns, reboots, imports. Everything that depends on how the app is built today - which
 * class receives the sensor event, which static fields have to be reset for a "new process" -
 * lives here and in the drivers, so that the migration only has to change the harness, never the
 * expectations in the tests.
 *
 * The clock is Robolectric's: robolectric.properties instruments the app's package, so its calls
 * to System.currentTimeMillis() return the time [at] last set with SystemClock.setCurrentTimeMillis
 * (plus the few milliseconds the looper adds while it runs animations). Time can only move forward
 * within a test.
 */
@RunWith(AndroidJUnit4::class)
abstract class StepsTest {

    protected val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private lateinit var previousZone: TimeZone
    private lateinit var previousLocale: Locale

    protected val context: Application get() = ApplicationProvider.getApplicationContext()
    protected val prefs: SharedPreferences
        get() = context.getSharedPreferences("pedometer", Context.MODE_PRIVATE)

    /** An ordinary Thursday, far from any DST switch */
    protected val day1: LocalDate = LocalDate.of(2030, 3, 14)
    protected val day2: LocalDate = day1.plusDays(1)
    protected val day3: LocalDate = day1.plusDays(2)

    @Before
    fun setUpStepsTest() {
        previousZone = TimeZone.getDefault()
        previousLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        Locale.setDefault(Locale.GERMANY)
        newProcess()
        at(day1, 8, 0)
    }

    @After
    fun tearDownStepsTest() {
        closeDatabase()
        TimeZone.setDefault(previousZone)
        Locale.setDefault(previousLocale)
    }

    // ---- clock ----

    protected fun millis(date: LocalDate, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    /** Moves the clock forward to the given local time */
    protected fun at(date: LocalDate, hour: Int, minute: Int = 0) {
        val target = millis(date, hour, minute)
        check(target >= now) { "the clock can only move forward" }
        SystemClock.setCurrentTimeMillis(target)
        now = target
    }

    private var now = 0L

    // ---- the database, as the app stores it ----

    /** Every row of the steps table, keyed by date, including the -1 row ("steps since boot") */
    protected fun rows(): Map<Long, Int> = withDb { db ->
        db.query(arrayOf("date", "steps"), null, null, null, null, "date", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) }
        }
    }

    /** The stored value for [date]: the step count for a past day, the negative offset for today */
    protected fun stored(date: LocalDate): Int? = rows()[millis(date)]

    /** The last "steps since boot" value the app saved */
    protected fun savedSinceBoot(): Int? = rows()[-1L]

    protected fun givenRows(vararg entries: Pair<LocalDate, Int>) = withDb { db ->
        entries.forEach { (date, steps) -> db.insertDayFromBackup(millis(date), steps) }
    }

    protected fun givenSavedSinceBoot(steps: Int) = withDb { it.saveCurrentSteps(steps) }

    protected fun <T> withDb(block: (Database) -> T): T {
        val db = Database.getInstance(context)
        try {
            return block(db)
        } finally {
            db.close()
        }
    }

    // ---- the system ----

    /**
     * Forgets everything the app keeps in memory, as after the process was killed. The database
     * and preferences stay.
     */
    protected fun newProcess() {
        closeDatabase()
        setStatic(SensorListener::class.java, "steps", 0)
        setStatic(SensorListener::class.java, "lastSaveSteps", 0)
        setStatic(SensorListener::class.java, "lastSaveTime", 0L)
    }

    protected fun givenBootCount(count: Int) {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, count)
    }

    /** The system shuts down cleanly: the service's shutdown receiver gets ACTION_SHUTDOWN */
    protected fun shutdown() {
        ShutdownRecevier().onReceive(context, Intent(Intent.ACTION_SHUTDOWN))
    }

    /** The device boots with a new boot count; the step counter starts at 0 again */
    protected fun reboot(bootCount: Int) {
        newProcess()
        givenBootCount(bootCount)
        bootCompleted()
    }

    /** BOOT_COMPLETED arrives, which since Android 15 does not have to mean a reboot */
    protected fun bootCompleted() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }

    protected fun appUpdated() {
        AppUpdatedReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    // ---- the step counter service ----

    protected inner class Service {
        private val controller: ServiceController<SensorListener> =
            Robolectric.buildService(SensorListener::class.java).create().startCommand(0, 1)

        /** The step counter reports [stepsSinceBoot] */
        fun sensor(stepsSinceBoot: Int) = sensorValue(stepsSinceBoot.toFloat())

        /** The step counter reports a raw float, as the sensor framework delivers it */
        fun sensorValue(value: Float) = controller.get().onSensorChanged(stepEvent(value))

        /** The hourly alarm (or anything else) starts the service again */
        fun restart() {
            controller.startCommand(0, 2)
        }
    }

    protected fun service() = Service()

    companion object {
        fun stepEvent(value: Float): SensorEvent =
            SensorEventBuilder.newBuilder()
                .setSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
                .setValues(floatArrayOf(value))
                .setTimestamp(0)
                .build()

        private fun closeDatabase() {
            val instance = Database::class.java.getDeclaredField("instance")
            instance.isAccessible = true
            val counter = Database::class.java.getDeclaredField("openCounter")
            counter.isAccessible = true
            (instance.get(null) as Database?)?.let {
                (counter.get(null) as AtomicInteger).set(1)
                it.close()
            }
            instance.set(null, null)
            (counter.get(null) as AtomicInteger).set(0)
        }

        private fun setStatic(cls: Class<*>, name: String, value: Any) {
            val field = cls.getDeclaredField(name)
            field.isAccessible = true
            field.set(null, value)
        }
    }
}
