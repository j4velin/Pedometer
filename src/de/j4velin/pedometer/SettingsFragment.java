package de.j4velin.pedometer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

import de.j4velin.pedometer.background.SensorListener;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceClickListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.TextView;

public class SettingsFragment extends PreferenceFragment implements OnPreferenceClickListener {

	final static int DEFAULT_GOAL = 10000;
	final static float DEFAULT_STEP_SIZE = Locale.getDefault() == Locale.US ? 2.5f : 75f;
	final static String DEFAULT_STEP_UNIT = Locale.getDefault() == Locale.US ? "ft" : "cm";

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
		findPreference("about").setOnPreferenceClickListener(this);
		findPreference("import").setOnPreferenceClickListener(this);
		findPreference("export").setOnPreferenceClickListener(this);

		findPreference("notification").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				getActivity().getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS).edit()
						.putBoolean("notification", (Boolean) newValue).commit();

				getActivity().startService(
						new Intent(getActivity(), SensorListener.class).putExtra("updateNotificationState", true));
				return true;
			}
		});

		Preference account = findPreference("account");
		account.setOnPreferenceClickListener(this);
		// If created for the first time, the GameClient should be setup and be
		// connected, but when recreating the fragment (due to orientation
		// change for example), then the fragment's onCreate is called before
		// the new GamesClient is setup. In this case, just use the player name
		// saved in the savedInstanceState bundle
		if ((savedInstanceState == null && ((MainActivity) getActivity()).getGC().isConnected())
				|| (savedInstanceState != null && savedInstanceState.containsKey("player"))) {
			account.setSummary(getString(R.string.signed_in, savedInstanceState == null ? ((MainActivity) getActivity()).getGC()
					.getCurrentPlayer().getDisplayName() : savedInstanceState.getString("player")));
		}

		final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

		Preference goal = findPreference("goal");
		goal.setOnPreferenceClickListener(this);
		goal.setSummary(getString(R.string.goal_summary, prefs.getInt("goal", DEFAULT_GOAL)));

		Preference stepsize = findPreference("stepsize");
		stepsize.setOnPreferenceClickListener(this);
		stepsize.setSummary(getString(R.string.step_size_summary, prefs.getFloat("stepsize_value", DEFAULT_STEP_SIZE),
				prefs.getString("stepsize_unit", DEFAULT_STEP_UNIT)));

		setHasOptionsMenu(true);		
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		try {
			if (((MainActivity) getActivity()).getGC().isConnected())
				outState.putString("player", ((MainActivity) getActivity()).getGC().getCurrentPlayer().getDisplayName());
			else
				outState.remove("player");
		} catch (Exception e) {
			if (Logger.LOG)
				Logger.log(e);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.action_settings).setVisible(false);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		return ((MainActivity) getActivity()).optionsItemSelected(item);
	}

	@Override
	public boolean onPreferenceClick(final Preference preference) {
		AlertDialog.Builder builder;
		View v;
		final SharedPreferences prefs;
		switch (preference.getTitleRes()) {
		case R.string.goal:
			builder = new AlertDialog.Builder(getActivity());
			final NumberPicker np = new NumberPicker(getActivity());
			prefs = getPreferenceManager().getSharedPreferences();
			np.setMinValue(1);
			np.setMaxValue(100000);
			np.setValue(prefs.getInt("goal", 10000));
			builder.setView(np);
			builder.setTitle("Set goal");
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					np.clearFocus();
					prefs.edit().putInt("goal", np.getValue()).apply();
					preference.setSummary(getString(R.string.goal_summary, np.getValue()));
					dialog.dismiss();
					getActivity().startService(
							new Intent(getActivity(), SensorListener.class).putExtra("updateNotificationState", true));
				}
			});
			builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			Dialog dialog = builder.create();
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
			dialog.show();
			break;
		case R.string.step_size:
			builder = new AlertDialog.Builder(getActivity());
			prefs = getPreferenceManager().getSharedPreferences();
			v = getActivity().getLayoutInflater().inflate(R.layout.stepsize, null);
			final RadioGroup unit = (RadioGroup) v.findViewById(R.id.unit);
			final EditText value = (EditText) v.findViewById(R.id.value);
			unit.check(prefs.getString("stepsize_unit", DEFAULT_STEP_UNIT).equals("cm") ? R.id.cm : R.id.ft);
			value.setText(String.valueOf(prefs.getFloat("stepsize_value", DEFAULT_STEP_SIZE)));
			builder.setView(v);
			builder.setTitle("Set step size");
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					prefs.edit().putFloat("stepsize_value", Float.valueOf(value.getText().toString()))
							.putString("stepsize_unit", unit.getCheckedRadioButtonId() == R.id.cm ? "cm" : "ft").apply();
					preference.setSummary(getString(R.string.step_size_summary, Float.valueOf(value.getText().toString()),
							unit.getCheckedRadioButtonId() == R.id.cm ? "cm" : "ft"));
					dialog.dismiss();
				}
			});
			builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			builder.create().show();
			break;
		case R.string.about:
			builder = new AlertDialog.Builder(getActivity());
			builder.setTitle("About");
			try {
				builder.setMessage("This app was created by Thomas Hoffmann (www.j4velin-development.de) and uses the 'HoloGraphLibrary' by Daniel Nadeau\n\nApp version: "
						+ getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName);
			} catch (NameNotFoundException e1) {
				// should not happen as the app is definitely installed when
				// seeing the dialog
				e1.printStackTrace();
			}
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			builder.create().show();
			break;
		case R.string.account:
			builder = new AlertDialog.Builder(getActivity());
			v = getActivity().getLayoutInflater().inflate(R.layout.signin, null);
			builder.setView(v);
			builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			if (((MainActivity) getActivity()).getGC().isConnected()) {
				((TextView) v.findViewById(R.id.signedin)).append(((MainActivity) getActivity()).getGC().getCurrentPlayer()
						.getDisplayName());
				v.findViewById(R.id.sign_in_button).setVisibility(View.GONE);
				builder.setPositiveButton("Sign out", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						((MainActivity) getActivity()).getGC().signOut();
						preference.setSummary(getString(R.string.sign_in));
						dialog.dismiss();
					}
				});
			}
			final Dialog d = builder.create();

			if (!((MainActivity) getActivity()).getGC().isConnected()) {
				v.findViewById(R.id.signedin).setVisibility(View.GONE);
				v.findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(final View v) {
						// start the asynchronous sign in flow
						((MainActivity) getActivity()).beginSignIn();
						d.dismiss();
					}
				});
			}
			d.show();
			break;
		case R.string.import_title:
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				File f = new File(getActivity().getExternalFilesDir(null), "Pedometer.csv");
				if (!f.exists() || !f.canRead()) {
					new AlertDialog.Builder(getActivity()).setMessage("Error: " + f.getAbsolutePath() + " can not be read")
							.create().show();
					break;
				}
				Database db = new Database(getActivity());
				db.open();
				String line;
				String[] data;
				int skips = 0;
				BufferedReader in;
				try {
					in = new BufferedReader(new FileReader(f));
					while ((line = in.readLine()) != null) {
						data = line.split(";");
						try {
							db.insertSteps(Long.valueOf(data[0]), Integer.valueOf(data[1]));
						} catch (NumberFormatException nfe) {
							skips++;
						}
					}
					in.close();
				} catch (IOException e) {
					new AlertDialog.Builder(getActivity()).setMessage("Error reading file: " + e.getMessage()).create().show();
					e.printStackTrace();
					break;
				} finally {
					db.close();
				}
				new AlertDialog.Builder(getActivity())
						.setMessage(
								skips > 0 ? skips + " entries were ignored as they did not contain valid data"
										: "Data successfully imported").create().show();
			} else {
				new AlertDialog.Builder(getActivity()).setMessage("Error: External storage not available").create().show();
			}
			break;
		case R.string.export_title:
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				BufferedWriter out = null;
				File f = new File(getActivity().getExternalFilesDir(null), "Pedometer.csv");
				try {
					f.createNewFile();
					out = new BufferedWriter(new FileWriter(f));
				} catch (IOException e) {
					new AlertDialog.Builder(getActivity()).setMessage("Error creating file: " + e.getMessage()).create().show();
					e.printStackTrace();
					break;
				}
				Database db = new Database(getActivity());
				db.open();
				Cursor c = db.query(new String[] { "date", "steps" }, null, null, null, null, "date", null);
				try {
					if (c != null && c.moveToFirst()) {
						while (!c.isAfterLast()) {
							out.append(c.getString(0) + ";" + c.getString(1) + "\n");
							c.moveToNext();
						}
					}
					out.close();
				} catch (IOException e) {
					new AlertDialog.Builder(getActivity()).setMessage("Error writing to file: " + e.getMessage()).create().show();
					e.printStackTrace();
					break;
				} finally {
					if (c != null)
						c.close();
					db.close();
				}
				new AlertDialog.Builder(getActivity()).setMessage("Data saved in " + f.getAbsolutePath()).create().show();
			} else {
				new AlertDialog.Builder(getActivity()).setMessage("Error: External storage not available").create().show();
			}
			break;
		}
		return false;
	}

}
