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

package com.android.datetimepicker.time;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import com.android.datetimepicker.R;

public class RadialPickerLayout extends FrameLayout implements OnTouchListener {
    private static final String TAG = "TimePicker";

    private final int TOUCH_SLOP;
    private final int TAP_TIMEOUT;
    private static final int HOUR_VALUE_TO_DEGREES_STEP_SIZE = 30;
    private static final int MINUTE_VALUE_TO_DEGREES_STEP_SIZE = 6;
    private static final int HOUR_INDEX = TimePickerDialog.HOUR_INDEX;
    private static final int MINUTE_INDEX = TimePickerDialog.MINUTE_INDEX;
    private static final int AMPM_INDEX = TimePickerDialog.AMPM_INDEX;
    private static final int ENABLE_PICKER_INDEX = TimePickerDialog.ENABLE_PICKER_INDEX;
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
    private boolean mHideAmPm;
    private int mCurrentItemShowing;

    private CircleView mCircleView;
    private AmPmCirclesView mAmPmCirclesView;
    private RadialTextsView mHourRadialTextsView;
    private RadialTextsView mMinuteRadialTextsView;
    private RadialSelectorView mHourRadialSelectorView;
    private RadialSelectorView mMinuteRadialSelectorView;
    private View mGrayBox;

    private boolean mInputEnabled;
    private int mIsTouchingAmOrPm = -1;
    private boolean mDoingMove;
    private boolean mDoingTouch;
    private int mDownDegrees;
    private float mDownX;
    private float mDownY;
    private AccessibilityManager mAccessibilityManager;

    private Handler mHandler = new Handler();

    public interface OnValueSelectedListener {
        void onValueSelected(int pickerIndex, int newValue, boolean autoAdvance);
    }

    public RadialPickerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnTouchListener(this);
        ViewConfiguration vc = ViewConfiguration.get(context);
        TOUCH_SLOP = vc.getScaledTouchSlop();
        TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
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

        mVibrator = (Vibrator) context.getSystemService(Service.VIBRATOR_SERVICE);
        mLastVibrate = 0;
        mLastValueSelected = -1;

        mTimeInitialized = false;

        mInputEnabled = true;
        mGrayBox = new View(context);
        mGrayBox.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mGrayBox.setBackgroundColor(getResources().getColor(R.color.black_50));
        mGrayBox.setVisibility(View.INVISIBLE);
        addView(mGrayBox);

        mAccessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
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
        mIs24HourMode = is24HourMode;
        mHideAmPm = mAccessibilityManager.isTouchExplorationEnabled()? true : mIs24HourMode;

        mCircleView.initialize(context, mHideAmPm);
        mCircleView.invalidate();
        if (!mHideAmPm) {
            mAmPmCirclesView.initialize(context, initialHoursOfDay < 12? AM : PM);
            mAmPmCirclesView.invalidate();
        }

        Resources res = context.getResources();
        int[] hours = {12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        int[] hours_24 = {0, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23};
        int[] minutes = {0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55};
        String[] hoursTexts = new String[12];
        String[] innerHoursTexts = new String[12];
        String[] minutesTexts = new String[12];
        for (int i = 0; i < 12; i++) {
            hoursTexts[i] = is24HourMode?
                    String.format("%02d", hours_24[i]) : String.format("%d", hours[i]);
            innerHoursTexts[i] = String.format("%d", hours[i]);
            minutesTexts[i] = String.format("%02d", minutes[i]);
        }
        mHourRadialTextsView.initialize(res,
                hoursTexts, (is24HourMode? innerHoursTexts : null), mHideAmPm, true);
        mHourRadialTextsView.invalidate();
        mMinuteRadialTextsView.initialize(res, minutesTexts, null, mHideAmPm, false);
        mMinuteRadialTextsView.invalidate();

        setValueForItem(HOUR_INDEX, initialHoursOfDay);
        setValueForItem(MINUTE_INDEX, initialMinutes);
        int hourDegrees = (initialHoursOfDay % 12) * HOUR_VALUE_TO_DEGREES_STEP_SIZE;
        mHourRadialSelectorView.initialize(context, mHideAmPm, is24HourMode, true,
                hourDegrees, isHourInnerCircle(initialHoursOfDay));
        int minuteDegrees = initialMinutes * MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
        mMinuteRadialSelectorView.initialize(context, mHideAmPm, false, false,
                minuteDegrees, false);

        mTimeInitialized = true;
    }

    public void setTime(int hours, int minutes) {
        setItem(HOUR_INDEX, hours);
        setItem(MINUTE_INDEX, minutes);
    }

    private void setItem(int index, int value) {
        if (index == HOUR_INDEX) {
            setValueForItem(HOUR_INDEX, value);
            int hourDegrees = (value % 12) * HOUR_VALUE_TO_DEGREES_STEP_SIZE;
            mHourRadialSelectorView.setSelection(hourDegrees, isHourInnerCircle(value),
                    false, false, false);
            mHourRadialSelectorView.invalidate();
        } else if (index == MINUTE_INDEX) {
            setValueForItem(MINUTE_INDEX, value);
            int minuteDegrees = value * MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
            mMinuteRadialSelectorView.setSelection(minuteDegrees, false, false, false, false);
            mMinuteRadialSelectorView.invalidate();
        }
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

    private int highPass30sFilter(int degrees) {
        int offset = (degrees + 2) / 30;
        degrees = Math.max(degrees - (30*offset + 4), 0) + 20*offset;
        degrees /= 4;
        degrees *= 6;
        /* // less aggressive filtering.
        degrees /= 5;
        int offset = degrees / 6;
        degrees = degrees - offset;
        degrees *= 6; */
        return degrees;
    }

    private int snapToStepSize(int degrees, int stepSize, int ceilingOrFloor) {
        int floor = (degrees / stepSize) * stepSize;
        int ceiling = floor + stepSize;
        if (ceilingOrFloor == 1) {
            degrees = ceiling;
        } else if (ceilingOrFloor == -1) {
            if (degrees == floor) {
                floor -= stepSize;
            }
            degrees = floor;
        } else {
            if ((degrees - floor) < (ceiling - degrees)) {
                degrees = floor;
            } else {
                degrees = ceiling;
            }
        }
        return degrees;
    }

    private int reselectSelector(int degrees, boolean isInnerCircle,
            boolean forceNotFineGrained, boolean forceDrawLine, boolean forceDrawDot) {
        if (degrees == -1) {
            return -1;
        }
        int currentShowing = getCurrentItemShowing();

        int stepSize;
        boolean allowFineGrained = !forceNotFineGrained && (currentShowing == MINUTE_INDEX);
        if (allowFineGrained) {
            degrees = highPass30sFilter(degrees);
        } else {
            degrees = snapToStepSize(degrees, HOUR_VALUE_TO_DEGREES_STEP_SIZE, 0);
        }

        RadialSelectorView radialSelectorView;
        if (currentShowing == HOUR_INDEX) {
            radialSelectorView = mHourRadialSelectorView;
            stepSize = HOUR_VALUE_TO_DEGREES_STEP_SIZE;
        } else {
            radialSelectorView = mMinuteRadialSelectorView;
            stepSize = MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
        }
        radialSelectorView.setSelection(degrees, isInnerCircle, forceDrawLine, forceDrawDot, false);
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
        if (currentItem == HOUR_INDEX) {
            return mHourRadialSelectorView.getDegreesFromCoords(
                    pointX, pointY, forceLegal, isInnerCircle);
        } else if (currentItem == MINUTE_INDEX) {
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

        int lastIndex = getCurrentItemShowing();
        mCurrentItemShowing = index;

        if (animate && (index != lastIndex)) {
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
            int hourAlpha = (index == HOUR_INDEX) ? 255 : 0;
            int minuteAlpha = (index == MINUTE_INDEX) ? 255 : 0;
            mHourRadialTextsView.setAlpha(hourAlpha);
            mHourRadialSelectorView.setAlpha(hourAlpha);
            mMinuteRadialTextsView.setAlpha(minuteAlpha);
            mMinuteRadialSelectorView.setAlpha(minuteAlpha);
        }

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final float eventX = event.getX();
        final float eventY = event.getY();
        int degrees;
        int value;
        final Boolean[] isInnerCircle = new Boolean[1];
        isInnerCircle[0] = false;

        long millis = SystemClock.uptimeMillis();

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mInputEnabled) {
                    return true;
                }

                mDownX = eventX;
                mDownY = eventY;

                mLastValueSelected = -1;
                mDoingMove = false;
                mDoingTouch = true;
                if (!mHideAmPm) {
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
                    boolean forceLegal = mAccessibilityManager.isTouchExplorationEnabled();
                    mDownDegrees = getDegreesFromCoords(eventX, eventY, forceLegal, isInnerCircle);
                    if (mDownDegrees != -1) {
                        tryVibrate();
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mDoingMove = true;
                                int value = reselectSelector(mDownDegrees,
                                        isInnerCircle[0], false, true, true);
                                mLastValueSelected = value;
                                mListener.onValueSelected(getCurrentItemShowing(), value, false);
                            }
                        }, TAP_TIMEOUT);
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mInputEnabled) {
                    // We shouldn't be in this state, because input is disabled.
                    Log.e(TAG, "Input was disabled, but received ACTION_MOVE.");
                    return true;
                }

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
                    value = reselectSelector(degrees,
                            isInnerCircle[0], false, true, true);
                    if (value != mLastValueSelected) {
                        tryVibrate();
                        mLastValueSelected = value;
                        mListener.onValueSelected(getCurrentItemShowing(), value, false);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!mInputEnabled) {
                    Log.d(TAG, "Input was disabled, but received ACTION_UP.");
                    mListener.onValueSelected(ENABLE_PICKER_INDEX, 1, false);
                    return true;
                }

                mHandler.removeCallbacksAndMessages(null);
                mDoingTouch = false;

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
                        value = reselectSelector(degrees, isInnerCircle[0],
                                !mDoingMove, true, false);
                        if (getCurrentItemShowing() == HOUR_INDEX && !mIs24HourMode) {
                            int amOrPm = getIsCurrentlyAmOrPm();
                            if (amOrPm == AM && value == 12) {
                                value = 0;
                            } else if (amOrPm == PM && value != 12) {
                                value += 12;
                            }
                        }
                        setValueForItem(getCurrentItemShowing(), value);
                        mListener.onValueSelected(getCurrentItemShowing(), value, true);
                    }
                }
                mDoingMove = false;
                return true;
            default:
                break;
        }
        return false;
    }

    public void tryVibrate() {
        if (mVibrator != null) {
            long now = SystemClock.uptimeMillis();
            // We want to try to vibrate each individual tick discretely.
            if (now - mLastVibrate >= 125) {
                mVibrator.vibrate(5);
                mLastVibrate = now;
            }
        }
    }

    public boolean trySettingInputEnabled(boolean inputEnabled) {
        if (mDoingTouch && !inputEnabled) {
            // If we're trying to disable input, but we're in the middle of a touch event,
            // we'll allow the touch event to continue before disabling input.
            return false;
        }
        mInputEnabled = inputEnabled;
        mGrayBox.setVisibility(inputEnabled? View.INVISIBLE : View.VISIBLE);
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
      info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.getText().clear();
            Time time = new Time();
            time.hour = getHours();
            time.minute = getMinutes();
            long millis = time.normalize(true);
            int flags = DateUtils.FORMAT_SHOW_TIME;
            if (mIs24HourMode) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            String timeString = DateUtils.formatDateTime(getContext(), millis, flags);
            event.getText().add(timeString);
            return true;
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    @SuppressLint("NewApi")
    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }

        int changeMultiplier = 0;
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            changeMultiplier = 1;
        } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            changeMultiplier = -1;
        }
        if (changeMultiplier != 0) {
            int value = getCurrentlyShowingValue();
            int stepSize = 0;
            int currentItemShowing = getCurrentItemShowing();
            if (currentItemShowing == HOUR_INDEX) {
                stepSize = HOUR_VALUE_TO_DEGREES_STEP_SIZE;
                value %= 12;
            } else if (currentItemShowing == MINUTE_INDEX) {
                stepSize = MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
            }

            int degrees = value * stepSize;
            degrees = snapToStepSize(degrees, HOUR_VALUE_TO_DEGREES_STEP_SIZE, changeMultiplier);
            value = degrees / stepSize;
            int maxValue = 0;
            int minValue = 0;
            if (currentItemShowing == HOUR_INDEX) {
                if (mIs24HourMode) {
                    maxValue = 23;
                } else {
                    maxValue = 12;
                    minValue = 1;
                }
            } else {
                maxValue = 55;
            }
            if (value > maxValue) {
                // If we scrolled forward past the highest number, wrap around to the lowest.
                value = minValue;
            } else if (value < minValue) {
                // If we scrolled backward past the lowest number, wrap around to the highest.
                value = maxValue;
            }
            setItem(currentItemShowing, value);
            mListener.onValueSelected(currentItemShowing, value, false);
            return true;
        }

        return false;
    }
}
