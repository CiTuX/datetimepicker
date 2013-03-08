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
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;

/**
 * A number picker allowing a user to choose a specific year.
 */
public class YearPickerView extends FrameLayout implements OnValueChangeListener {

    private final NumberPicker mPicker;
    private final DatePickerController mController;

    public YearPickerView(Context context, DatePickerController controller) {
        super(context);
        mController = controller;
        ViewGroup.LayoutParams frame = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        setLayoutParams(frame);
        mPicker = new NumberPicker(context);
        LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        mPicker.setLayoutParams(params);
        mPicker.setOnLongPressUpdateInterval(100);
        mPicker.setMinValue(controller.getMinYear());
        mPicker.setMaxValue(controller.getMaxYear());
        mPicker.setWrapSelectorWheel(false);
        mPicker.setValue(controller.getSelectedDay().year);
        mPicker.setOnValueChangedListener(this);
        addView(mPicker);
    }

    public void setValue(int value) {
        mPicker.setValue(value);
    }

    public void onChange() {
        mPicker.setMinValue(mController.getMinYear());
        mPicker.setMaxValue(mController.getMaxYear());
        requestLayout();
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        mController.onYearPickerSelectionChanged(newVal);
    }
}
