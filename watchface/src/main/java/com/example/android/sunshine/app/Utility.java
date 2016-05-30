package com.example.android.sunshine.app;

import android.content.Context;
import android.text.format.Time;

/**
 * Created by Vincent on 2016-04-23.
 */
public class Utility {

    /**
     * Helper method to provide the icon resource id according to the weather condition id returned
     * by the OpenWeatherMap call.
     * @param weatherId from OpenWeatherMap API response
     * @return resource id for the corresponding icon. -1 if no relation is found.
     */
    public static int getIconResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
    }

    public static String getDateString(Context context, Time time) {
        String date;
        int weekDay;
        int month;
        int monthDay;
        int year;

        // Format weekDay (e.g.: SUN, MON, TUE, WED, THU, FRI, SAT)
        weekDay = time.weekDay;
        switch(weekDay) {
            case 0:
                date = context.getString(R.string.sunday);
                break;
            case 1:
                date = context.getString(R.string.monday);
                break;
            case 2:
                date = context.getString(R.string.tuesday);
                break;
            case 3:
                date = context.getString(R.string.wednesday);
                break;
            case 4:
                date = context.getString(R.string.thursday);
                break;
            case 5:
                date = context.getString(R.string.friday);
                break;
            case 6:
                date = context.getString(R.string.saturday);
                break;
            default:
                date = "";
        }

        // Format month (e.g.: JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV, DEC)
        month = time.month;
        switch(month) {
            case 0:
                date += context.getString(R.string.january);
                break;
            case 1:
                date += context.getString(R.string.february);
                break;
            case 2:
                date += context.getString(R.string.march);
                break;
            case 3:
                date += context.getString(R.string.april);
                break;
            case 4:
                date += context.getString(R.string.may);
                break;
            case 5:
                date += context.getString(R.string.june);
                break;
            case 6:
                date += context.getString(R.string.july);
                break;
            case 7:
                date += context.getString(R.string.august);
                break;
            case 8:
                date += context.getString(R.string.september);
                break;
            case 9:
                date += context.getString(R.string.october);
                break;
            case 10:
                date += context.getString(R.string.november);
                break;
            case 11:
                date += context.getString(R.string.december);
                break;
            default:
                date += "";
        }

        // Format monthDay (e.g.: 01, 02, 03, 04, ..., 29, 30, 31)
        monthDay = time.monthDay;
        date += " " + monthDay;

        // Format year (e.g.: 2016, 2017, 2018, ...)
        year = time.year;
        date += " " + year;

        return date;
    }

}