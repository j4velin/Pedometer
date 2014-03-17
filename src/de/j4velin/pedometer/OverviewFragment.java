package de.j4velin.pedometer;

import java.text.DateFormat;
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
import android.app.Dialog;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
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
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class OverviewFragment extends Fragment implements SensorEventListener {

	private TextView stepsView, totalView, averageView, partialDateView, partialStepsView;
	private Button partialButton;
	private PieSlice sliceGoal, sliceCurrent;
	private PieGraph pg;
	private int todayOffset, total_start, goal, since_boot, total_days, partial_step_ini;
	public final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());
	public final static DateFormat dateFortmatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,Locale.getDefault());
	private boolean showSteps = true, partialStarted = false;

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
		averageView = (TextView) v.findViewById(R.id.average);
		partialDateView = (TextView) v.findViewById(R.id.date_partial_counter);
		partialStepsView = (TextView) v.findViewById(R.id.steps_partial_counter);
		partialButton = (Button) v.findViewById(R.id.button_partial_counter);

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
				stepsDistanceChanged();
			}
		});
		partialStarted = getActivity().getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS)
				.getBoolean("partial_active", false);
		partial_step_ini = getActivity().getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS)
				.getInt("partial_stepsOffset", 0);
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(getActivity().getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS)
				.getLong("partial_start_date", 0));
		if (partialStarted){
			partialButton.setText(R.string.button_stop_partial);
			partialDateView.setText(dateFortmatter.format(cal));
		} else {
			partialButton.setText(R.string.button_start_partial);
		}

		partialButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				SharedPreferences pref = getActivity().getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS);
				Editor prefEditor = pref.edit();
				Calendar cal = Calendar.getInstance();
				if (partialStarted){//stop
					partialButton.setText(R.string.button_start_partial);
					partialDateView.setText("");
				} else {//start
					partialButton.setText(R.string.button_stop_partial);
					partialDateView.setText(dateFortmatter.format(cal));
					prefEditor.putLong("partial_start_date", cal.getTimeInMillis());
					partial_step_ini = Math.max(total_start + todayOffset + since_boot, 0);
					prefEditor.putInt("partial_stepsOffset", partial_step_ini);
				}
				partialStarted = !partialStarted;
				prefEditor.putBoolean("partial_active", partialStarted).commit();
				stepsDistanceChanged();
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

		if (Logger.LOG)
			db.logState();
		// read todays offset
		todayOffset = db.getSteps(Util.getToday());

		goal = getActivity().getSharedPreferences("pedometer", Context.MODE_MULTI_PROCESS).getInt(
				"goal", 10000);

		getActivity().bindService(new Intent(getActivity(), SensorListener.class),
				new ServiceConnection() {
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
							if (Logger.LOG)
								Logger.log("SensorListener.steps: " + msg.arg1);
							since_boot = msg.arg1;
							updatePie();
							if (getActivity() != null)
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
		sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
				SensorManager.SENSOR_DELAY_UI);

		total_start = db.getTotalWithoutToday();
		total_days = db.getDays();

		db.close();

		stepsDistanceChanged();
	}

	/**
	 * Call this method if the Fragment should update the "steps"/"km" text in
	 * the pie graph as well as the pie and the bars graphs.
	 */
	private void stepsDistanceChanged() {
		if (showSteps) {
			((TextView) getView().findViewById(R.id.unit)).setText(getString(R.string.steps));
		} else {
			String unit = getActivity().getSharedPreferences("pedometer",
					Context.MODE_MULTI_PROCESS).getString("stepsize_unit",
							SettingsFragment.DEFAULT_STEP_UNIT);
			if (unit.equals("cm")) {
				unit = "km";
			} else {
				unit = "mi";
			}
			((TextView) getView().findViewById(R.id.unit)).setText(unit);
		}

		updatePie();
		updateBars();
	}

	@Override
	public void onPause() {
		super.onPause();
		try {
			SensorManager sm = (SensorManager) getActivity().getSystemService(
					Context.SENSOR_SERVICE);
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
			Logger.log("sensorChanged | todayOffset: " + todayOffset + " since boot: "
					+ event.values[0]);
		if (todayOffset == Integer.MIN_VALUE) {
			// no values for today
			// we dont know when the reboot was, so set todays steps to 0 by
			// initializing them with -STEPS_SINCE_BOOT
			todayOffset = -(int) event.values[0];
		}
		since_boot = (int) event.values[0];
		updatePie();
	}

	/**
	 * Updates the pie graph to show todays steps/distance as well as the
	 * yesterday and total values. Should be called when switching from step
	 * count to distance.
	 */
	private void updatePie() {
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
			totalView.setText(formatter.format(total_start + steps_today));
			averageView.setText(formatter.format((total_start + steps_today) / total_days));
			partialStepsView.setText(partialStarted?formatter.format(total_start + steps_today - partial_step_ini):"");
		} else {
			// update only every 10 steps when displaying distance
			SharedPreferences prefs = getActivity().getSharedPreferences("pedometer",
					Context.MODE_MULTI_PROCESS);
			float stepsize = prefs.getFloat("stepsize_value", SettingsFragment.DEFAULT_STEP_SIZE);
			float distance_today = steps_today * stepsize;
			float distance_total = (total_start + steps_today) * stepsize;
			float distance_partial = (total_start + steps_today - partial_step_ini) * stepsize;
			if (prefs.getString("stepsize_unit", SettingsFragment.DEFAULT_STEP_UNIT).equals("cm")) {
				distance_today /= 100000;
				distance_total /= 100000;
				distance_partial /= 100000;
			} else {
				distance_today /= 5280;
				distance_total /= 5280;
				distance_partial /= 5280;
			}
			stepsView.setText(formatter.format(distance_today));
			totalView.setText(formatter.format(distance_total));
			averageView.setText(formatter.format(distance_total / total_days));
			partialStepsView.setText(partialStarted?formatter.format(distance_partial):"");
		}
	}

	/**
	 * Updates the bar graph to show the steps/distance of the last week. Should
	 * be called when switching from step count to distance.
	 */
	private void updateBars() {
		Database db = new Database(getActivity());
		db.open();
		Calendar yesterday = Calendar.getInstance();
		yesterday.setTimeInMillis(Util.getToday());
		yesterday.add(Calendar.DAY_OF_YEAR, -1);
		BarGraph g = (BarGraph) getView().findViewById(R.id.bargraph);
		ArrayList<Bar> points = new ArrayList<Bar>();
		Bar d;
		SimpleDateFormat df = new SimpleDateFormat("E", Locale.getDefault());
		yesterday.add(Calendar.DAY_OF_YEAR, -6);
		int steps;
		float distance, stepsize = SettingsFragment.DEFAULT_STEP_SIZE;
		boolean stepsize_cm = true;
		if (!showSteps) {
			// load some more settings if distance is needed
			SharedPreferences prefs = getActivity().getSharedPreferences("pedometer",
					Context.MODE_MULTI_PROCESS);
			stepsize = prefs.getFloat("stepsize_value", SettingsFragment.DEFAULT_STEP_SIZE);
			stepsize_cm = prefs.getString("stepsize_unit", SettingsFragment.DEFAULT_STEP_UNIT)
					.equals("cm");
		}
		for (int i = 0; i < 7; i++) {
			steps = db.getSteps(yesterday.getTimeInMillis());
			if (steps > 0) {
				d = new Bar();
				if (steps > goal)
					d.setColor(Color.parseColor("#99CC00"));
				else
					d.setColor(Color.parseColor("#0099cc"));
				d.setName(df.format(new Date(yesterday.getTimeInMillis())));
				// steps or distance?
				if (showSteps) {
					d.setValue(steps);
					d.setValueString(formatter.format(d.getValue()));
				} else {
					distance = steps * stepsize;
					if (stepsize_cm) {
						distance /= 100000;
					} else {
						distance /= 5280;
					}
					d.setValue(distance);
					d.setValueString(formatter.format(distance));
				}
				points.add(d);
			}
			yesterday.add(Calendar.DAY_OF_YEAR, 1);
		}
		if (points.size() > 0) {
			g.setBars(points);
			g.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					final Dialog d = new Dialog(getActivity());
					d.requestWindowFeature(Window.FEATURE_NO_TITLE);
					d.setContentView(R.layout.statistics);
					d.findViewById(R.id.close).setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							d.dismiss();
						}
					});
					Database db = new Database(getActivity());
					db.open();

					Pair<Date, Integer> record = db.getRecordData();

					Calendar date = Calendar.getInstance();
					date.setTimeInMillis(Util.getToday());
					int daysThisMonth = date.get(Calendar.DAY_OF_MONTH);

					date.add(Calendar.DATE, -6);

					int thisWeek = db.getSteps(date.getTimeInMillis(), System.currentTimeMillis())
							+ since_boot;

					date.setTimeInMillis(Util.getToday());
					date.set(Calendar.DAY_OF_MONTH, 1);
					int thisMonth = db.getSteps(date.getTimeInMillis(), System.currentTimeMillis())
							+ since_boot;

					((TextView) d.findViewById(R.id.record)).setText(formatter
							.format(record.second)
							+ " @ "
							+ java.text.DateFormat.getDateInstance().format(record.first));

					((TextView) d.findViewById(R.id.totalthisweek)).setText(formatter
							.format(thisWeek));
					((TextView) d.findViewById(R.id.totalthismonth)).setText(formatter
							.format(thisMonth));

					((TextView) d.findViewById(R.id.averagethisweek)).setText(formatter
							.format(thisWeek / 7));
					((TextView) d.findViewById(R.id.averagethismonth)).setText(formatter
							.format(thisMonth / daysThisMonth));

					db.close();
					d.show();
				}
			});
		} else {
			g.setVisibility(View.GONE);
		}
		db.close();
	}

}
