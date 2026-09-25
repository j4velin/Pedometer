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
import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.tasks.Task
import de.j4velin.pedometer.BuildConfig
import de.j4velin.pedometer.PedometerApp
import de.j4velin.pedometer.R
import de.j4velin.pedometer.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun createGamesIntegration(): GamesIntegration = PlayGamesIntegration()

/** Play Games v2: signs in automatically where possible; there is no sign out */
private class PlayGamesIntegration : GamesIntegration {

    override val available = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(GamesState())
    override val state: StateFlow<GamesState> = _state.asStateFlow()

    override fun start(activity: Activity) {
        PlayGamesSdk.initialize(activity)
        PlayGames.getGamesSignInClient(activity).isAuthenticated()
            .addOnCompleteListener { onSignInResult(activity, it) }
    }

    override fun signIn(activity: Activity) {
        PlayGames.getGamesSignInClient(activity).signIn()
            .addOnCompleteListener { onSignInResult(activity, it) }
    }

    private fun onSignInResult(activity: Activity, task: Task<AuthenticationResult>) {
        val signedIn = task.isSuccessful && task.result.isAuthenticated
        if (BuildConfig.DEBUG) Logger.log("Play Games signed in: $signedIn")
        _state.value = GamesState(signedIn)
        if (!signedIn) return
        PlayGames.getPlayersClient(activity).currentPlayer.addOnSuccessListener { player ->
            _state.value = _state.value.copy(playerName = player.displayName)
        }
        updateAchievementsAndLeaderboards(activity)
    }

    @Suppress("DEPRECATION") // Play Games shows its screens only for startActivityForResult
    override fun showAchievements(activity: Activity) {
        PlayGames.getAchievementsClient(activity).achievementsIntent
            .addOnSuccessListener { activity.startActivityForResult(it, RC_GAMES_UI) }
    }

    @Suppress("DEPRECATION")
    override fun showLeaderboards(activity: Activity) {
        PlayGames.getLeaderboardsClient(activity).allLeaderboardsIntent
            .addOnSuccessListener { activity.startActivityForResult(it, RC_GAMES_UI) }
    }

    /**
     * Unlocks the achievements whose condition is met and which are not unlocked yet, and submits
     * the leaderboard scores. The player must be signed in. Reads the history off the main
     * thread.
     */
    private fun updateAchievementsAndLeaderboards(activity: Activity) {
        val app = PedometerApp.get(activity)
        val history = app.history
        val today = app.accounting.today()
        // the unlocked achievements are remembered in the default preferences, as before
        val prefs = activity.getSharedPreferences(
            activity.packageName + "_preferences", Context.MODE_PRIVATE
        )
        val achievements = PlayGames.getAchievementsClient(activity)
        val leaderboards = PlayGames.getLeaderboardsClient(activity)
        scope.launch {
            history.removeInvalidEntries()
            suspend fun unlock(key: String, id: Int, condition: suspend () -> Boolean) {
                if (!prefs.getBoolean(key, false) && condition()) {
                    achievements.unlock(activity.getString(id))
                    prefs.edit { putBoolean(key, true) }
                }
            }
            suspend fun anyDayWith(steps: Int) = history.daysWithAtLeast(steps, today) > 0

            unlock("achievement_boot_are_made_for_walking", R.string.achievement_boots_made_for_walking) { anyDayWith(7500) }
            unlock("achievement_boot_are_made_for_walking2", R.string.achievement_boots_made_for_walking_ii) { anyDayWith(10000) }
            unlock("achievement_boot_are_made_for_walking3", R.string.achievement_boots_made_for_walking_iii) { anyDayWith(15000) }
            unlock("achievement_boot_are_made_for_walking4", R.string.achievement_boots_made_for_walking_iv) { anyDayWith(20000) }
            unlock("achievement_boot_are_made_for_walking5", R.string.achievement_boots_made_for_walking_v) { anyDayWith(25000) }

            val daysForStamina = history.daysWithAtLeast(10000, today)
            unlock("achievement_stamina", R.string.achievement_stamina) { daysForStamina >= 5 }
            unlock("achievement_stamina2", R.string.achievement_stamina_ii) { daysForStamina >= 10 }
            unlock("achievement_stamina3", R.string.achievement_stamina_iii) { daysForStamina >= 15 }
            unlock("achievement_stamina4", R.string.achievement_stamina_iv) { daysForStamina >= 30 }
            unlock("achievement_stamina5", R.string.achievement_stamina_v) { daysForStamina >= 60 }
            unlock("achievement_stamina6", R.string.achievement_stamina_vi) { daysForStamina >= 100 }

            val summary = history.summary(today)
            val totalSteps = summary.total
            unlock("achievement_marathon", R.string.achievement_marathon) { totalSteps > 100000 }
            unlock("achievement_marathon2", R.string.achievement_marathon_ii) { totalSteps > 200000 }
            unlock("achievement_marathon3", R.string.achievement_marathon_iii) { totalSteps > 500000 }
            unlock("achievement_marathon4", R.string.achievement_marathon_iv) { totalSteps > 750000 }
            unlock("achievement_marathon5", R.string.achievement_marathon_v) { totalSteps > 1000000 }

            if (summary.days >= 10) {
                val average = totalSteps / summary.days.toFloat()
                unlock("achievement_continual", R.string.achievement_continual_i) { average >= 7500 }
                unlock("achievement_continual2", R.string.achievement_continual_ii) { average >= 10000 }
                unlock("achievement_continual3", R.string.achievement_continual_iii) { average >= 12500 }
                leaderboards.submitScore(
                    activity.getString(R.string.leaderboard_highest_average), average.toLong()
                )
            }
            leaderboards.submitScore(
                activity.getString(R.string.leaderboard_most_steps_walked), totalSteps.toLong()
            )
            leaderboards.submitScore(
                activity.getString(R.string.leaderboard_most_steps_walked_in_one_day),
                history.recordSteps().toLong()
            )
        }
    }

    private companion object {
        const val RC_GAMES_UI = 2
    }
}
