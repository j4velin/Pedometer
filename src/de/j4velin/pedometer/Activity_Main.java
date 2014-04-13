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

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.example.games.basegameutils.BaseGameActivity;

import de.j4velin.pedometer.background.SensorListener;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.widget.TextView;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;

public class Activity_Main extends BaseGameActivity {

	@Override
	protected void onCreate(final Bundle b) {
		super.onCreate(b);
		startService(new Intent(this, SensorListener.class));
		if (b == null) {
			// Create new fragment and transaction
			Fragment newFragment = new Fragment_Overview();
			FragmentTransaction transaction = getFragmentManager().beginTransaction();

			// Replace whatever is in the fragment_container view with this
			// fragment,
			// and add the transaction to the back stack
			transaction.replace(android.R.id.content, newFragment);

			// Commit the transaction
			transaction.commit();
		}
		getGameHelper().setConnectOnStart(
				getSharedPreferences("pedometer_playservices", Context.MODE_PRIVATE).getBoolean("autosignin", true));
	}

	@Override
	public void onSignInFailed() {
	}

	@Override
	public void onSignInSucceeded() {
		PlayServices.achievementsAndLeaderboard(getApiClient(), this);
		getSharedPreferences("pedometer_playservices", Context.MODE_PRIVATE).edit().putBoolean("autosignin", true).apply();
	}

	public GoogleApiClient getGC() {
		return getApiClient();
	}

	public void beginSignIn() {
		beginUserInitiatedSignIn();
	}

	public void signOut() {
		super.signOut();
		getSharedPreferences("pedometer_playservices", Context.MODE_PRIVATE).edit().putBoolean("autosignin", false).apply();
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
			getFragmentManager().beginTransaction().replace(android.R.id.content, new Fragment_Settings()).addToBackStack(null)
					.commit();
			break;
		case R.id.action_leaderboard:
		case R.id.action_achievements:
			if (getApiClient().isConnected()) {
				startActivityForResult(
						item.getItemId() == R.id.action_achievements ? Games.Achievements.getAchievementsIntent(getApiClient())
								: Games.Leaderboards.getAllLeaderboardsIntent(getApiClient()), 1);
			} else {
				AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
				builder2.setTitle(R.string.sign_in_necessary);
				builder2.setMessage(R.string.please_sign_in_with_your_google_account);
				builder2.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						getFragmentManager().beginTransaction().replace(android.R.id.content, new Fragment_Settings()).commit();
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
		case R.id.action_faq:
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://j4velin-systems.de/faq/index.php?app=pm"))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			break;
		case R.id.action_about:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.about);
			TextView tv = new TextView(this);
			tv.setPadding(10, 10, 10, 10);
			tv.setText(R.string.about_text_links);
			try {
				tv.append(getString(R.string.about_app_version,
						getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
			} catch (NameNotFoundException e1) {
				// should not happen as the app is definitely installed when
				// seeing the dialog
				e1.printStackTrace();
			}
			tv.setMovementMethod(LinkMovementMethod.getInstance());
			builder.setView(tv);
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			builder.create().show();
			break;
		}
		return true;
	}
}