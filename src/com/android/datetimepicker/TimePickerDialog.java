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
 * limitations under the License
 */

package com.android.datetimepicker;

import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.style.AlignmentSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.datetimepicker.R;

import com.android.datetimepicker.TimePicker.OnValueSelectedListener;

/**
 * Dialog to set a time.
 */
public class TimePickerDialog extends DialogFragment implements OnValueSelectedListener{
    private static final String TAG = "TimePickerDialog";

    private static final String KEY_HOUR_OF_DAY = "hour_of_day";
    private static final String KEY_MINUTE = "minute";
    private static final String KEY_IS_24_HOUR_VIEW = "is_24_hour_view";
    private static final String KEY_CURRENT_ITEM_SHOWING = "current_item_showing";
    public static final int HOUR_INDEX = 0;
    public static final int MINUTE_INDEX = 1;
    public static final int AMPM_INDEX = 2; // NOT a real index for the purpose of what's showing.
    public static final int AM = 0;
    public static final int PM = 1;

    private Handler mHandler = new Handler();

    private OnTimeSetListener mCallback;

    private TextView mDoneButton;
    private TextView mHourView;
    private TextView mMinuteView;
    private TextView mAmPmTextView;
    private TimePicker mTimePicker;

    private int mBlue;
    private int mBlack;
    private String mAmText;
    private String mPmText;

    private boolean mAllowAutoAdvance;
    private int mInitialHourOfDay;
    private int mInitialMinute;
    private boolean mIs24HourMode;
    private int mWidthPixels;

    /**
     * The callback interface used to indicate the user is done filling in
     * the time (they clicked on the 'Set' button).
     */
    public interface OnTimeSetListener {

        /**
         * @param view The view associated with this listener.
         * @param hourOfDay The hour that was set.
         * @param minute The minute that was set.
         */
        void onTimeSet(TimePicker view, int hourOfDay, int minute);
    }

    public TimePickerDialog() {
        // Empty constructor required for dialog fragment.
    }

    public TimePickerDialog(Context context, int theme, OnTimeSetListener callback,
            int hourOfDay, int minute, boolean is24HourMode) {
        // Empty constructor required for dialog fragment.
    }

    public static TimePickerDialog newInstance(OnTimeSetListener callback,
            int hourOfDay, int minute, boolean is24HourMode) {
        TimePickerDialog ret = new TimePickerDialog();
        ret.initialize(callback, hourOfDay, minute, is24HourMode);
        return ret;
    }

    public void initialize(OnTimeSetListener callback,
            int hourOfDay, int minute, boolean is24HourMode) {
        mCallback = callback;

        mInitialHourOfDay = hourOfDay;
        mInitialMinute = minute;
        mIs24HourMode = is24HourMode;
    }

    public void setOnTimeSetListener(OnTimeSetListener callback) {
        mCallback = callback;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_HOUR_OF_DAY)
                    && savedInstanceState.containsKey(KEY_MINUTE)
                    && savedInstanceState.containsKey(KEY_IS_24_HOUR_VIEW)) {
            mInitialHourOfDay = savedInstanceState.getInt(KEY_HOUR_OF_DAY);
            mInitialMinute = savedInstanceState.getInt(KEY_MINUTE);
            mIs24HourMode = savedInstanceState.getBoolean(KEY_IS_24_HOUR_VIEW);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        View view = inflater.inflate(R.layout.time_picker_dialog, null);
        Resources res = getResources();

        mBlue = res.getColor(R.color.blue);
        mBlack = res.getColor(R.color.black_80);

        mHourView = (TextView) view.findViewById(R.id.hours);
        mMinuteView = (TextView) view.findViewById(R.id.minutes);
        mAmPmTextView = (TextView) view.findViewById(R.id.ampm_label);
        mAmText = res.getString(R.string.am_label);
        mPmText = res.getString(R.string.pm_label);

        mTimePicker = (TimePicker) view.findViewById(R.id.time_picker);
        mTimePicker.setOnValueSelectedListener(this);
        mTimePicker.initialize(getActivity(), mInitialHourOfDay, mInitialMinute, mIs24HourMode);
        int currentItemShowing = HOUR_INDEX;
        if (savedInstanceState != null &&
                savedInstanceState.containsKey(KEY_CURRENT_ITEM_SHOWING)) {
            currentItemShowing = savedInstanceState.getInt(KEY_CURRENT_ITEM_SHOWING);
        }
        setCurrentItemShowing(currentItemShowing, false);
        mTimePicker.invalidate();

        mHourView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setCurrentItemShowing(HOUR_INDEX, true);
                mTimePicker.tryVibrate();
            }
        });
        mMinuteView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setCurrentItemShowing(MINUTE_INDEX, true);
                mTimePicker.tryVibrate();
            }
        });

        mDoneButton = (TextView) view.findViewById(R.id.done_button);
        mDoneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTimePicker.tryVibrate();
                if (mCallback != null) {
                    mCallback.onTimeSet(mTimePicker,
                            mTimePicker.getHours(), mTimePicker.getMinutes());
                }
                dismiss();
            }
        });

        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mWidthPixels = metrics.widthPixels;

        if (mIs24HourMode) {
            mAmPmTextView.setVisibility(View.GONE);

            RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            paramsSeparator.addRule(RelativeLayout.CENTER_IN_PARENT);
            TextView separatorView = (TextView) view.findViewById(R.id.separator);
            separatorView.setLayoutParams(paramsSeparator);
        } else {
            mAmPmTextView.setVisibility(View.VISIBLE);
            updateAmPmDisplay(mInitialHourOfDay < 12? AM : PM);
            View amPmHitspace = view.findViewById(R.id.ampm_hitspace);
            amPmHitspace.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mTimePicker.tryVibrate();
                    int amOrPm = mTimePicker.getIsCurrentlyAmOrPm();
                    if (amOrPm == AM) {
                        amOrPm = PM;
                    } else if (amOrPm == PM){
                        amOrPm = AM;
                    }
                    updateAmPmDisplay(amOrPm);
                    mTimePicker.setAmOrPm(amOrPm);
                }
            });
        }

        mAllowAutoAdvance = true;
        setHour(mInitialHourOfDay);
        setMinute(mInitialMinute);

        return view;
    }

    private void updateAmPmDisplay(int amOrPm) {
        if (amOrPm == AM) {
            mAmPmTextView.setText(mAmText);
        } else if (amOrPm == PM){
            mAmPmTextView.setText(mPmText);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mTimePicker != null) {
            outState.putInt(KEY_HOUR_OF_DAY, mTimePicker.getHours());
            outState.putInt(KEY_MINUTE, mTimePicker.getMinutes());
            outState.putBoolean(KEY_IS_24_HOUR_VIEW, mIs24HourMode);
            outState.putInt(KEY_CURRENT_ITEM_SHOWING, mTimePicker.getCurrentItemShowing());
        }
    }

    @Override
    public void onValueSelected(int pickerIndex, int newValue, boolean autoAdvance) {
        if (pickerIndex == HOUR_INDEX) {
            setHour(newValue);
            if (mAllowAutoAdvance && autoAdvance) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setCurrentItemShowing(MINUTE_INDEX, true);
                    }
                }, 150);
            }
        } else if (pickerIndex == MINUTE_INDEX){
            setMinute(newValue);
        } else if (pickerIndex == AMPM_INDEX) {
            updateAmPmDisplay(newValue);
        }
    }

    private void setHour(int value) {
        String format;
        if (mIs24HourMode) {
            format = "%02d";
        } else {
            format = "%d";
            value = value % 12;
            if (value == 0) {
                value = 12;
            }
        }

        mHourView.setText(String.format(format, value));
    }

    private void setMinute(int value) {
        if (value == 60) {
            value = 0;
        }
        mMinuteView.setText(String.format("%02d", value));
    }

    private void setCurrentItemShowing(int index, boolean animate) {
/*
        if (mAllowAutoAdvance && index == 1) {
            // Once we've seen the minutes, no need to auto-advance.
            mAllowAutoAdvance = false;
        }
*/
        mTimePicker.setCurrentItemShowing(index, animate);
        int hourColor = (index == HOUR_INDEX)? mBlue : mBlack;
        int minuteColor = (index == MINUTE_INDEX)? mBlue : mBlack;
        mHourView.setTextColor(hourColor);
        mMinuteView.setTextColor(minuteColor);
    }
}
