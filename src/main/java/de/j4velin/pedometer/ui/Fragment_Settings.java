/*
 * Copyright 2014 Thomas Hoffmann
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.RadioGroup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import de.j4velin.pedometer.Database;
import de.j4velin.pedometer.R;
import de.j4velin.pedometer.SensorListener;
import de.j4velin.pedometer.util.API26Wrapper;
import de.j4velin.pedometer.util.PlaySettingsWrapper;
import de.j4velin.pedometer.util.Util;

public class Fragment_Settings extends PreferenceFragment implements OnPreferenceClickListener {

    final static int DEFAULT_GOAL = 10000;
    final static float DEFAULT_STEP_SIZE = Locale.getDefault() == Locale.US ? 2.5f : 75f;
    final static String DEFAULT_STEP_UNIT = Locale.getDefault() == Locale.US ? "ft" : "cm";

    private final static int REQUEST_EXPORT = 1;
    private final static int REQUEST_IMPORT = 2;
    private final static String CSV_MIME_TYPE = "text/csv";

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        final SharedPreferences prefs =
                getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);

        findPreference("import").setOnPreferenceClickListener(this);
        findPreference("export").setOnPreferenceClickListener(this);
        if (Build.VERSION.SDK_INT >= 26) {
            findPreference("notification").setOnPreferenceClickListener(this);
        } else {
            findPreference("notification")
                    .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(final Preference preference,
                                                          final Object newValue) {
                            prefs.edit().putBoolean("notification", (Boolean) newValue).apply();

                            NotificationManager manager = (NotificationManager) getActivity()
                                    .getSystemService(Context.NOTIFICATION_SERVICE);
                            if ((Boolean) newValue) {
                                manager.notify(SensorListener.NOTIFICATION_ID,
                                        SensorListener.getNotification(getActivity()));
                            } else {
                                manager.cancel(SensorListener.NOTIFICATION_ID);
                            }

                            return true;
                        }
                    });
        }

        Preference account = findPreference("account");
        PlaySettingsWrapper
                .setupAccountSetting(account, savedInstanceState, (Activity_Main) getActivity());

        Preference goal = findPreference("goal");
        goal.setOnPreferenceClickListener(this);
        goal.setSummary(getString(R.string.goal_summary, prefs.getInt("goal", DEFAULT_GOAL)));

        Preference stepsize = findPreference("stepsize");
        stepsize.setOnPreferenceClickListener(this);
        stepsize.setSummary(getString(R.string.step_size_summary,
                prefs.getFloat("stepsize_value", DEFAULT_STEP_SIZE),
                prefs.getString("stepsize_unit", DEFAULT_STEP_UNIT)));

        setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        PlaySettingsWrapper.onSavedInstance(outState, (Activity_Main) getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        if (Build.VERSION.SDK_INT >= 26) { // notification settings might have changed
            SensorListener.start(getActivity());
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.action_settings).setVisible(false);
        menu.findItem(R.id.action_split_count).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return ((Activity_Main) getActivity()).optionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        AlertDialog.Builder builder;
        View v;
        final SharedPreferences prefs =
                getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);
        final String key = preference.getKey();
        if ("goal".equals(key)) {
            builder = new AlertDialog.Builder(getActivity());
            final NumberPicker np = new NumberPicker(getActivity());
            np.setMinValue(1);
            np.setMaxValue(100000);
            np.setValue(prefs.getInt("goal", 10000));
            builder.setView(np);
            builder.setTitle(R.string.set_goal);
            builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    np.clearFocus();
                    prefs.edit().putInt("goal", np.getValue()).commit();
                    preference.setSummary(getString(R.string.goal_summary, np.getValue()));
                    dialog.dismiss();
                    SensorListener.start(getActivity());
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            Dialog dialog = builder.create();
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.show();
        } else if ("stepsize".equals(key)) {
            builder = new AlertDialog.Builder(getActivity());
            v = getActivity().getLayoutInflater().inflate(R.layout.stepsize, null);
            final RadioGroup unit = (RadioGroup) v.findViewById(R.id.unit);
            final EditText value = (EditText) v.findViewById(R.id.value);
            unit.check(
                    prefs.getString("stepsize_unit", DEFAULT_STEP_UNIT).equals("cm") ? R.id.cm :
                            R.id.ft);
            value.setText(String.valueOf(prefs.getFloat("stepsize_value", DEFAULT_STEP_SIZE)));
            builder.setView(v);
            builder.setTitle(R.string.set_step_size);
            builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        prefs.edit().putFloat("stepsize_value",
                                Float.valueOf(value.getText().toString()))
                                .putString("stepsize_unit",
                                        unit.getCheckedRadioButtonId() == R.id.cm ? "cm" : "ft")
                                .apply();
                        preference.setSummary(getString(R.string.step_size_summary,
                                Float.valueOf(value.getText().toString()),
                                unit.getCheckedRadioButtonId() == R.id.cm ? "cm" : "ft"));
                    } catch (NumberFormatException nfe) {
                        nfe.printStackTrace();
                    }
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
        } else if ("export".equals(key)) {
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType(CSV_MIME_TYPE)
                    .putExtra(Intent.EXTRA_TITLE, "Pedometer.csv"), REQUEST_EXPORT);
        } else if ("import".equals(key)) {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{CSV_MIME_TYPE, "text/comma-separated-values",
                                    "text/plain", "application/octet-stream"}), REQUEST_IMPORT);
        } else if ("notification".equals(key)) {
            API26Wrapper.launchNotificationSettings(getActivity());
        }
        return false;
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            super.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == REQUEST_EXPORT) {
            exportCsv(data.getData());
        } else if (requestCode == REQUEST_IMPORT) {
            importCsv(data.getData());
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void showMessage(final String message) {
        new AlertDialog.Builder(getActivity()).setMessage(message)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create().show();
    }

    /**
     * Writes a CSV file containing data about past days and the steps taken on them
     *
     * @param uri the document picked by the user
     */
    private void exportCsv(final Uri uri) {
        Database db = Database.getInstance(getActivity());
        // today's entry is not a step count yet, but the negative sensor value at the start of
        // the day - exporting it (as 0) would reset today's steps when importing the file again
        Cursor c = db.query(new String[]{"date", "steps"}, "date > 0 AND date < ?",
                new String[]{String.valueOf(Util.getToday())}, null, null, "date", null);
        try (OutputStream os = getActivity().getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) throw new IOException(uri.toString());
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            if (c != null && c.moveToFirst()) {
                while (!c.isAfterLast()) {
                    out.append(c.getString(0)).append(";")
                            .append(String.valueOf(Math.max(0, c.getInt(1)))).append("\n");
                    c.moveToNext();
                }
            }
            out.flush();
        } catch (IOException | SecurityException e) {
            showMessage(getString(R.string.error_file, e.getMessage()));
            e.printStackTrace();
            return;
        } finally {
            if (c != null) c.close();
            db.close();
        }
        showMessage(getString(R.string.data_saved, getDisplayName(uri)));
    }

    /**
     * Imports previously exported data from a csv file.
     * Overwrites days for which there is already an entry in the database
     *
     * @param uri the document picked by the user
     */
    private void importCsv(final Uri uri) {
        Database db = Database.getInstance(getActivity());
        String line;
        String[] data;
        int ignored = 0, inserted = 0, overwritten = 0;
        final long today = Util.getToday();
        try (InputStream is = getActivity().getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException(uri.toString());
            BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            while ((line = in.readLine()) != null) {
                data = line.split(";");
                try {
                    long date = Long.parseLong(data[0]);
                    // files exported by older versions contain today's entry (as 0 steps),
                    // which would overwrite today's offset and thereby the steps taken so far
                    if (date >= today) continue;
                    if (db.insertDayFromBackup(date, Integer.valueOf(data[1]))) {
                        inserted++;
                    } else {
                        overwritten++;
                    }
                } catch (Exception nfe) {
                    ignored++;
                }
            }
        } catch (IOException | SecurityException e) {
            showMessage(getString(R.string.file_cant_read, getDisplayName(uri)));
            e.printStackTrace();
            return;
        } finally {
            db.close();
        }
        String message = getString(R.string.entries_imported, inserted + overwritten);
        if (overwritten > 0)
            message += "\n\n" + getString(R.string.entries_overwritten, overwritten);
        if (ignored > 0) message += "\n\n" + getString(R.string.entries_ignored, ignored);
        showMessage(message);
    }

    private String getDisplayName(final Uri uri) {
        try (Cursor c = getActivity().getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri.getLastPathSegment();
    }
}
