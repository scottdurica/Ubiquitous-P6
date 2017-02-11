package com.example.android.sunshine.sync;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by scott on 2/8/2017.
 */

public class WearableSyncSender implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    public static final String TAG = WearableSyncSender.class.getSimpleName();

    private static WearableSyncSender mWearableSyncSender;
    private PutDataMapRequest mPutDataMapRequest;
    private Context mContext;
    GoogleApiClient mGoogleApiClient;

    //private constructor to force use of getInstance()
    private WearableSyncSender(){};

    public static synchronized WearableSyncSender getInstance(){
        if (mWearableSyncSender == null){
            mWearableSyncSender = new WearableSyncSender();
        }
        return mWearableSyncSender;
    }

    public void setupClient(Context context, PutDataMapRequest putDataMapRequest) {
        mPutDataMapRequest = putDataMapRequest;
        mContext = context;

        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        if (!mGoogleApiClient.isConnected()){
            mGoogleApiClient.connect();
        }
    }

    public void sendDataToWearable() {
        PutDataRequest putDataRequest = mPutDataMapRequest.asPutDataRequest().setUrgent();

        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest).
                setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(final DataApi.DataItemResult result) {
                        if(result.getStatus().isSuccess()) {
                            Log.d(TAG, "Data sent " + result.getDataItem().getUri());
                        }
                    }
                });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "Client has connected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended");

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed");

    }
}
