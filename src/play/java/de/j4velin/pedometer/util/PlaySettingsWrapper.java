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

import com.google.android.gms.games.PlayGames;

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
                final View v = main.getLayoutInflater().inflate(R.layout.signin, null);
                builder.setView(v);
                builder.setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                final Dialog d = builder.create();
                if (main.isSignedIn()) {
                    // Play Games v2 has no sign out: players manage that in the Play Games app
                    v.findViewById(R.id.sign_in_button).setVisibility(View.GONE);
                    showPlayerName(main, (TextView) v.findViewById(R.id.signedin), null);
                } else {
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
        if (main.isSignedIn()) {
            showPlayerName(main, null, account);
        }
    }

    private static void showPlayerName(final Activity_Main main, final TextView view,
                                       final Preference preference) {
        PlayGames.getPlayersClient(main).getCurrentPlayer().addOnSuccessListener(player -> {
            String text = main.getString(R.string.signed_in, player.getDisplayName());
            if (view != null) view.setText(text);
            if (preference != null) preference.setSummary(text);
        });
    }

    public static void onSavedInstance(final Bundle outState, final Activity_Main main) {

    }

}
