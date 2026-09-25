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

package de.j4velin.pedometer.games

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The F-Droid build has no Google dependencies, and so no games */
fun createGamesIntegration(): GamesIntegration = NoGames

private object NoGames : GamesIntegration {
    override val available = false
    override val state: StateFlow<GamesState> = MutableStateFlow(GamesState()).asStateFlow()
    override fun start(activity: Activity) = Unit
    override fun signIn(activity: Activity) = Unit
    override fun showAchievements(activity: Activity) = Unit
    override fun showLeaderboards(activity: Activity) = Unit
}
