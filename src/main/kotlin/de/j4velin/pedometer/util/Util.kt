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

import java.util.Calendar

object Util {

    /**
     * @return milliseconds since 1.1.1970 for 0:00:00 local time of the day [now] is in
     */
    @JvmStatic
    @JvmOverloads
    fun getToday(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /**
     * @return milliseconds since 1.1.1970 for 0:00:01 local time of the day after the one [now]
     * is in
     */
    @JvmStatic
    @JvmOverloads
    fun getTomorrow(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 1)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DATE, 1)
        timeInMillis
    }
}
