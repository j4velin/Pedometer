package de.j4velin.pedometer;

import java.util.Calendar;
import java.util.Date;

import android.app.Dialog;
import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.TextView;
import de.j4velin.pedometer.util.Util;

public class Dialog_Statistics {

	public static Dialog getDialog(final Context c, int since_boot) {
		final Dialog d = new Dialog(c);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		d.setContentView(R.layout.statistics);
		d.findViewById(R.id.close).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				d.dismiss();
			}
		});
		Database db = new Database(c);
		db.open();

		Pair<Date, Integer> record = db.getRecordData();

		Calendar date = Calendar.getInstance();
		date.setTimeInMillis(Util.getToday());
		int daysThisMonth = date.get(Calendar.DAY_OF_MONTH);

		date.add(Calendar.DATE, -6);

		int thisWeek = db.getSteps(date.getTimeInMillis(), System.currentTimeMillis()) + since_boot;

		date.setTimeInMillis(Util.getToday());
		date.set(Calendar.DAY_OF_MONTH, 1);
		int thisMonth = db.getSteps(date.getTimeInMillis(), System.currentTimeMillis()) + since_boot;

		((TextView) d.findViewById(R.id.record)).setText(Fragment_Overview.formatter.format(record.second) + " @ "
				+ java.text.DateFormat.getDateInstance().format(record.first));

		((TextView) d.findViewById(R.id.totalthisweek)).setText(Fragment_Overview.formatter.format(thisWeek));
		((TextView) d.findViewById(R.id.totalthismonth)).setText(Fragment_Overview.formatter.format(thisMonth));

		((TextView) d.findViewById(R.id.averagethisweek)).setText(Fragment_Overview.formatter.format(thisWeek / 7));
		((TextView) d.findViewById(R.id.averagethismonth)).setText(Fragment_Overview.formatter.format(thisMonth / daysThisMonth));
		
		db.close();
		
		return d;
	}

}
