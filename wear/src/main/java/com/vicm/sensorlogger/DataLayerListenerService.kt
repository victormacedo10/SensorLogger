package com.vicm.sensorlogger

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class DataLayerListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d("DataLayerListener", "Message received: ${messageEvent.path}")
        when (messageEvent.path) {
            "/start_collection" -> {
                val intent = Intent(this, SensorService::class.java)
                startForegroundService(intent)
            }
            "/stop_collection" -> {
                val intent = Intent(this, SensorService::class.java)
                stopService(intent)
            }
        }
    }
}
