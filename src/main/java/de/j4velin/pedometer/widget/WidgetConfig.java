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

package de.j4velin.pedometer.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import de.j4velin.lib.colorpicker.ColorPickerDialog;
import de.j4velin.lib.colorpicker.ColorPreviewButton;
import de.j4velin.pedometer.R;

public class WidgetConfig extends Activity implements OnClickListener {

    private static int widgetId;

    @Override
    protected void onPause() {
        super.onPause();
        WidgetUpdateService.enqueueUpdate(this);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            setContentView(R.layout.widgetconfig);

            ColorPreviewButton textcolor = (ColorPreviewButton) findViewById(R.id.textcolor);
            textcolor.setOnClickListener(this);
            textcolor.setColor(Color.WHITE);
            ColorPreviewButton bgcolor = (ColorPreviewButton) findViewById(R.id.bgcolor);
            bgcolor.setOnClickListener(this);
            bgcolor.setColor(Color.TRANSPARENT);

            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

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
                (findViewById(v.getId()).getTag() != null) ?
                        (Integer) findViewById(v.getId()).getTag() : -1);
        dialog.setHexValueEnabled(true);
        dialog.setAlphaSliderVisible(true);
        dialog.setOnColorChangedListener(new ColorPickerDialog.OnColorChangedListener() {
            @Override
            public void onColorChanged(int color) {
                ((ColorPreviewButton) v).setColor(color);
                v.setTag(color);
                getSharedPreferences("Widgets", Context.MODE_PRIVATE).edit()
                        .putInt((v.getId() == R.id.bgcolor ? "background_" : "color_") + widgetId,
                                color).apply();
            }
        });
        dialog.show();
    }

}
