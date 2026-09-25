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

package de.j4velin.pedometer.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.j4velin.pedometer.PedometerApp
import de.j4velin.pedometer.R
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val goal: Int = 0,
    val stepSize: Float = 0f,
    /** "cm" or "ft" */
    val stepUnit: String = "cm",
    /** The result of an export or import, until the user closes it */
    val message: String? = null,
)

/** The settings, and the export and import of the history */
class SettingsViewModel(
    private val app: PedometerApp,
    private val io: CoroutineDispatcher = app.io,
) : ViewModel() {

    private val settings get() = app.settings

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        _state.value = _state.value.copy(
            goal = settings.goal,
            stepSize = settings.stepSize,
            stepUnit = settings.stepUnit,
        )
    }

    fun setGoal(goal: Int) {
        settings.goal = goal
        reload()
        // the notification and the overview show the progress towards the goal
        app.accounting.refresh()
    }

    fun setStepSize(size: Float, unit: String) {
        settings.stepSize = size
        settings.stepUnit = unit
        reload()
    }

    /** Writes the finished days to [uri], a document the user just created */
    fun export(uri: Uri) = viewModelScope.launch {
        val message = withContext(io) {
            try {
                // the document was just created, so there is nothing to truncate (and not
                // every provider supports "wt")
                val stream = app.contentResolver.openOutputStream(uri, "w")
                    ?: throw IOException(uri.toString())
                OutputStreamWriter(stream, Charsets.UTF_8).use { app.accounting.exportCsv(it) }
                app.getString(R.string.data_saved, displayName(uri))
            } catch (e: IOException) {
                e.printStackTrace()
                app.getString(R.string.error_file, e.message)
            } catch (e: SecurityException) {
                e.printStackTrace()
                app.getString(R.string.error_file, e.message)
            }
        }
        _state.value = _state.value.copy(message = message)
    }

    /** Imports a file written by [export], overwriting the days it contains */
    fun import(uri: Uri) = viewModelScope.launch {
        val message = withContext(io) {
            try {
                val stream = app.contentResolver.openInputStream(uri)
                    ?: throw IOException(uri.toString())
                val result = InputStreamReader(stream, Charsets.UTF_8)
                    .use { app.accounting.importCsv(it) }
                buildString {
                    append(
                        plural(R.plurals.entries_imported, result.inserted + result.overwritten)
                    )
                    if (result.overwritten > 0) {
                        append("\n\n").append(
                            plural(R.plurals.entries_overwritten, result.overwritten)
                        )
                    }
                    if (result.ignored > 0) {
                        append("\n\n").append(plural(R.plurals.entries_ignored, result.ignored))
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                app.getString(R.string.file_cant_read, displayName(uri))
            } catch (e: SecurityException) {
                e.printStackTrace()
                app.getString(R.string.file_cant_read, displayName(uri))
            }
        }
        _state.value = _state.value.copy(message = message)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun plural(id: Int, count: Int) = app.resources.getQuantityString(id, count, count)

    private fun displayName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } ?: uri.lastPathSegment
}
