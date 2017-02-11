package com.example.android.sunshine.sync;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by scott on 2/11/2017.
 */

public class WearableRequestForDataListenerService extends WearableListenerService {
//    private static final String LOG_TAG = "DataListenerService";


    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                String path = item.getUri().getPath();
                if (path.equals("/data_request")) {
                    for (String key : dataMap.keySet()) {
                        if (!dataMap.containsKey(key)) {
                            continue;
                        }
                        switch (key) {

                            case "data_request":

                                SunshineSyncUtils.startImmediateSync(getApplicationContext());
                                break;

                        }
                    }

                }
            }
        }
    }
}
