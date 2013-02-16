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

package com.android.datetimepicker;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Service;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;

import com.android.datetimepicker.R;

public class TimePicker extends FrameLayout implements OnTouchListener {
    private static final String TAG = "TimePicker";

    private final int TOUCH_SLOP;
    private final int TAP_TIMEOUT;
    private final int PRESSED_STATE_DURATION;
    private static final int HOUR_VALUE_TO_DEGREES_STEP_SIZE = 30;
    private static final int MINUTE_VALUE_TO_DEGREES_STEP_SIZE = 6;
    private static final int HOUR_INDEX = TimePickerDialog.HOUR_INDEX;
    private static final int MINUTE_INDEX = TimePickerDialog.MINUTE_INDEX;
    private static final int AMPM_INDEX = TimePickerDialog.AMPM_INDEX;
    private static final int AM = TimePickerDialog.AM;
    private static final int PM = TimePickerDialog.PM;

    private Vibrator mVibrator;
    private long mLastVibrate;
    private int mLastValueSelected;

    private OnValueSelectedListener mListener;
    private boolean mTimeInitialized;
    private int mCurrentHoursOfDay;
    private int mCurrentMinutes;
    private boolean mIs24HourMode;
    private int mCurrentItemShowing;

    private CircleView mCircleView;
    private AmPmCirclesView mAmPmCirclesView;
    private RadialTextsView mHourRadialTextsView;
    private RadialTextsView mMinuteRadialTextsView;
    private RadialSelectorView mHourRadialSelectorView;
    private RadialSelectorView mMinuteRadialSelectorView;

    private int mIsTouchingAmOrPm = -1;
    private boolean mDoingMove;
    private int mDownDegrees;
    private float mDownX;
    private float mDownY;

    private ReselectSelectorRunnable mReselectSelectorRunnable;

    private Handler mHandler = new Handler();

    public interface OnValueSelectedListener {
        void onValueSelected(int pickerIndex, int newValue, boolean autoAdvance);
    }

    public TimePicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnTouchListener(this);
        ViewConfiguration vc = ViewConfiguration.get(context);
        TOUCH_SLOP = vc.getScaledTouchSlop();
        TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
        PRESSED_STATE_DURATION = ViewConfiguration.getPressedStateDuration();
        mDoingMove = false;

        mCircleView = new CircleView(context);
        addView(mCircleView);

        mAmPmCirclesView = new AmPmCirclesView(context);
        addView(mAmPmCirclesView);

        mHourRadialTextsView = new RadialTextsView(context);
        addView(mHourRadialTextsView);
        mMinuteRadialTextsView = new RadialTextsView(context);
        addView(mMinuteRadialTextsView);

        mHourRadialSelectorView = new RadialSelectorView(context);
        addView(mHourRadialSelectorView);
        mMinuteRadialSelectorView = new RadialSelectorView(context);
        addView(mMinuteRadialSelectorView);

        setCurrentItemShowing(HOUR_INDEX, false);

        mReselectSelectorRunnable = new ReselectSelectorRunnable(this);

        mVibrator = (Vibrator) context.getSystemService(Service.VIBRATOR_SERVICE);
        mLastVibrate = 0;
        mLastValueSelected = -1;

        mTimeInitialized = false;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        super.onMeasure(widthMeasureSpec,
                measuredWidth < measuredHeight? widthMeasureSpec : heightMeasureSpec);
    }

    public void setOnValueSelectedListener(OnValueSelectedListener listener) {
        mListener = listener;
    }

    public void initialize(Context context, int initialHoursOfDay, int initialMinutes,
            boolean is24HourMode) {
        if (mTimeInitialized) {
            Log.e(TAG, "Time has already been initialized.");
            return;
        }

        setValueForItem(HOUR_INDEX, initialHoursOfDay);
        setValueForItem(MINUTE_INDEX, initialMinutes);
        mIs24HourMode = is24HourMode;

        mCircleView.initialize(context, is24HourMode);
        mCircleView.invalidate();
        if (!is24HourMode) {
            mAmPmCirclesView.initialize(context, initialHoursOfDay < 12? AM : PM);
            mAmPmCirclesView.invalidate();
        }

        Resources res = context.getResources();
        String[] hoursTexts = res.getStringArray(is24HourMode? R.array.hours_24 : R.array.hours);
        String[] innerHoursTexts = res.getStringArray(R.array.hours);
        String[] minutesTexts = res.getStringArray(R.array.minutes);
        mHourRadialTextsView.initialize(res,
                hoursTexts, (is24HourMode? innerHoursTexts : null), is24HourMode, true);
        mHourRadialTextsView.invalidate();
        mMinuteRadialTextsView.initialize(res, minutesTexts, null, is24HourMode, false);
        mMinuteRadialTextsView.invalidate();

        int initialHourDegrees = (initialHoursOfDay % 12) * HOUR_VALUE_TO_DEGREES_STEP_SIZE;
        int initialMinuteDegrees = initialMinutes * MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
        mHourRadialSelectorView.initialize(context, initialHourDegrees,
                is24HourMode, is24HourMode, isHourInnerCircle(initialHoursOfDay), true);
        mHourRadialSelectorView.invalidate();
        mMinuteRadialSelectorView.initialize(context, initialMinuteDegrees,
                is24HourMode, false, false, false);
        mHourRadialSelectorView.invalidate();


        mTimeInitialized = true;
    }

    private boolean isHourInnerCircle(int hourOfDay) {
        // We'll have the 00 hours on the outside circle.
        return mIs24HourMode && (hourOfDay <= 12 && hourOfDay != 0);
    }

    public int getHours() {
        return mCurrentHoursOfDay;
    }

    public int getMinutes() {
        return mCurrentMinutes;
    }

    private int getCurrentlyShowingValue() {
        int currentIndex = getCurrentItemShowing();
        if (currentIndex == HOUR_INDEX) {
            return mCurrentHoursOfDay;
        } else if (currentIndex == MINUTE_INDEX) {
            return mCurrentMinutes;
        } else {
            return -1;
        }
    }

    public int getIsCurrentlyAmOrPm() {
        if (mCurrentHoursOfDay < 12) {
            return AM;
        } else if (mCurrentHoursOfDay < 24) {
            return PM;
        }
        return -1;
    }

    private void setValueForItem(int index, int value) {
        if (index == HOUR_INDEX) {
            mCurrentHoursOfDay = value;
        } else if (index == MINUTE_INDEX){
            mCurrentMinutes = value;
        } else if (index == AMPM_INDEX) {
            if (value == AM) {
                mCurrentHoursOfDay = mCurrentHoursOfDay % 12;
            } else if (value == PM) {
                mCurrentHoursOfDay = (mCurrentHoursOfDay % 12) + 12;
            }
        }
    }

    public void setAmOrPm(int amOrPm) {
        mAmPmCirclesView.setAmOrPm(amOrPm);
        mAmPmCirclesView.invalidate();
        setValueForItem(AMPM_INDEX, amOrPm);
    }

    private int reselectSelector(int index, int degrees, boolean isInnerCircle,
            boolean forceNotFineGrained, boolean forceDrawLine, boolean forceDrawDot) {
        if (degrees == -1 || (index != 0 && index != 1)) {
            return -1;
        }

        int stepSize;
        int currentShowing = getCurrentItemShowing();
        if (!forceNotFineGrained && (currentShowing == 1)) {
            stepSize = MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
        } else {
            stepSize = HOUR_VALUE_TO_DEGREES_STEP_SIZE;
        }
        int floor = (degrees / stepSize) * stepSize;
        int ceiling = floor + stepSize;
        if ((degrees - floor) < (ceiling - degrees)) {
            degrees = floor;
        } else {
            degrees = ceiling;
        }

        RadialSelectorView radialSelectorView;
        if (index == 0) {
            // Index == 0, hours.
            radialSelectorView = mHourRadialSelectorView;
            stepSize = HOUR_VALUE_TO_DEGREES_STEP_SIZE;
        } else {
            // Index == 1, minutes.
            radialSelectorView = mMinuteRadialSelectorView;
            stepSize = MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
        }
        radialSelectorView.setSelection(degrees, isInnerCircle, forceDrawLine, forceDrawDot);
        radialSelectorView.invalidate();


        if (currentShowing == HOUR_INDEX) {
            if (mIs24HourMode) {
                if (degrees == 0 && isInnerCircle) {
                    degrees = 360;
                } else if (degrees == 360 && !isInnerCircle) {
                    degrees = 0;
                }
            } else if (degrees == 0) {
                degrees = 360;
            }
        } else if (degrees == 360 && currentShowing == MINUTE_INDEX) {
            degrees = 0;
        }

        int value = degrees / stepSize;
        if (currentShowing == HOUR_INDEX && mIs24HourMode && !isInnerCircle && degrees != 0) {
            value += 12;
        }
        return value;
    }

    private int getDegreesFromCoords(float pointX, float pointY, boolean forceLegal,
            final Boolean[] isInnerCircle) {
        int currentItem = getCurrentItemShowing();
        if (currentItem == 0) {
            return mHourRadialSelectorView.getDegreesFromCoords(
                    pointX, pointY, forceLegal, isInnerCircle);
        } else if (currentItem == 1) {
            return mMinuteRadialSelectorView.getDegreesFromCoords(
                    pointX, pointY, forceLegal, isInnerCircle);
        } else {
            return -1;
        }
    }

    public int getCurrentItemShowing() {
        if (mCurrentItemShowing != HOUR_INDEX && mCurrentItemShowing != MINUTE_INDEX) {
            Log.e(TAG, "Current item showing was unfortunately set to "+mCurrentItemShowing);
            return -1;
        }
        return mCurrentItemShowing;
    }

    public void setCurrentItemShowing(int index, boolean animate) {
        if (index != HOUR_INDEX && index != MINUTE_INDEX) {
            Log.e(TAG, "TimePicker does not support view at index "+index);
            return;
        }

        if (animate && (index != getCurrentItemShowing())) {
            ObjectAnimator[] anims = new ObjectAnimator[4];
            if (index == MINUTE_INDEX) {
                anims[0] = mHourRadialTextsView.getDisappearAnimator();
                anims[1] = mHourRadialSelectorView.getDisappearAnimator();
                anims[2] = mMinuteRadialTextsView.getReappearAnimator();
                anims[3] = mMinuteRadialSelectorView.getReappearAnimator();
            } else if (index == HOUR_INDEX){
                anims[0] = mHourRadialTextsView.getReappearAnimator();
                anims[1] = mHourRadialSelectorView.getReappearAnimator();
                anims[2] = mMinuteRadialTextsView.getDisappearAnimator();
                anims[3] = mMinuteRadialSelectorView.getDisappearAnimator();
            }

            AnimatorSet transition = new AnimatorSet();
            transition.playTogether(anims);
            transition.start();
        } else {
            int hourAlpha = (index == 0) ? 255 : 0;
            int minuteAlpha = (index == 1) ? 255 : 0;
            mHourRadialTextsView.setAlpha(hourAlpha);
            mHourRadialSelectorView.setAlpha(hourAlpha);
            mMinuteRadialTextsView.setAlpha(minuteAlpha);
            mMinuteRadialSelectorView.setAlpha(minuteAlpha);
        }

        mCurrentItemShowing = index;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final float eventX = event.getX();
        final float eventY = event.getY();
        int degrees;
        int value;
        final int currentShowing = getCurrentItemShowing();
        final Boolean[] isInnerCircle = new Boolean[1];
        isInnerCircle[0] = false;

        long millis = SystemClock.uptimeMillis();

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = eventX;
                mDownY = eventY;

                mLastValueSelected = -1;
                mDoingMove = false;
                if (!mIs24HourMode) {
                    mIsTouchingAmOrPm = mAmPmCirclesView.getIsTouchingAmOrPm(eventX, eventY);
                } else {
                    mIsTouchingAmOrPm = -1;
                }
                if (mIsTouchingAmOrPm == AM || mIsTouchingAmOrPm == PM) {
                    tryVibrate();
                    mDownDegrees = -1;
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mAmPmCirclesView.setAmOrPmPressed(mIsTouchingAmOrPm);
                            mAmPmCirclesView.invalidate();
                        }
                    }, TAP_TIMEOUT);
                } else {
                    mDownDegrees = getDegreesFromCoords(eventX, eventY, false, isInnerCircle);
                    if (mDownDegrees != -1) {
                        tryTick();
                        mLastValueSelected = getCurrentlyShowingValue();
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mDoingMove = true;
                                int value = reselectSelector(currentShowing, mDownDegrees,
                                        isInnerCircle[0], false, true, true);
                                mListener.onValueSelected(getCurrentItemShowing(), value, false);
                            }
                        }, TAP_TIMEOUT);
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dY = Math.abs(eventY - mDownY);
                float dX = Math.abs(eventX - mDownX);

                if (!mDoingMove && dX <= TOUCH_SLOP && dY <= TOUCH_SLOP) {
                    // Hasn't registered down yet, just slight, accidental movement of finger.
                    break;
                }

                // If we're in the middle of touching down on AM or PM, check if we still are.
                // If so, no-op. If not, remove its pressed state. Either way, no need to check
                // for touches on the other circle.
                if (mIsTouchingAmOrPm == AM || mIsTouchingAmOrPm == PM) {
                    mHandler.removeCallbacksAndMessages(null);
                    int isTouchingAmOrPm = mAmPmCirclesView.getIsTouchingAmOrPm(eventX, eventY);
                    if (isTouchingAmOrPm != mIsTouchingAmOrPm) {
                        mAmPmCirclesView.setAmOrPmPressed(-1);
                        mAmPmCirclesView.invalidate();
                        mIsTouchingAmOrPm = -1;
                    }
                    break;
                }

                if (mDownDegrees == -1) {
                    // Original down was illegal, so no movement will register.
                    break;
                }

                mDoingMove = true;
                mHandler.removeCallbacksAndMessages(null);
                degrees = getDegreesFromCoords(eventX, eventY, true, isInnerCircle);
                if (degrees != -1) {
                    value = reselectSelector(currentShowing, degrees,
                            isInnerCircle[0], false, true, true);
                    if (value != mLastValueSelected) {
                        tryTick();
                        mLastValueSelected = value;
                    }
                    mListener.onValueSelected(getCurrentItemShowing(), value, false);
                }
                return true;
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacksAndMessages(null);

                if (mIsTouchingAmOrPm == AM || mIsTouchingAmOrPm == PM) {
                    int isTouchingAmOrPm = mAmPmCirclesView.getIsTouchingAmOrPm(eventX, eventY);
                    mAmPmCirclesView.setAmOrPmPressed(-1);
                    mAmPmCirclesView.invalidate();

                    if (isTouchingAmOrPm == mIsTouchingAmOrPm) {
                        mAmPmCirclesView.setAmOrPm(isTouchingAmOrPm);
                        if (getIsCurrentlyAmOrPm() != isTouchingAmOrPm) {
                            mListener.onValueSelected(AMPM_INDEX, mIsTouchingAmOrPm, false);
                            setValueForItem(AMPM_INDEX, isTouchingAmOrPm);
                        }
                    }
                    mIsTouchingAmOrPm = -1;
                    break;
                }

                if (mDownDegrees != -1) {
                    degrees = getDegreesFromCoords(eventX, eventY, mDoingMove, isInnerCircle);
                    if (degrees != -1) {
                        value = reselectSelector(currentShowing, degrees, isInnerCircle[0],
                                !mDoingMove, true, false);
                        mListener.onValueSelected(getCurrentItemShowing(), value, true);

                        if (currentShowing == HOUR_INDEX && !mIs24HourMode) {
                            int amOrPm = getIsCurrentlyAmOrPm();
                            if (amOrPm == AM && value == 12) {
                                value = 0;
                            } else if (amOrPm == PM && value != 12) {
                                value += 12;
                            }
                        }
                        setValueForItem(getCurrentItemShowing(), value);
                    }
                }
                mDoingMove = false;
                return true;
            default:
                break;
        }
        return false;
    }

    private class ReselectSelectorRunnable implements Runnable {
        TimePicker mTimePicker;
        private int mIndex;
        private int mDegrees;
        private boolean mIsInnerCircle;
        private boolean mForceNotFineGrained;
        private boolean mForceDrawLine;
        private boolean mForceDrawDot;

        public ReselectSelectorRunnable(TimePicker timePicker) {
            mTimePicker = timePicker;
        }

        public void initializeValues(int index, int degrees, boolean isInnerCircle,
                boolean forceNotFineGrained, boolean forceDrawLine, boolean forceDrawDot) {
            mIndex = index;
            mDegrees = degrees;
            mIsInnerCircle = isInnerCircle;
            mForceNotFineGrained = forceNotFineGrained;
            mForceDrawDot = forceDrawDot;
        }

        @Override
        public void run() {
            mTimePicker.reselectSelector(mIndex, mDegrees, mIsInnerCircle, mForceNotFineGrained,
                    mForceDrawLine, mForceDrawDot);
        }
    }

    public void tryVibrate() {
        if (mVibrator != null) {
            long now = SystemClock.uptimeMillis();
            // We want to try to vibrate each individual tick discretely.
            if (now - mLastVibrate >= 100) {
                mVibrator.vibrate(5);
                mLastVibrate = now;
            }
        }
    }

    public void tryTick() {
        tryVibrate();
    }
}
