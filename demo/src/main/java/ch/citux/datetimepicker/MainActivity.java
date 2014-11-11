package ch.citux.datetimepicker;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.datetimepicker.date.DatePickerDialog;
import com.android.datetimepicker.time.RadialPickerLayout;
import com.android.datetimepicker.time.TimePickerDialog;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;


public class MainActivity extends Activity implements DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {

    private static final String TIME_PATTERN = "HH:mm";

    private TextView lblDate;
    private TextView lblTime;
    private Calendar calendar;
    private DateFormat dateFormat;
    private SimpleDateFormat timeFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        calendar = Calendar.getInstance();
        dateFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());
        timeFormat = new SimpleDateFormat(TIME_PATTERN, Locale.getDefault());

        lblDate = (TextView) findViewById(R.id.lblDate);
        lblTime = (TextView) findViewById(R.id.lblTime);

        update();
    }

    private void update() {
        lblDate.setText(dateFormat.format(calendar.getTime()));
        lblTime.setText(timeFormat.format(calendar.getTime()));
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnDatePicker:
                DatePickerDialog.newInstance(this, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show(getFragmentManager(), "datePicker");
                break;
            case R.id.btnTimePicker:
                TimePickerDialog.newInstance(this, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show(getFragmentManager(), "timePicker");
                break;
        }
    }

    @Override
    public void onDateSet(DatePickerDialog dialog, int year, int monthOfYear, int dayOfMonth) {
        calendar.set(year, monthOfYear, dayOfMonth);
        update();
    }

    @Override
    public void onTimeSet(RadialPickerLayout view, int hourOfDay, int minute) {
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        update();
    }
}
