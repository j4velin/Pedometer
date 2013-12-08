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

package de.j4velin.pedometer;

import com.google.android.gms.games.GamesClient;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;

/**
 * Class to manage the Google Play achievements
 * 
 */
public class PlayServices {

	/**
	 * Updates the 'most steps walked' leaderboard score
	 * 
	 * @param gc
	 *            the GamesClient
	 * @param c
	 *            the Context
	 * @param totalSteps
	 *            the new score = total steps walked
	 */
	private static void updateTotalLeaderboard(final GamesClient gc, final Context c, int totalSteps) {
		// some cheat detection needed?
		gc.submitScore(c.getString(R.string.leaderboard_most_steps_walked), totalSteps);
	}
	
	/**
	 * Updates the 'most steps walked in one day' leaderboard score
	 * 
	 * @param gc
	 *            the GamesClient
	 * @param c
	 *            the Context
	 * @param steps
	 *            the new score = max number of steps walked in one day
	 */
	private static void updateOneDayLeaderboard(final GamesClient gc, final Context c, int steps) {
		// some cheat detection needed?
		gc.submitScore(c.getString(R.string.leaderboard_most_steps_walked_in_one_day), steps);
	}

	/**
	 * Check the conditions for not-yet-unlocked achievements and unlock them if
	 * the condition is met and updates the leaderboard
	 * 
	 * @param gc
	 *            the GamesClient
	 * @param db
	 *            the Database
	 * @param context
	 *            the Context
	 * @param todayOffset
	 *            step offset for today
	 */
	static void achievementsAndLeaderboard(final GamesClient gc, final Context context) {
		if (gc.isConnected()) {
			Database db = new Database(context);
			db.open();
						
			db.removeInvalidEntries();
			
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			if (!prefs.getBoolean("achievement_boot_are_made_for_walking", false)) {
				Cursor c = db.query(new String[] { "steps" }, "steps >= 7500", null, null, null, null, "1");
				if (c.getCount() >= 1) {
					gc.unlockAchievement(context.getString(R.string.achievement_boots_made_for_walking));
					prefs.edit().putBoolean("achievement_boot_are_made_for_walking", true).apply();
				}
				c.close();
			}
			if (!prefs.getBoolean("achievement_boot_are_made_for_walking2", false)) {
				Cursor c = db.query(new String[] { "steps" }, "steps >= 10000", null, null, null, null, "1");
				if (c.getCount() >= 1) {
					gc.unlockAchievement(context.getString(R.string.achievement_boots_made_for_walking_ii));
					prefs.edit().putBoolean("achievement_boot_are_made_for_walking2", true).apply();
				}
				c.close();
			}
			if (!prefs.getBoolean("achievement_boot_are_made_for_walking3", false)) {
				Cursor c = db.query(new String[] { "steps" }, "steps >= 15000", null, null, null, null, "1");
				if (c.getCount() >= 1) {
					gc.unlockAchievement(context.getString(R.string.achievement_boots_made_for_walking_iii));
					prefs.edit().putBoolean("achievement_boot_are_made_for_walking3", true).apply();
				}
				c.close();
			}

			Cursor c = db.query(new String[] { "steps" }, "steps >= 10000", null, null, null, null, "15");
			int daysForStamina = c.getCount();
			c.close();

			if (!prefs.getBoolean("achievement_stamina", false)) {
				if (daysForStamina >= 5) {
					gc.unlockAchievement(context.getString(R.string.achievement_stamina));
					prefs.edit().putBoolean("achievement_stamina", true).apply();
				}
			}
			if (!prefs.getBoolean("achievement_stamina2", false)) {
				if (daysForStamina >= 10) {
					gc.unlockAchievement(context.getString(R.string.achievement_stamina_ii));
					prefs.edit().putBoolean("achievement_stamina2", true).apply();
				}
			}
			if (!prefs.getBoolean("achievement_stamina3", false)) {
				if (daysForStamina >= 15) {
					gc.unlockAchievement(context.getString(R.string.achievement_stamina_iii));
					prefs.edit().putBoolean("achievement_stamina3", true).apply();
				}
			}

			int totalSteps = db.getTotalWithoutToday();

			if (!prefs.getBoolean("achievement_marathon", false)) {
				if (totalSteps > 100000) {
					gc.unlockAchievement(context.getString(R.string.achievement_marathon));
					prefs.edit().putBoolean("achievement_marathon", true).apply();
				}
			}
			if (!prefs.getBoolean("achievement_marathon2", false)) {
				if (totalSteps > 200000) {
					gc.unlockAchievement(context.getString(R.string.achievement_marathon_ii));
					prefs.edit().putBoolean("achievement_marathon2", true).apply();
				}
			}
			if (!prefs.getBoolean("achievement_marathon3", false)) {
				if (totalSteps > 500000) {
					gc.unlockAchievement(context.getString(R.string.achievement_marathon_iii));
					prefs.edit().putBoolean("achievement_marathon3", true).apply();
				}
			}
			
			updateTotalLeaderboard(gc, context, totalSteps);
					
			updateOneDayLeaderboard(gc, context, db.getRecord());
			
			db.close();
		}
	}
}
