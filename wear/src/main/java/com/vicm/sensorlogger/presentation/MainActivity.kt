package com.vicm.sensorlogger.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.vicm.sensorlogger.SensorService
import com.vicm.sensorlogger.presentation.theme.SensorLoggerTheme

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private var isRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(isRunning) {
                toggleService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        updateScreenOn()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    private fun updateScreenOn() {
        if (isRunning) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/collection_state") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    isRunning = dataMap.getBoolean("is_collecting")
                    updateScreenOn()
                }
            }
        }
    }

    private fun toggleService() {
        val intent = Intent(this, SensorService::class.java)
        if (isRunning) {
            stopService(intent)
        } else {
            startForegroundService(intent)
        }
        // Optimistic update, though listener will confirm
        // isRunning = !isRunning 
    }
}

@Composable
fun WearApp(isRunning: Boolean, onToggle: () -> Unit) {
    SensorLoggerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            MainScreen(isRunning, onToggle)
        }
    }
}

@Composable
fun MainScreen(isRunning: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRunning) "Collecting Data..." else "Ready",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colors.onBackground
        )

        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.primaryButtonColors(
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            )
        ) {
            Text(text = if (isRunning) "Stop" else "Start")
        }
    }
}