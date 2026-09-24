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

package de.j4velin.pedometer.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.games.AuthenticationResult;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.PlayGamesSdk;
import com.google.android.gms.tasks.Task;

import de.j4velin.pedometer.BuildConfig;
import de.j4velin.pedometer.R;
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.PlayServices;

public class Activity_Main extends FragmentActivity {

    private final static int RC_LEADERBOARDS = 2;

    private boolean signedIn;

    @Override
    protected void onCreate(final Bundle b) {
        super.onCreate(b);
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

        ActivityHelper.onCreate(this, b);

        // Play Games v2 signs the player in automatically, if possible
        PlayGamesSdk.initialize(this);
        PlayGames.getGamesSignInClient(this).isAuthenticated()
                .addOnCompleteListener(this::onSignInResult);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, final String[] permissions,
                                           final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ActivityHelper.onRequestPermissionsResult(this, requestCode);
    }

    public boolean isSignedIn() {
        return signedIn;
    }

    public void beginSignIn() {
        PlayGames.getGamesSignInClient(this).signIn().addOnCompleteListener(this::onSignInResult);
    }

    private void onSignInResult(final Task<AuthenticationResult> task) {
        signedIn = task.isSuccessful() && task.getResult().isAuthenticated();
        if (BuildConfig.DEBUG) Logger.log("Play Games signed in: " + signedIn);
        if (signedIn) {
            PlayServices.achievementsAndLeaderboard(this, this);
        }
    }

    public boolean optionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            getFragmentManager().popBackStackImmediate();
        } else if (id == R.id.action_settings) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new Fragment_Settings()).addToBackStack(null)
                    .commit();
        } else if (id == R.id.action_leaderboard
                || id == R.id.action_achievements) {
            if (signedIn) {
                (item.getItemId() == R.id.action_achievements ?
                        PlayGames.getAchievementsClient(this).getAchievementsIntent() :
                        PlayGames.getLeaderboardsClient(this).getAllLeaderboardsIntent())
                        .addOnSuccessListener(
                                intent -> startActivityForResult(intent, RC_LEADERBOARDS));
            } else {
                AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
                builder2.setTitle(R.string.sign_in_necessary);
                builder2.setMessage(R.string.please_sign_in_with_your_google_account);
                builder2.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                getFragmentManager().beginTransaction()
                                        .replace(android.R.id.content, new Fragment_Settings())
                                        .addToBackStack(null).commit();
                            }
                        });
                builder2.setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                builder2.create().show();
            }
        } else if (id == R.id.action_faq) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://j4velin.de/faq/index.php?app=pm"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (id == R.id.action_about) {
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
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            builder.create().show();
        }
        return true;
    }
}
