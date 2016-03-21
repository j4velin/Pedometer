/*
 * Copyright 2016 Thomas Hoffmann
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.games.Games;

import de.j4velin.pedometer.BuildConfig;
import de.j4velin.pedometer.R;
import de.j4velin.pedometer.ui.Activity_Main;

/**
 * Class to wrap some Google Play related stuff in the SettingsFragment
 */
public class PlaySettingsWrapper {

    public static void setupAccountSetting(final Preference account,
                                           final Bundle savedInstanceState,
                                           final Activity_Main main) {
        account.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                AlertDialog.Builder builder = new AlertDialog.Builder(main);
                View v = main.getLayoutInflater().inflate(R.layout.signin, null);
                builder.setView(v);
                builder.setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                if (main.getGC().isConnected()) {
                    ((TextView) v.findViewById(R.id.signedin)).setText(
                            main.getString(R.string.signed_in,
                                    Games.Players.getCurrentPlayer(main.getGC()).getDisplayName()));
                    v.findViewById(R.id.sign_in_button).setVisibility(View.GONE);
                    builder.setPositiveButton(R.string.sign_out,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    main.signOut();
                                    preference.setSummary(main.getString(R.string.sign_in));
                                    dialog.dismiss();
                                }
                            });
                }
                final Dialog d = builder.create();

                if (!main.getGC().isConnected()) {
                    v.findViewById(R.id.signedin).setVisibility(View.GONE);
                    v.findViewById(R.id.sign_in_button)
                            .setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(final View v) {
                                    // start the asynchronous sign in flow
                                    main.beginSignIn();
                                    d.dismiss();
                                }
                            });
                }
                d.show();
                return false;
            }
        });
        // If created for the first time, the GameClient should be setup and be
        // connected, but when recreating the fragment (due to orientation
        // change for example), then the fragment's onCreate is called before
        // the new GamesClient is setup. In this case, just use the player name
        // saved in the savedInstanceState bundle
        if ((savedInstanceState == null && main.getGC().

                isConnected()

        ) || (savedInstanceState != null && savedInstanceState.containsKey("player")))

        {
            account.setSummary(main.getString(R.string.signed_in, savedInstanceState == null ?
                    Games.Players.getCurrentPlayer(main.getGC()).getDisplayName() :
                    savedInstanceState.getString("player")));
        }

    }

    public static void onSavedInstance(final Bundle outState, final Activity_Main main) {
        try {
            if (main.getGC().isConnected()) outState.putString("player",
                    Games.Players.getCurrentPlayer(main.getGC()).getDisplayName());
            else outState.remove("player");
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
        }
    }

}
