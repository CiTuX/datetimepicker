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
import android.content.res.Resources;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.datetimepicker.R;
import com.android.datetimepicker.date.DatePickerDialog.OnDateChangedListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a selectable list of years.
 */
public class YearPickerView extends ListView implements OnItemClickListener, OnDateChangedListener {

    private final DatePickerController mController;
    private YearAdapter mAdapter;
    private int mViewSize;
    private int mChildSize;
    private TextViewWithCircularIndicator mSelectedView;

    /**
     * @param context
     */
    public YearPickerView(Context context, DatePickerController controller) {
        super(context);
        mController = controller;
        mController.registerOnDateChangedListener(this);
        setVerticalFadingEdgeEnabled(true);
        ViewGroup.LayoutParams frame = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        setLayoutParams(frame);
        Resources res = context.getResources();
        mViewSize = res.getDimensionPixelOffset(R.dimen.pager_height);
        mChildSize = res.getDimensionPixelOffset(R.dimen.year_label_height);
        setFadingEdgeLength(mChildSize / 3);
        init(context);
        setOnItemClickListener(this);
        setSelector(new StateListDrawable());
        setDividerHeight(0);
        onDateChanged();
    }

    private void init(Context context) {
        ArrayList<String> years = new ArrayList<String>();
        for (int year = mController.getMinYear(); year <= mController.getMaxYear(); year++) {
            years.add(Integer.valueOf(year).toString());
        }
        mAdapter = new YearAdapter(context, R.layout.year_label_text_view, years);
        setAdapter(mAdapter);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mController.tryVibrate();
        TextViewWithCircularIndicator clickedView = (TextViewWithCircularIndicator) view;
        if (mSelectedView != clickedView) {
            mSelectedView.drawIndicator(false);
            mSelectedView.requestLayout();
            clickedView.drawIndicator(true);
            clickedView.requestLayout();
            mSelectedView = clickedView;
        }
        mController.onYearSelected(getYearFromTextView(clickedView));
        mAdapter.notifyDataSetChanged();
    }

    private int getYearFromTextView(TextView view) {
        return Integer.valueOf(view.getText().toString());
    }

    private class YearAdapter extends ArrayAdapter<String> {

        public YearAdapter(Context context, int resource, List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextViewWithCircularIndicator v = (TextViewWithCircularIndicator)
                    super.getView(position, convertView, parent);
            v.requestLayout();
            int year = getYearFromTextView(v);
            boolean selected = mController.getSelectedDay().year == year;
            v.drawIndicator(selected);
            if (selected) {
                mSelectedView = v;
            }
            return v;
        }
    }

    public void postSetSelection(final int position) {
        post(new Runnable() {

            @Override
            public void run() {
                setSelection(position);
                requestLayout();
            }
        });
    }

    public void postSetSelectionFromTop(final int position) {
        post(new Runnable() {

            @Override
            public void run() {
                setSelectionFromTop(position, mViewSize / 2 - mChildSize / 2);
                requestLayout();
            }
        });
    }

    @Override
    public void onDateChanged() {
        mAdapter.notifyDataSetChanged();
        postSetSelectionFromTop(mController.getSelectedDay().year - mController.getMinYear());
    }
}
