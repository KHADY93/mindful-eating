package io.github.khady93.mindfuleating.sync

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives sync updates pushed from the other device (watch <-> phone) via the Wearable Data
 * Layer API and routes them to the matching sync object. Declared in both app manifests.
 */
class DataLayerListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            val item = event.dataItem
            when (item.uri.path) {
                SettingsSync.PATH -> SettingsSync.onRemoteDataChanged(applicationContext, item)
                HistorySync.PATH -> HistorySync.onRemoteDataChanged(applicationContext, item)
            }
        }
        dataEvents.release()
    }
}
