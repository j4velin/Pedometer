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

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import de.j4velin.pedometer.data.Settings
import de.j4velin.pedometer.data.StepsDatabase
import de.j4velin.pedometer.data.StepsHistory
import de.j4velin.pedometer.domain.StepAccounting
import de.j4velin.pedometer.games.GamesIntegration
import de.j4velin.pedometer.games.createGamesIntegration
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Holds the objects that live as long as the process */
class PedometerApp : Application() {

    lateinit var database: StepsDatabase
        private set
    lateinit var settings: Settings
        private set
    lateinit var accounting: StepAccounting
        private set
    lateinit var history: StepsHistory
        private set

    /** Where reading the history and files happens. Tests run it inline. */
    var io: CoroutineDispatcher = Dispatchers.IO
        @VisibleForTesting set

    /** Play Games in the play flavor, nothing in fdroid */
    val games: GamesIntegration by lazy { createGamesIntegration() }

    override fun onCreate() {
        super.onCreate()
        createObjects()
    }

    /** Creates everything anew, as a new process would. Tests use it to simulate one. */
    @VisibleForTesting
    internal fun createObjects() {
        database = StepsDatabase(this)
        settings = Settings(this)
        accounting = StepAccounting(database, settings)
        history = StepsHistory(database, io)
        if (restoredFromBackup()) accounting.onRestored()
    }

    /**
     * Whether the data comes from a backup of another installation: the id in the preferences,
     * which Android backs up, differs from the one in the files it never backs up
     */
    private fun restoredFromBackup(): Boolean {
        val file = File(noBackupFilesDir, INSTALL_ID_FILE)
        val local = file.takeIf { it.exists() }?.readText()
        val backedUp = settings.installId
        if (local != null && local == backedUp) return false
        val id = local ?: UUID.randomUUID().toString().also { file.writeText(it) }
        settings.installId = id
        // without any id, this is a new installation or an update from a version before ids
        return backedUp != null
    }

    companion object {
        private const val INSTALL_ID_FILE = "install_id"

        @JvmStatic
        fun get(context: Context): PedometerApp = context.applicationContext as PedometerApp
    }
}
