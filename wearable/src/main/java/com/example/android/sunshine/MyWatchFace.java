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

package com.example.android.sunshine;

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
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.sync.DataRequestSender;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
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
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    /*^
     *  Credit to Udacity Forums and specifically this post (Receiving data from device to the wearable emulator) for
     *  basic understanding of how to implement a DataListener to handle data transfer from app to wearlable.
     */

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener{
        public final String LOG_TAG = Engine.class.getSimpleName();
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaintTop;
        Paint mBackgroundPaintBottom;
        Paint mTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
        private boolean isRound;

        GoogleApiClient mGoogleApiClient;

        private final Double WEATHER_NON_UPDATED_VALUE = 0d;
        private int mConditionId;
        private int mIconId = R.drawable.ic_clear;
        private double mHighTemp = WEATHER_NON_UPDATED_VALUE;
        private double mLowTemp = WEATHER_NON_UPDATED_VALUE;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
//            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaintTop = new Paint();
            mBackgroundPaintTop.setColor(resources.getColor(R.color.colorPrimary));
            mBackgroundPaintBottom = new Paint();
            mBackgroundPaintBottom.setColor(resources.getColor(R.color.colorPrimaryDark));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.white));

            mCalendar = Calendar.getInstance();

            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            Log.e(LOG_TAG, "Connected");
//                            Wearable.DataApi.addListener(mGoogleApiClient, LayoutFaceService.this);

                            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this).setResultCallback(new ResultCallback<Status>() {
                                @Override
                                public void onResult(@NonNull Status status) {
                                    Log.d(LOG_TAG, "Result status is " + status.toString());
                                }
                            });
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            Log.d(LOG_TAG, "Suspended : " + i);
                            Wearable.DataApi.removeListener(mGoogleApiClient, Engine.this);
                            if (mGoogleApiClient.isConnected()){
                                mGoogleApiClient.disconnect();
                            }
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Log.e(LOG_TAG, "onConnectionFailed : " + connectionResult);
                        }
                    })
                    .build();
            mGoogleApiClient.connect();
            //Request temperature data if it hasn't been synced yet.
            if (mHighTemp == WEATHER_NON_UPDATED_VALUE && mLowTemp == WEATHER_NON_UPDATED_VALUE){
                requestDataFromMobile();
            }
        }
        private void requestDataFromMobile(){
            PutDataMapRequest putDataMapRequest=PutDataMapRequest.create(("/data_request"));

            putDataMapRequest.getDataMap().putString("data_request","");
            putDataMapRequest.getDataMap().putLong("data_request_time", System.currentTimeMillis());

            DataRequestSender sender = DataRequestSender.getInstance();
            sender.setupClient(MyWatchFace.this, putDataMapRequest);
            sender.sendRequestForDataToMobile();
        }
        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
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
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
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
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            isRound = insets.isRound();
//            mXOffset = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
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
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
//                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
//                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height()/2, mBackgroundPaintTop);
                canvas.drawRect(0, bounds.height()/2, bounds.width(), bounds.height(), mBackgroundPaintBottom);
            }

            int centerX = bounds.width()/2;
            int centerY = bounds.height()/2;
            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));

            SimpleDateFormat simpleTimeFormat = new SimpleDateFormat("HH:mm");
            String timeText = simpleTimeFormat.format(mCalendar.getTime());
            Paint weatherTextPaint = createTextPaint(Color.WHITE);

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, MMM dd yyyy");
            String dateText = simpleDateFormat.format(mCalendar.getTime());
            Paint dateTextPaint = createTextPaint(Color.LTGRAY);
            dateTextPaint.setTextSize(20f);


            String formattedHighTemp = Utilities.formatTemperature(MyWatchFace.this, mHighTemp);;
            String formattedLowTemp = Utilities.formatTemperature(MyWatchFace.this, mLowTemp);

            Paint titlePaint = createTextPaint(Color.WHITE);
            titlePaint.setTextSize(20f);

            weatherTextPaint.setTextSize(12f);

            Paint highTemptTextPaint = createTextPaint(Color.WHITE);
            highTemptTextPaint.setTextSize(35f);

            Paint lowTempTextPaint = createTextPaint(Color.LTGRAY);
            lowTempTextPaint.setTextSize(35f);

            Paint linePaint = new Paint();
            linePaint.setColor(Color.LTGRAY);

            if (!isRound) {
                canvas.drawText(text, centerX - (mTextPaint.measureText(text)/2f), centerY - 45f, mTextPaint);
                canvas.drawText(dateText, centerX - dateTextPaint.measureText(dateText)/2f, centerY - 5f, dateTextPaint);
                if (isInAmbientMode()){
                    canvas.drawLine(centerX - 25f, centerY + 31f, centerX + 25f, centerY + 31f, linePaint);
                    canvas.drawText(formattedHighTemp, centerX - 50f, centerY + 90f, highTemptTextPaint);
                    canvas.drawText(formattedLowTemp, centerX + 10f, centerY + 90f, lowTempTextPaint);
                }else{
                    canvas.drawLine(centerX - 25f, centerY + 31f, centerX + 25f, centerY + 31f, linePaint);
                    canvas.drawText(formattedHighTemp, centerX - 15f, centerY + 90f, highTemptTextPaint);
                    canvas.drawText(formattedLowTemp, centerX + 45f, centerY + 90f, lowTempTextPaint);
                    Bitmap bitmap;
                    if (mIconId != -1) {
                        bitmap = BitmapFactory.decodeResource(getResources(), mIconId);
                        canvas.drawBitmap(bitmap, centerX - 95f, centerY + 50f, null);
                    }
                }


            } else {
                canvas.drawText(text, centerX - (mTextPaint.measureText(text)/2f), centerY - 60f, mTextPaint);
                canvas.drawText(dateText, centerX - dateTextPaint.measureText(dateText)/2f, centerY - 20f, dateTextPaint);
                if (isInAmbientMode()){
                    canvas.drawLine(centerX - 25f, centerY + 16f, centerX + 25f, centerY + 16f, linePaint);
                    canvas.drawText(formattedHighTemp, centerX - 50f, centerY + 75f, highTemptTextPaint);
                    canvas.drawText(formattedLowTemp, centerX + 10f, centerY + 75f, lowTempTextPaint);
                }else{
                    canvas.drawLine(0, centerY, bounds.width(), centerY, linePaint);
                    canvas.drawText(formattedHighTemp, centerX - 15f, centerY + 75f, highTemptTextPaint);
                    canvas.drawText(formattedLowTemp, centerX + 45f, centerY + 75f, lowTempTextPaint);
                    Bitmap bitmap;
                    if (mIconId != -1) {
                        bitmap = BitmapFactory.decodeResource(getResources(), mIconId);
                        canvas.drawBitmap(bitmap, centerX - 95f, centerY + 35f, null);
                    }
                }

            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
//            Log.e(LOG_TAG, "UpdateTimer");
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


        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    String path = item.getUri().getPath();
                    if (path.equals("/wearable_data")) {
                        for (String key : dataMap.keySet()) {
                            if (!dataMap.containsKey(key)) {
                                continue;
                            }
                            switch (key) {

                                case "wearable_id":
                                    mConditionId = dataMap.getInt(key);
                                    mIconId = Utilities.getDrawableIdForWeatherCondition(mConditionId);;
                                    break;
                                case "wearable_max":
                                    mHighTemp = dataMap.getDouble(key);
                                    break;
                                case "wearable_min":
                                    mLowTemp = dataMap.getDouble(key);
                                    break;
                            }
                        }
                        invalidate();
                    }
                }
                }
            }

        }
    }

