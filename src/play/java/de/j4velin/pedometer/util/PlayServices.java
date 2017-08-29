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

package de.j4velin.pedometer.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;

import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.R;

/**
 * Class to manage the Google Play achievements
 */
public abstract class PlayServices {

    /**
     * Updates the 'most steps walked' leaderboard score
     *
     * @param gc         the GamesClient
     * @param c          the Context
     * @param totalSteps the new score = total steps walked
     */
    private static void updateTotalLeaderboard(final GoogleApiClient gc, final Context c, int totalSteps) {
        // some cheat detection needed?
        Games.Leaderboards
                .submitScore(gc, c.getString(R.string.leaderboard_most_steps_walked), totalSteps);
    }

    /**
     * Updates the 'most steps walked in one day' leaderboard score
     *
     * @param gc    the GamesClient
     * @param c     the Context
     * @param steps the new score = max number of steps walked in one day
     */
    private static void updateOneDayLeaderboard(final GoogleApiClient gc, final Context c, int steps) {
        // some cheat detection needed?
        Games.Leaderboards
                .submitScore(gc, c.getString(R.string.leaderboard_most_steps_walked_in_one_day),
                        steps);
    }

    /**
     * Updates the 'hightest average' leaderboard score
     *
     * @param gc  the GamesClient
     * @param c   the Context
     * @param avg the new score = current average
     */
    private static void updateAverageLeaderboard(final GoogleApiClient gc, final Context c, float avg) {
        // some cheat detection needed?
        Games.Leaderboards
                .submitScore(gc, c.getString(R.string.leaderboard_highest_average), (long) avg);
    }

    /**
     * Check the conditions for not-yet-unlocked achievements and unlock them if
     * the condition is met and updates the leaderboard
     *
     * @param gc      the GamesClient
     * @param context the Context
     */
    public static void achievementsAndLeaderboard(final GoogleApiClient gc, final Context context) {
        if (gc.isConnected()) {
            Database db = Database.getInstance(context);
            db.removeInvalidEntries();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean("achievement_boot_are_made_for_walking", false)) {
                Cursor c = db.query(new String[]{"steps"}, "steps >= 7500 AND date > 0", null, null,
                        null, null, "1");
                if (c.getCount() >= 1) {
                    unlockAchievement(gc,
                            context.getString(R.string.achievement_boots_made_for_walking));
                    prefs.edit().putBoolean("achievement_boot_are_made_for_walking", true).apply();
                }
                c.close();
            }
            if (!prefs.getBoolean("achievement_boot_are_made_for_walking2", false)) {
                Cursor c =
                        db.query(new String[]{"steps"}, "steps >= 10000 AND date > 0", null, null,
                                null, null, "1");
                if (c.getCount() >= 1) {
                    unlockAchievement(gc,
                            context.getString(R.string.achievement_boots_made_for_walking_ii));
                    prefs.edit().putBoolean("achievement_boot_are_made_for_walking2", true).apply();
                }
                c.close();
            }
            if (!prefs.getBoolean("achievement_boot_are_made_for_walking3", false)) {
                Cursor c =
                        db.query(new String[]{"steps"}, "steps >= 15000 AND date > 0", null, null,
                                null, null, "1");
                if (c.getCount() >= 1) {
                    unlockAchievement(gc,
                            context.getString(R.string.achievement_boots_made_for_walking_iii));
                    prefs.edit().putBoolean("achievement_boot_are_made_for_walking3", true).apply();
                }
                c.close();
            }
            if (!prefs.getBoolean("achievement_boot_are_made_for_walking4", false)) {
                Cursor c =
                        db.query(new String[]{"steps"}, "steps >= 20000 AND date > 0", null, null,
                                null, null, "1");
                if (c.getCount() >= 1) {
                    unlockAchievement(gc,
                            context.getString(R.string.achievement_boots_made_for_walking_iv));
                    prefs.edit().putBoolean("achievement_boot_are_made_for_walking4", true).apply();
                }
                c.close();
            }
            if (!prefs.getBoolean("achievement_boot_are_made_for_walking5", false)) {
                Cursor c =
                        db.query(new String[]{"steps"}, "steps >= 25000 AND date > 0", null, null,
                                null, null, "1");
                if (c.getCount() >= 1) {
                    unlockAchievement(gc,
                            context.getString(R.string.achievement_boots_made_for_walking_v));
                    prefs.edit().putBoolean("achievement_boot_are_made_for_walking5", true).apply();
                }
                c.close();
            }

            Cursor c = db.query(new String[]{"COUNT(*)"}, "steps >= 10000 AND date > 0", null, null,
                    null, null, null);
            c.moveToFirst();
            int daysForStamina = c.getInt(0);
            c.close();

            if (!prefs.getBoolean("achievement_stamina", false)) {
                if (daysForStamina >= 5) {
                    unlockAchievement(gc, context.getString(R.string.achievement_stamina));
                    prefs.edit().putBoolean("achievement_stamina", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_stamina2", false)) {
                if (daysForStamina >= 10) {
                    unlockAchievement(gc, context.getString(R.string.achievement_stamina_ii));
                    prefs.edit().putBoolean("achievement_stamina2", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_stamina3", false)) {
                if (daysForStamina >= 15) {
                    unlockAchievement(gc, context.getString(R.string.achievement_stamina_iii));
                    prefs.edit().putBoolean("achievement_stamina3", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_stamina4", false)) {
                if (daysForStamina >= 30) {
                    unlockAchievement(gc, context.getString(R.string.achievement_stamina_iv));
                    prefs.edit().putBoolean("achievement_stamina4", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_stamina5", false)) {
                if (daysForStamina >= 60) {
                    unlockAchievement(gc, context.getString(R.string.achievement_stamina_v));
                    prefs.edit().putBoolean("achievement_stamina5", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_stamina6", false)) {
                if (daysForStamina >= 100) {
                    unlockAchievement(gc, context.getString(R.string.achievement_stamina_vi));
                    prefs.edit().putBoolean("achievement_stamina6", true).apply();
                }
            }

            int totalSteps = db.getTotalWithoutToday();

            if (!prefs.getBoolean("achievement_marathon", false)) {
                if (totalSteps > 100000) {
                    unlockAchievement(gc, context.getString(R.string.achievement_marathon));
                    prefs.edit().putBoolean("achievement_marathon", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_marathon2", false)) {
                if (totalSteps > 200000) {
                    unlockAchievement(gc, context.getString(R.string.achievement_marathon_ii));
                    prefs.edit().putBoolean("achievement_marathon2", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_marathon3", false)) {
                if (totalSteps > 500000) {
                    unlockAchievement(gc, context.getString(R.string.achievement_marathon_iii));
                    prefs.edit().putBoolean("achievement_marathon3", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_marathon4", false)) {
                if (totalSteps > 750000) {
                    unlockAchievement(gc, context.getString(R.string.achievement_marathon_iv));
                    prefs.edit().putBoolean("achievement_marathon4", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_marathon5", false)) {
                if (totalSteps > 1000000) {
                    unlockAchievement(gc, context.getString(R.string.achievement_marathon_v));
                    prefs.edit().putBoolean("achievement_marathon5", true).apply();
                }
            }

            int days = db.getDaysWithoutToday();
            if (days >= 10) {
                float average = totalSteps / (float) days;
                if (!prefs.getBoolean("achievement_continual", false)) {
                    if (average >= 7500) {
                        unlockAchievement(gc, context.getString(R.string.achievement_continual_i));
                        prefs.edit().putBoolean("achievement_continual", true).apply();
                    }
                }
                if (!prefs.getBoolean("achievement_continual2", false)) {
                    if (average >= 10000) {
                        unlockAchievement(gc, context.getString(R.string.achievement_continual_ii));
                        prefs.edit().putBoolean("achievement_continual2", true).apply();
                    }
                }
                if (!prefs.getBoolean("achievement_continual3", false)) {
                    if (average >= 12500) {
                        unlockAchievement(gc,
                                context.getString(R.string.achievement_continual_iii));
                        prefs.edit().putBoolean("achievement_continual3", true).apply();
                    }
                }
                updateAverageLeaderboard(gc, context, average);
            }

            updateTotalLeaderboard(gc, context, totalSteps);

            updateOneDayLeaderboard(gc, context, db.getRecord());

            db.close();
        }
    }

    private static void unlockAchievement(GoogleApiClient gc, String achivmentName) {
        Games.Achievements.unlock(gc, achivmentName);
    }
}
