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

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
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

	private static TextView stepsView, totalView;
	private static PieSlice sliceGoal, sliceCurrent;
	private static PieGraph pg;
	private static int todayOffset, total_start, goal, steps_today;
	private static Database db;
	private final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());
	private boolean showSteps = true;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View v = inflater.inflate(R.layout.activity_main, null);
		stepsView = (TextView) v.findViewById(R.id.steps);
		db = new Database(getActivity());
		db.open();
		Calendar yesterday = Calendar.getInstance();
		yesterday.setTimeInMillis(Util.getToday());
		yesterday.add(Calendar.DAY_OF_YEAR, -1);
		totalView = (TextView) v.findViewById(R.id.total);

		int yesterdaySteps = db.getSteps(yesterday.getTimeInMillis());
		((TextView) v.findViewById(R.id.yesterday)).setText(formatter
				.format((yesterdaySteps > Integer.MIN_VALUE) ? yesterdaySteps : 0));
		total_start = db.getTotalWithoutToday();
		totalView.setText(String.valueOf(total_start));

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

		// At the bottom, show some bars for the last 6 days
		ArrayList<Bar> points = new ArrayList<Bar>();
		Bar d;
		SimpleDateFormat df = new SimpleDateFormat("E", Locale.getDefault());
		yesterday.add(Calendar.DAY_OF_YEAR, -6);
		int steps;
		for (int i = 0; i < 7; i++) {
			steps = db.getSteps(yesterday.getTimeInMillis());
			if (steps > Integer.MIN_VALUE) {
				d = new Bar();
				d.setColor(Color.parseColor("#990000"));
				d.setName(df.format(new Date(yesterday.getTimeInMillis())));
				d.setValue(steps);
				d.setValueString(formatter.format(d.getValue()));
				points.add(d);
			}
			yesterday.add(Calendar.DAY_OF_YEAR, 1);
		}
		BarGraph g = (BarGraph) v.findViewById(R.id.bargraph);
		if (points.size() > 0) {
			g.setBars(points);
		} else {
			g.setVisibility(View.GONE);
		}

		stepsView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View view) {
				showSteps = !showSteps;
				if (showSteps) {
					((TextView) v.findViewById(R.id.unit)).setText("steps");
				} else {
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
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
				updateSteps(SensorListener.steps);
			}
		});

		return v;
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
		db.open();
		if (Logger.LOG)
			db.logState();
		// read todays offset
		todayOffset = db.getSteps(Util.getToday());
		goal = PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("goal", 10000);
		if (Logger.LOG)
			Logger.log("SensorListener.steps: " + SensorListener.steps);
		updateSteps(SensorListener.steps);

		// register a sensorlistener to live update the UI if a step is taken
		SensorManager sm = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
		Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
		sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
	}

	@Override
	public void onPause() {
		super.onPause();
		db.close();
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
		if (todayOffset == Integer.MIN_VALUE) { // no values for today
			// we dont know when the reboot was, so set todays steps to 0 by
			// initializing them with -STEPS_SINCE_BOOT
			db.insertDay(Util.getToday(), -(int) event.values[0]);
			todayOffset = -(int) event.values[0];
		}
		updateSteps((int) event.values[0]);
	}

	private void updateSteps(int steps) {
		if (Logger.LOG)
			Logger.log("update steps: " + steps);
		// todayOffset might still be Integer.MIN_VALUE on first start
		steps_today = Math.max(todayOffset + steps, 0);
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
		} else if (steps % 10 == 0) {
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
