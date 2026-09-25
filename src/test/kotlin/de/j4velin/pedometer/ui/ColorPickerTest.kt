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

package de.j4velin.pedometer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorPickerTest {

    @Test
    fun format() {
        assertEquals("#FFFFFFFF", formatColor(-1))
        assertEquals("#00000000", formatColor(0))
        assertEquals("#80112233", formatColor(0x80112233.toInt()))
    }

    @Test
    fun parse() {
        assertEquals(0x80112233.toInt(), parseColor("#80112233"))
        assertEquals(0x80112233.toInt(), parseColor("80112233"))
        assertEquals(0xFFAABBCC.toInt(), parseColor(" #aabbcc "))
        assertEquals(0, parseColor("#00000000"))
    }

    @Test
    fun parseInvalid() {
        assertNull(parseColor(""))
        assertNull(parseColor("#"))
        assertNull(parseColor("#12345"))
        assertNull(parseColor("#1234567"))
        assertNull(parseColor("#GG112233"))
        assertNull(parseColor("#-1112233"))
    }
}
