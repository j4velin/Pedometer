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

package de.j4velin.pedometer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Material 3 "tonal spot" schemes generated from the app's green #99CC00 (Material color
// utilities, 2021 spec). Every role is set: a missing one would fall back to Material's baseline
// purple rather than to the rest of this palette. The window background in themes.xml has to
// match [LightColors].background and [DarkColors].background.

private val LightColors = lightColorScheme(
    primary = Color(0xFF526526),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4EC9D),
    onPrimaryContainer = Color(0xFF3B4D10),
    inversePrimary = Color(0xFFB8CF84),
    secondary = Color(0xFF5A6147),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE6C5),
    onSecondaryContainer = Color(0xFF424A31),
    tertiary = Color(0xFF396660),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE4),
    onTertiaryContainer = Color(0xFF204E48),
    background = Color(0xFFFAFAEE),
    onBackground = Color(0xFF1A1C15),
    surface = Color(0xFFFAFAEE),
    onSurface = Color(0xFF1A1C15),
    surfaceVariant = Color(0xFFE2E4D4),
    onSurfaceVariant = Color(0xFF45483C),
    surfaceTint = Color(0xFF526526),
    inverseSurface = Color(0xFF2F3129),
    inverseOnSurface = Color(0xFFF1F1E5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF76786B),
    outlineVariant = Color(0xFFC6C8B9),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFAEE),
    surfaceContainer = Color(0xFFEFEFE3),
    surfaceContainerHigh = Color(0xFFE9E9DD),
    surfaceContainerHighest = Color(0xFFE3E3D7),
    surfaceContainerLow = Color(0xFFF4F4E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDADBCF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8CF84),
    onPrimary = Color(0xFF263500),
    primaryContainer = Color(0xFF3B4D10),
    onPrimaryContainer = Color(0xFFD4EC9D),
    inversePrimary = Color(0xFF526526),
    secondary = Color(0xFFC2CAAA),
    onSecondary = Color(0xFF2C331D),
    secondaryContainer = Color(0xFF424A31),
    onSecondaryContainer = Color(0xFFDEE6C5),
    tertiary = Color(0xFFA0D0C8),
    onTertiary = Color(0xFF023732),
    tertiaryContainer = Color(0xFF204E48),
    onTertiaryContainer = Color(0xFFBCECE4),
    background = Color(0xFF12140D),
    onBackground = Color(0xFFE3E3D7),
    surface = Color(0xFF12140D),
    onSurface = Color(0xFFE3E3D7),
    surfaceVariant = Color(0xFF45483C),
    onSurfaceVariant = Color(0xFFC6C8B9),
    surfaceTint = Color(0xFFB8CF84),
    inverseSurface = Color(0xFFE3E3D7),
    inverseOnSurface = Color(0xFF2F3129),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF909284),
    outlineVariant = Color(0xFF45483C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383A32),
    surfaceContainer = Color(0xFF1E2019),
    surfaceContainerHigh = Color(0xFF292B23),
    surfaceContainerHighest = Color(0xFF34362E),
    surfaceContainerLow = Color(0xFF1A1C15),
    surfaceContainerLowest = Color(0xFF0D0F08),
    surfaceDim = Color(0xFF12140D),
)

@Composable
fun PedometerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
