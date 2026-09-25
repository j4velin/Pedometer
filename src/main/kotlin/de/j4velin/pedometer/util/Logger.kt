/*
 * Copyright 2013 Thomas Hoffmann
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

package de.j4velin.pedometer.util

import android.database.Cursor
import android.os.Environment
import android.util.Log
import de.j4velin.pedometer.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date

/** Debug log, to logcat and to Pedometer.txt on the external storage. Does nothing in release builds. */
object Logger {

    private const val APP = "Pedometer"
    private var fw: FileWriter? = null

    @JvmStatic
    fun log(ex: Throwable) {
        log(ex.message.toString())
        ex.stackTrace.forEach { log(it.toString()) }
    }

    @JvmStatic
    fun log(c: Cursor) {
        if (!BuildConfig.DEBUG) return
        log((0 until c.columnCount).joinToString("") { c.getColumnName(it) + "\t| " })
        c.moveToPosition(-1)
        while (c.moveToNext()) {
            log((0 until c.columnCount).joinToString("") { c.getString(it) + "\t| " })
        }
    }

    @JvmStatic
    @Synchronized
    fun log(msg: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(APP, msg)
        try {
            val writer = fw ?: FileWriter(
                @Suppress("DEPRECATION")
                File(Environment.getExternalStorageDirectory(), "$APP.txt"), true
            ).also { fw = it }
            @Suppress("DEPRECATION")
            writer.write(Date().toLocaleString() + " - " + msg + "\n")
            writer.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
