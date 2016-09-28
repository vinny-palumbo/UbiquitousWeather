package com.example.android.sunshine.app.wear;

import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;

public class WatchfaceWeatherService extends WearableListenerService {
    private static final String TAG = "WatchfaceWeatherService";

    private static final String PATH_WEATHER = "/watchface-weather";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged");
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                String path = dataEvent.getDataItem().getUri().getPath();
                if (path.equals(PATH_WEATHER)) {
                    SunshineSyncAdapter.syncImmediately(this);
                }
            }
        }
    }
}