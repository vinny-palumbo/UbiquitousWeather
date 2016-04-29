/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.vinnypalumbo.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class DigitalWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<DigitalWatchFaceService.Engine> mWeakReference;

        public EngineHandler(DigitalWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            DigitalWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTimeTextPaint;
        Paint mDateTextPaint;
        Paint mLinePaint;
        Paint mHighTempTextPaint;
        Paint mLowTempTextPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        float mXOffsetTime;
        float mYOffsetTime;
        float mXOffsetDate;
        float mYOffsetDate;
        float mXOffsetIcon;
        float mYOffsetIcon;
        float mXOffsetHighTemp;
        float mYOffsetHighTemp;
        float mXOffsetLowTemp;
        float mYOffsetLowTemp;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(DigitalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = DigitalWatchFaceService.this.getResources();

            // set offsets

            mYOffsetTime = resources.getDimension(R.dimen.digital_y_offset_time);

            mXOffsetDate = resources.getDimension(R.dimen.digital_x_offset_date);
            mYOffsetDate = resources.getDimension(R.dimen.digital_y_offset_date);

            mXOffsetIcon = resources.getDimension(R.dimen.digital_x_offset_icon);
            mYOffsetIcon = resources.getDimension(R.dimen.digital_y_offset_icon);

            mXOffsetHighTemp = resources.getDimension(R.dimen.digital_x_offset_hightemp);
            mYOffsetHighTemp = resources.getDimension(R.dimen.digital_y_offset_hightemp);

            mXOffsetLowTemp = resources.getDimension(R.dimen.digital_x_offset_lowtemp);
            mYOffsetLowTemp = resources.getDimension(R.dimen.digital_y_offset_lowtemp);

            // set paint colors

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimeTextPaint = new Paint();
            mTimeTextPaint = createTextPaint(resources.getColor(R.color.white));

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.faded_white));

            mLinePaint = new Paint();
            mLinePaint = createTextPaint(resources.getColor(R.color.more_faded_white));

            mHighTempTextPaint = new Paint();
            mHighTempTextPaint = createTextPaint(resources.getColor(R.color.white));

            mLowTempTextPaint = new Paint();
            mLowTempTextPaint = createTextPaint(resources.getColor(R.color.faded_white));

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            DigitalWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            DigitalWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = DigitalWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffsetTime = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_time_round : R.dimen.digital_x_offset_time);
            float timeTextSize = resources.getDimension(R.dimen.digital_time_text_size);
            float dateTextSize = resources.getDimension(R.dimen.digital_date_text_size);
            float highTempTextSize = resources.getDimension(R.dimen.digital_hightemp_text_size);
            float lowTempTextSize = resources.getDimension(R.dimen.digital_lowtemp_text_size);

            mTimeTextPaint.setTextSize(timeTextSize);
            mDateTextPaint.setTextSize(dateTextSize);
            mHighTempTextPaint.setTextSize(highTempTextSize);
            mLowTempTextPaint.setTextSize(lowTempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                    mLinePaint.setAntiAlias(!inAmbientMode);
                    mHighTempTextPaint.setAntiAlias(!inAmbientMode);
                    mLowTempTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            mTime.setToNow();
            // Draw the Time
            String timeText = String.format("%02d:%02d", mTime.hour, mTime.minute);
            canvas.drawText(timeText, mXOffsetTime, mYOffsetTime, mTimeTextPaint);

            // Add date string
            String dateText = getDateString(mTime);// e.g.: "FRI, JUL 14 2015"
            canvas.drawText(dateText, mXOffsetDate, mYOffsetDate, mDateTextPaint);

            // Add horizontal line in the center
            float left = bounds.width() * 4/10;
            float right = bounds.width() * 6/10;
            float top = bounds.height() * 57/100 + 1;
            float bottom = bounds.height() * 57/100;
            canvas.drawRect(left, top, right, bottom, mLinePaint);

            // Add weather icon
            Bitmap weatherIcon = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_clear);
            canvas.drawBitmap(weatherIcon, mXOffsetIcon, mYOffsetIcon, null);

            // Add High Temp
            float highTempText= 25;
            String formattedHighTemp = String.format(getString(R.string.format_temperature), highTempText);
            canvas.drawText(formattedHighTemp, mXOffsetHighTemp, mYOffsetHighTemp, mHighTempTextPaint);

            // Add Low Temp
            float lowTempText= 16;
            String formattedLowTemp = String.format(getString(R.string.format_temperature), lowTempText);
            canvas.drawText(formattedLowTemp, mXOffsetLowTemp, mYOffsetLowTemp, mLowTempTextPaint);
        }

        private String getDateString(Time time) {
            String date;
            int weekDay;
            int month;
            int monthDay;
            int year;

            // Format weekDay (e.g.: SUN, MON, TUE, WED, THU, FRI, SAT)
            weekDay = mTime.weekDay;
            switch(weekDay) {
                case 0:
                    date = getResources().getString(R.string.sunday);
                    break;
                case 1:
                    date = getResources().getString(R.string.monday);
                    break;
                case 2:
                    date = getResources().getString(R.string.tuesday);
                    break;
                case 3:
                    date = getResources().getString(R.string.wednesday);
                    break;
                case 4:
                    date = getResources().getString(R.string.thursday);
                    break;
                case 5:
                    date = getResources().getString(R.string.friday);
                    break;
                case 6:
                    date = getResources().getString(R.string.saturday);
                    break;
                default:
                    date = "";
            }

            // Format month (e.g.: JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV, DEC)
            month = mTime.month;
            switch(month) {
                case 0:
                    date += getResources().getString(R.string.january);
                    break;
                case 1:
                    date += getResources().getString(R.string.february);
                    break;
                case 2:
                    date += getResources().getString(R.string.march);
                    break;
                case 3:
                    date += getResources().getString(R.string.april);
                    break;
                case 4:
                    date += getResources().getString(R.string.may);
                    break;
                case 5:
                    date += getResources().getString(R.string.june);
                    break;
                case 6:
                    date += getResources().getString(R.string.july);
                    break;
                case 7:
                    date += getResources().getString(R.string.august);
                    break;
                case 8:
                    date += getResources().getString(R.string.september);
                    break;
                case 9:
                    date += getResources().getString(R.string.october);
                    break;
                case 10:
                    date += getResources().getString(R.string.november);
                    break;
                case 11:
                    date += getResources().getString(R.string.december);
                    break;
                default:
                    date += "";
            }

            // Format monthDay (e.g.: 01, 02, 03, 04, ..., 29, 30, 31)
            monthDay = mTime.monthDay;
            date += " " + monthDay;

            // Format year (e.g.: 2016, 2017, 2018, ...)
            year = mTime.year;
            date += " " + year;

            return date;
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}