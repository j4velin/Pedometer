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
import kotlinx.coroutines.flow.StateFlow

data class GamesState(val signedIn: Boolean = false, val playerName: String? = null)

/**
 * Achievements and leaderboards. The play flavor implements it with Play Games; the fdroid
 * flavor has none, see [createGamesIntegration] in the flavor source sets.
 */
interface GamesIntegration {

    /** false if this build has no games: the menu entries and the account setting are hidden */
    val available: Boolean

    val state: StateFlow<GamesState>

    /** The main screen was created: signs the player in if possible and updates the scores */
    fun start(activity: Activity)

    fun signIn(activity: Activity)

    fun showAchievements(activity: Activity)

    fun showLeaderboards(activity: Activity)
}
