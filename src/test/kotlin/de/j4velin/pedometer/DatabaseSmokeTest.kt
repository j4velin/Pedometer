package de.j4velin.pedometer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the test stack: Kotlin tests, Robolectric and SQLite against the Java [Database] */
@RunWith(AndroidJUnit4::class)
class DatabaseSmokeTest {

    @Test
    fun savedStepsSinceBootAreReadBack() {
        val db = Database.getInstance(ApplicationProvider.getApplicationContext())
        try {
            db.saveCurrentSteps(1234)
            assertEquals(1234, db.currentSteps)
        } finally {
            db.close()
        }
    }
}
