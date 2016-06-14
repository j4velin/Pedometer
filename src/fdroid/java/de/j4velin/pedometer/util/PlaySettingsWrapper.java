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

import android.os.Bundle;
import android.preference.Preference;

import de.j4velin.pedometer.ui.Activity_Main;

/**
 * Class to wrap some Google Play related stuff in the SettingsFragment
 */
public class PlaySettingsWrapper {

    public static void setupAccountSetting(final Preference account,
                                           final Bundle savedInstanceState,
                                           final Activity_Main main) {
        account.setSummary("This feature is not available on the F-Droid version of the app");
        account.setEnabled(false);
    }

    public static void onSavedInstance(final Bundle outState, final Activity_Main main) {

    }

}
