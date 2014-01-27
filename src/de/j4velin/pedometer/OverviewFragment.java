package de.j4velin.pedometer;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.echo.holographlibrary.Bar;
import com.echo.holographlibrary.BarGraph;
import com.echo.holographlibrary.PieGraph;
import com.echo.holographlibrary.PieSlice;

import de.j4velin.pedometer.background.SensorListener;
import de.j4velin.pedometer.util.Logger;
import de.j4velin.pedometer.util.Util;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

public class OverviewFragment extends Fragment implements SensorEventListener {

	private TextView stepsView, totalView;
	private PieSlice sliceGoal, sliceCurrent;
	private PieGraph pg;
	private int todayOffset, total_start, goal, since_boot;
	public final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());
	private boolean showSteps = true;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View v = inflater.inflate(R.layout.fragment_overview, null);
		stepsView = (TextView) v.findViewById(R.id.steps);
		totalView = (TextView) v.findViewById(R.id.total);

		pg = (PieGraph) v.findViewById(R.id.graph);

		// slice for the steps taken today
		sliceCurrent = new PieSlice();
		sliceCurrent.setColor(Color.parseColor("#99CC00"));
		sliceCurrent.setValue(0);
		pg.addSlice(sliceCurrent);

		// slice for the "missing" steps until reaching the goal
		sliceGoal = new PieSlice();
		sliceGoal.setColor(Color.parseColor("#990000"));
		sliceGoal.setValue(1);
		pg.addSlice(sliceGoal);

		pg.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View view) {
				showSteps = !showSteps;
				if (showSteps) {
					((TextView) v.findViewById(R.id.unit)).setText("steps");
				} else {
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
					int steps_today = Math.max(todayOffset + since_boot, 0);
					float distance = steps_today * prefs.getFloat("stepsize_value", SettingsFragment.DEFAULT_STEP_SIZE);
					String unit = prefs.getString("stepsize_unit", SettingsFragment.DEFAULT_STEP_UNIT);
					if (unit.equals("cm")) {
						distance /= 100000;
						unit = "km";
					} else {
						distance /= 5280;
						unit = "mi";
					}
					stepsView.setText(formatter.format(distance));
					((TextView) v.findViewById(R.id.unit)).setText(unit);
				}
				updateSteps();
			}
		});

		return v;
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);

		Database db = new Database(getActivity());
		db.open();

		if (todayOffset == 0) {
			if (Logger.LOG)
				db.logState();
			// read todays offset
			todayOffset = db.getSteps(Util.getToday());
		}

		goal = PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("goal", 10000);

		getActivity().bindService(new Intent(getActivity(), SensorListener.class), new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {

			}

			@SuppressLint("HandlerLeak")
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				Messenger messenger = new Messenger(service);
				try {
					final ServiceConnection conn = this;
					Messenger incoming = new Messenger(new Handler() {
						public void handleMessage(Message msg) {
							if (msg != null) {
								if (Logger.LOG)
									Logger.log("SensorListener.steps: " + msg.arg1);
								since_boot = msg.arg1;
								updateSteps();
							}
							getActivity().unbindService(conn);
						}
					});
					Message msg = Message.obtain();
					msg.replyTo = incoming;
					messenger.send(msg);
				} catch (RemoteException e) {
					if (Logger.LOG)
						Logger.log(e);
					e.printStackTrace();
				}
			}
		}, 0);

		// register a sensorlistener to live update the UI if a step is taken
		SensorManager sm = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
		sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_UI);

		// update yesterday & total step count
		Calendar yesterday = Calendar.getInstance();
		yesterday.setTimeInMillis(Util.getToday());
		yesterday.add(Calendar.DAY_OF_YEAR, -1);

		int yesterdaySteps = db.getSteps(yesterday.getTimeInMillis());
		((TextView) getView().findViewById(R.id.yesterday)).setText(formatter
				.format((yesterdaySteps > Integer.MIN_VALUE) ? yesterdaySteps : 0));
		total_start = db.getTotalWithoutToday();
		totalView.setText(String.valueOf(total_start));

		// At the bottom, show some bars for the last 6 days
		BarGraph g = (BarGraph) getView().findViewById(R.id.bargraph);
		ArrayList<Bar> points = new ArrayList<Bar>();
		Bar d;
		SimpleDateFormat df = new SimpleDateFormat("E", Locale.getDefault());
		yesterday.add(Calendar.DAY_OF_YEAR, -6);
		int steps;
		for (int i = 0; i < 7; i++) {
			steps = db.getSteps(yesterday.getTimeInMillis());
			if (steps <= 0) {
				continue;
			}
			d = new Bar();
			if (steps > goal)
				d.setColor(Color.parseColor("#99CC00"));
			else
				d.setColor(Color.parseColor("#0099cc"));
			d.setName(df.format(new Date(yesterday.getTimeInMillis())));
			d.setValue(steps);
			d.setValueString(formatter.format(d.getValue()));
			points.add(d);
			yesterday.add(Calendar.DAY_OF_YEAR, 1);
		}
		db.close();
		if (points.size() > 0) {
			g.setBars(points);
		} else {
			g.setVisibility(View.GONE);
		}

	}

	@Override
	public void onPause() {
		super.onPause();
		try {
			SensorManager sm = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
			sm.unregisterListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		return ((MainActivity) getActivity()).optionsItemSelected(item);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// won't happen
	}

	@Override
	public void onSensorChanged(final SensorEvent event) {
		if (event.values[0] == 0)
			return;
		if (Logger.LOG)
			Logger.log("sensorChanged | todayOffset: " + todayOffset + " since boot: " + event.values[0]);
		if (todayOffset == Integer.MIN_VALUE) {
			// no values for today
			// we dont know when the reboot was, so set todays steps to 0 by
			// initializing them with -STEPS_SINCE_BOOT
			todayOffset = -(int) event.values[0];
		}
		since_boot = (int) event.values[0];
		updateSteps();
	}

	private void updateSteps() {
		if (Logger.LOG)
			Logger.log("update steps: " + since_boot);
		// todayOffset might still be Integer.MIN_VALUE on first start
		int steps_today = Math.max(todayOffset + since_boot, 0);
		sliceCurrent.setValue(steps_today);
		if (goal - steps_today > 0) {
			// goal not reached yet
			if (pg.getSlices().size() == 1) {
				// can happen if the goal value was changed: old goal value was
				// reached but now there are some steps missing for the new goal
				pg.addSlice(sliceGoal);
			}
			sliceGoal.setValue(goal - steps_today);
		} else {
			// goal reached
			pg.removeSlices();
			pg.addSlice(sliceCurrent);
		}
		pg.invalidate();
		if (showSteps) {
			stepsView.setText(formatter.format(steps_today));
		} else if (since_boot % 10 == 0) {
			// update only every 10 steps
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			float distance = steps_today * prefs.getFloat("stepsize_value", SettingsFragment.DEFAULT_STEP_SIZE);
			if (prefs.getString("stepsize_unit", SettingsFragment.DEFAULT_STEP_UNIT).equals("cm")) {
				distance /= 100000;
			} else {
				distance /= 5280;
			}
			stepsView.setText(formatter.format(distance));
		}
		totalView.setText(formatter.format(total_start + steps_today));
	}

}
