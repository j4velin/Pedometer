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

import de.j4velin.pedometer.data.Settings
import de.j4velin.pedometer.testing.StepsTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest : StepsTest() {

    @Test
    fun plausibleStepSizes() {
        assertTrue(Settings.isValidStepSize(75f, "cm"))
        assertTrue(Settings.isValidStepSize(2.5f, "ft"))
        assertFalse(Settings.isValidStepSize(0f, "cm"))
        assertFalse(Settings.isValidStepSize(-75f, "cm"))
        assertFalse(Settings.isValidStepSize(Float.NaN, "cm"))
        assertFalse(Settings.isValidStepSize(Float.POSITIVE_INFINITY, "ft"))
        assertFalse("75 ft is no step", Settings.isValidStepSize(75f, "ft"))
        assertFalse("2.5 cm is no step either", Settings.isValidStepSize(2.5f, "cm"))
    }

    @Test
    fun implausibleStoredStepSizeReadsAsTheDefaultForItsUnit() {
        prefs.edit().putString("stepsize_unit", "cm").putFloat("stepsize_value", Float.NaN).commit()
        assertEquals(75f, Settings(context).stepSize)
        prefs.edit().putString("stepsize_unit", "ft").putFloat("stepsize_value", -1f).commit()
        assertEquals(2.5f, Settings(context).stepSize)
        prefs.edit().putFloat("stepsize_value", 2.2f).commit()
        assertEquals(2.2f, Settings(context).stepSize)
    }
}
