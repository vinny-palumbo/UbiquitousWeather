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

package com.example.android.sunshine.app;

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
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class DigitalWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "DigitalWatchFaceService";

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
//            Log.d(TAG, "handleMessage");
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

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        private static final String PATH_WEATHER = "/watchface-weather";
        private static final String PATH_WEATHER_DETAILS = "/watchface-weather-data";

        private static final String KEY_HIGH_TEMP = "highTemp";
        private static final String KEY_LOW_TEMP = "lowTemp";
        private static final String KEY_WEATHER_ID = "weatherId";

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

        int weatherId = -1;
        Bitmap weatherIcon;
        String highTempText;
        String lowTempText;

        GoogleApiClient mGoogleApiClient;

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

            mGoogleApiClient = new GoogleApiClient.Builder(DigitalWatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
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
                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
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
            String dateText = Utility.getDateString(getApplicationContext(), mTime);// e.g.: "FRI, JUL 14 2015"
            canvas.drawText(dateText, mXOffsetDate, mYOffsetDate, mDateTextPaint);

            // Add horizontal line in the center
            float left = bounds.width() * 4/10;
            float right = bounds.width() * 6/10;
            float top = bounds.height() * 57/100 + 1;
            float bottom = bounds.height() * 57/100;
            if(highTempText != null || lowTempText != null || weatherIcon != null){
                canvas.drawRect(left, top, right, bottom, mLinePaint);
            }

            // Add weather icon
            weatherId = 800;
            if(weatherId > -1){
                Bitmap weatherIcon = BitmapFactory.decodeResource(getResources(),
                        Utility.getIconResourceForWeatherCondition(weatherId));
                canvas.drawBitmap(weatherIcon, mXOffsetIcon, mYOffsetIcon, null);
            }

            // Add High Temp
            highTempText = "0";
            if(highTempText != null){
                String formattedHighTemp = String.format(getString(R.string.format_temperature), highTempText);
                canvas.drawText(formattedHighTemp, mXOffsetHighTemp, mYOffsetHighTemp, mHighTempTextPaint);
            }

            // Add Low Temp
            lowTempText = "0";
            if(lowTempText != null) {
                String formattedLowTemp = String.format(getString(R.string.format_temperature), lowTempText);
                canvas.drawText(formattedLowTemp, mXOffsetLowTemp, mYOffsetLowTemp, mLowTempTextPaint);
            }
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

        // methods related to Google API Client

        @Override
        public void onConnected(Bundle bundle){
            Log.d(TAG, "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            getUpdatedWeatherForecastFromPhone();
        }

        public void getUpdatedWeatherForecastFromPhone() {
            Log.d(TAG, "getUpdatedWeatherForecastFromPhone");
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WEATHER);

            PutDataRequest request = putDataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.d(TAG, "Failed asking phone for updated weather forecast");
                            } else {
                                Log.d(TAG, "Successfully asked phone for updated weather forecast");
                            }
                        }
                    });
        }

        @Override
        public void onConnectionSuspended(int i){
            Log.d(TAG, "onConnectionSuspended(): Connection to Google API client was suspended");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult){
            Log.e(TAG, "onConnectionFailed(): Failed to connect, with result: " + connectionResult);
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged");

            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    String path = event.getDataItem().getUri().getPath();
                    if (path.equals(Engine.PATH_WEATHER)) {
                        if (dataMap.containsKey(KEY_HIGH_TEMP)) {
                            highTempText = dataMap.getString(KEY_HIGH_TEMP);
                            Log.d(TAG, "High: " + highTempText);
                        }
                        if (dataMap.containsKey(KEY_LOW_TEMP)) {
                            lowTempText = dataMap.getString(KEY_LOW_TEMP);
                            Log.d(TAG, "Low: " + lowTempText);
                        }
                        if (dataMap.containsKey(KEY_WEATHER_ID)) {
                            weatherId = dataMap.getInt(KEY_WEATHER_ID);
                            Log.d(TAG, "Weather ID: " + weatherId);
                        }
                        invalidate();
                    }
                }
            }
        }
    }
}