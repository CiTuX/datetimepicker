/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.datetimepicker.date;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.SimpleAdapter;

import com.android.datetimepicker.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Displays a selectable list of months in a grid format.
 */
public class MonthPickerView extends GridView implements OnItemClickListener {

    private static final int NUM_COLUMNS = 3;
    private static final int NUM_MONTHS = 12;

    private final Calendar mCalendar = Calendar.getInstance();
    private final DatePickerController mController;

    /**
     * @param context
     */
    public MonthPickerView(Context context, DatePickerController controller) {
        super(context);
        ViewGroup.LayoutParams frame = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        setLayoutParams(frame);
        setNumColumns(NUM_COLUMNS);
        init(context);
        mController = controller;
        setOnItemClickListener(this);
        setSelector(new StateListDrawable());
    }

    private void init(Context context) {
        ArrayList<String> months = new ArrayList<String>();
        mCalendar.set(Calendar.DAY_OF_MONTH, 1);
        for (int i = 0; i < NUM_MONTHS; i++) {
            mCalendar.set(Calendar.MONTH, i);
            months.add(mCalendar.getDisplayName(Calendar.MONTH, Calendar.SHORT,
                    Locale.getDefault()).toUpperCase(Locale.getDefault()));
        }
        setAdapter(new ArrayAdapter<String>(context, R.layout.month_text_view, months));
    }

    public class MonthPickerAdapter extends SimpleAdapter {

        /**
         * @param context
         * @param data
         * @param resource
         * @param from
         * @param to
         */
        public MonthPickerAdapter(Context context,
                List<? extends Map<String, ?>> data,
                int resource, String[] from, int[] to) {
            super(context, data, resource, from, to);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mController.onMonthPickerSelectionChanged(position);
    }
}
