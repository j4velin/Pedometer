package de.j4velin.pedometer.ui

import android.net.Uri
import de.j4velin.pedometer.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Export and import of the history as "date;steps" lines */
class CsvTest : ScreenTest() {

    @get:Rule
    val folder = TemporaryFolder()

    private val today get() = day3

    // a file: URI has no display name, so the app falls back to its last path segment - which on
    // Windows is the whole path. A real document picker hands out content: URIs with a name.
    private fun displayName(file: File) = Uri.fromFile(file).lastPathSegment

    @Before
    fun history() {
        at(today, 9, 0)
        givenRows(
            day1.minusDays(1) to -3,
            day1 to 8000,
            day2 to 12000,
            today to -4000,
        )
        givenSavedSinceBoot(6500)
    }

    @Test
    fun exportWritesFinishedDaysOnly() {
        val file = folder.newFile("Pedometer.csv")
        overview().settings().documentPicked(1, file)

        assertEquals(
            "${millis(day1.minusDays(1))};0\n${millis(day1)};8000\n${millis(day2)};12000\n",
            file.readText()
        )
        assertEquals(context.getString(R.string.data_saved, displayName(file)), latestMessage())
    }

    @Test
    fun importOverwritesAddsAndSkips() {
        val file = folder.newFile("backup.csv")
        file.writeText(
            listOf(
                "${millis(day1)};9000", // overwrites
                "${millis(day1.minusDays(5))};3000", // new
                "not a number;5", // ignored
                "${millis(day2)}", // no steps: ignored
                "${millis(today)};0", // today: skipped silently, it would reset today's steps
                "${millis(today.plusDays(1))};5", // the future: skipped silently too
            ).joinToString("\n")
        )
        overview().settings().documentPicked(2, file)

        assertEquals(9000, stored(day1))
        assertEquals(3000, stored(day1.minusDays(5)))
        assertEquals(12000, stored(day2))
        assertEquals(-4000, stored(today))
        assertEquals(null, stored(today.plusDays(1)))
        assertEquals(
            context.getString(R.string.entries_imported, 2) + "\n\n" +
                context.getString(R.string.entries_overwritten, 1) + "\n\n" +
                context.getString(R.string.entries_ignored, 2),
            latestMessage()
        )
    }

    @Test
    fun exportThenImportRestoresTheHistory() {
        val file = folder.newFile("roundtrip.csv")
        val settings = overview().settings()
        settings.documentPicked(1, file)
        withDb { it.writableDatabase.delete("steps", "date > 0 AND date < ?", arrayOf(millis(today).toString())) }

        settings.documentPicked(2, file)
        assertEquals(
            mapOf(-1L to 6500, millis(day1.minusDays(1)) to 0, millis(day1) to 8000,
                millis(day2) to 12000, millis(today) to -4000),
            rows()
        )
    }

    @Test
    fun unreadableFileIsReported() {
        val missing = File(folder.root, "gone.csv")
        overview().settings().documentPicked(2, missing)
        assertEquals(context.getString(R.string.file_cant_read, displayName(missing)), latestMessage())
        assertEquals(8000, stored(day1))
    }
}
