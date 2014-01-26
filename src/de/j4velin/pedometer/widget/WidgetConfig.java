package de.j4velin.pedometer.widget;

import de.j4velin.pedometer.R;

import net.margaritov.preference.colorpicker.ColorPickerDialog;
import net.margaritov.preference.colorpicker.ColorPickerDialog.OnColorChangedListener;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class WidgetConfig extends Activity implements OnClickListener {

	private static int widgetId;

	@Override
	protected void onPause() {
		super.onPause();
		save();
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		final Bundle extras = intent.getExtras();
		if (extras != null) {
			setContentView(R.layout.widgetconfig);

			findViewById(R.id.textcolor).setOnClickListener(this);
			findViewById(R.id.bgcolor).setOnClickListener(this);

			widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

			final Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
			setResult(RESULT_OK, resultValue);
		} else {
			finish();
		}
	}

	@Override
	public void onClick(final View v) {
		ColorPickerDialog dialog = new ColorPickerDialog(this,
				(findViewById(v.getId()).getTag() != null) ? (Integer) findViewById(v.getId()).getTag() : -1);
		dialog.setHexValueEnabled(true);
		dialog.setAlphaSliderVisible(true);
		dialog.setOnColorChangedListener(new OnColorChangedListener() {
			@Override
			public void onColorChanged(int color) {
				findViewById(v.getId()).setTag(color);
				v.setBackgroundColor(color);
				v.setTag(color);
			}
		});
		dialog.show();
	}

	private void save() {
		final Editor edit = getSharedPreferences("Widgets", Context.MODE_PRIVATE).edit();
		edit.putInt("text_" + widgetId, (Integer) findViewById(R.id.textcolor).getTag());
		edit.putInt("bg_" + widgetId, (Integer) findViewById(R.id.bgcolor).getTag());
		edit.apply();
	}

}
