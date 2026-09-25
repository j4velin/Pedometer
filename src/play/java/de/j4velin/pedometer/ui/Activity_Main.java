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
import android.os.Bundle;

import com.google.android.gms.games.AuthenticationResult;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.PlayGamesSdk;
import com.google.android.gms.tasks.Task;

import de.j4velin.pedometer.BuildConfig;
import de.j4velin.pedometer.R;
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.PlayServices;

public class Activity_Main extends PedometerActivity {

    private final static int RC_LEADERBOARDS = 2;

    private boolean signedIn;

    @Override
    protected void onCreate(final Bundle b) {
        super.onCreate(b);
        // Play Games v2 signs the player in automatically, if possible
        PlayGamesSdk.initialize(this);
        PlayGames.getGamesSignInClient(this).isAuthenticated()
                .addOnCompleteListener(this::onSignInResult);
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

    @Override
    protected void onGamesItem(final int id) {
        if (signedIn) {
            (id == R.id.action_achievements ?
                    PlayGames.getAchievementsClient(this).getAchievementsIntent() :
                    PlayGames.getLeaderboardsClient(this).getAllLeaderboardsIntent())
                    .addOnSuccessListener(
                            intent -> startActivityForResult(intent, RC_LEADERBOARDS));
        } else {
            new AlertDialog.Builder(this).setTitle(R.string.sign_in_necessary)
                    .setMessage(R.string.please_sign_in_with_your_google_account)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        dialog.dismiss();
                        openSettings();
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .create().show();
        }
    }
}
