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
import com.google.example.games.basegameutils.BaseGameActivity;

import android.os.Bundle;
import android.view.MenuItem;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;

public class MainActivity extends BaseGameActivity {

	@Override
	protected void onCreate(final Bundle b) {
		super.onCreate(b);
		startService(new Intent(this, SensorListener.class));
		// AppUpdateReceiver is not launched when installing the app,
		// so make sure the newDayAlarm is set
		NewDayReceiver.sheduleAlarmForNextDay(this);

		// Create new fragment and transaction
		Fragment newFragment = new OverviewFragment();
		FragmentTransaction transaction = getFragmentManager().beginTransaction();

		// Replace whatever is in the fragment_container view with this
		// fragment,
		// and add the transaction to the back stack
		transaction.replace(android.R.id.content, newFragment);

		// Commit the transaction
		transaction.commit();
	}

	@Override
	public void onSignInFailed() {
	}

	@Override
	public void onSignInSucceeded() {
		PlayServices.achievementsAndLeaderboard(getGamesClient(), this);
	}

	public GamesClient getGC() {
		return getGamesClient();
	}
	
	public void beginSignIn() {
		beginUserInitiatedSignIn();
	}

	@Override
	public void onBackPressed() {
		if (getFragmentManager().getBackStackEntryCount() > 0) {
			getFragmentManager().popBackStackImmediate();
		} else {
			finish();
		}
	}

	public boolean optionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			getFragmentManager().popBackStackImmediate();
			break;
		case R.id.action_settings:
			getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).addToBackStack(null)
					.commit();
			break;
		case R.id.action_leaderboard:
		case R.id.action_achievements:
			if (getGamesClient().isConnected()) {
				startActivityForResult(item.getItemId() == R.id.action_achievements ? getGamesClient().getAchievementsIntent()
						: getGamesClient().getLeaderboardIntent(getString(R.string.leaderboard_mosts_steps_walked)), 1);
			} else {
				AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
				builder2.setTitle("Sign in necessary");
				builder2.setMessage("Please sign in with your Google+ account to use this feature.");
				builder2.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
					}
				});
				builder2.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				builder2.create().show();
			}
			break;
		}
		return true;
	}
}