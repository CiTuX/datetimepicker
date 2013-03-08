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

import com.android.datetimepicker.date.SimpleMonthAdapter.CalendarDay;

/**
 * Controller class to communicate among the various components of the date picker dialog.
 */
public interface DatePickerController {

    public void onYearPickerSelectionChanged(int year);

    public void onMonthPickerSelectionChanged(int month);

    public void onDayPickerSelectionChanged(int year, int month, int day);

    public CalendarDay getSelectedDay();

    public int getFirstDayOfWeek();

    public int getMinYear();

    public int getMaxYear();
}
