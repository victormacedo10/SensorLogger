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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.wear.compose.material.Icon
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private var isRunning by mutableStateOf(false)
    private var isProcessingFile by mutableStateOf(false)
    private var processingError by mutableStateOf<String?>(null)

    private val simulationReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                com.vicm.sensorlogger.FileSimulationService.ACTION_SIMULATION_STARTED -> {
                    isProcessingFile = true
                    processingError = null
                }
                com.vicm.sensorlogger.FileSimulationService.ACTION_SIMULATION_ENDED -> {
                    isProcessingFile = false
                    val error = intent.getStringExtra(com.vicm.sensorlogger.FileSimulationService.EXTRA_ERROR)
                    if (error != null) {
                        processingError = error
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load user metrics on startup
        SensorService.loadUserMetrics(this)

        val filter = android.content.IntentFilter().apply {
            addAction(com.vicm.sensorlogger.FileSimulationService.ACTION_SIMULATION_STARTED)
            addAction(com.vicm.sensorlogger.FileSimulationService.ACTION_SIMULATION_ENDED)
        }
        registerReceiver(simulationReceiver, filter, RECEIVER_NOT_EXPORTED)
        
        setContent {
            WearApp(
                isRunning = isRunning,
                isProcessingFile = isProcessingFile,
                processingError = processingError,
                onToggleService = { toggleService() },
                onOpenFile = { startFileSimulation() },
                onErrorDismiss = { processingError = null }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(simulationReceiver)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        
        // Sync state with service
        isProcessingFile = com.vicm.sensorlogger.FileSimulationService.isServiceRunning
        
        updateScreenOn()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    private fun updateScreenOn() {
        if (isRunning || isProcessingFile) {
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
        if (isProcessingFile) return // Prevent if simulation is running
        val intent = Intent(this, SensorService::class.java)
        if (isRunning) {
            stopService(intent)
        } else {
            startForegroundService(intent)
        }
        // isRunning will be updated via DataLayer callback or we can optimistically toggle it
        // But SensorService updates it via DataLayer, so we wait for that. 
        // However, for immediate UI feedback, we might want to toggle. 
        // The original code toggled it manually: isRunning = !isRunning
        // We'll keep that.
        isRunning = !isRunning 
    }

    private fun startFileSimulation() {
        if (isRunning) return // Prevent if collection is running
        if (isProcessingFile) return
        
        isProcessingFile = true
        val intent = Intent(this, com.vicm.sensorlogger.FileSimulationService::class.java)
        startService(intent)
    }
}

@Composable
fun WearApp(
    isRunning: Boolean, 
    isProcessingFile: Boolean,
    processingError: String?,
    onToggleService: () -> Unit,
    onOpenFile: () -> Unit,
    onErrorDismiss: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    SensorLoggerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            
            if (showSettings) {
                SettingsScreen(
                    onBack = { showSettings = false }
                )
            } else {
                MainScreen(
                    isRunning = isRunning, 
                    isProcessingFile = isProcessingFile,
                    onToggle = onToggleService,
                    onOpenFile = onOpenFile,
                    onSettingsClick = { showSettings = true }
                )
            }

            if (processingError != null) {
                androidx.wear.compose.material.dialog.Alert(
                    title = { Text("Error") },
                    content = { Text(processingError) },
                    negativeButton = {},
                    positiveButton = { 
                        Button(onClick = onErrorDismiss) { Text("OK") } 
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    isRunning: Boolean, 
    isProcessingFile: Boolean,
    onToggle: () -> Unit,
    onOpenFile: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onSettingsClick,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
            enabled = !isRunning && !isProcessingFile
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings"
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(0.75f),
            colors = ButtonDefaults.primaryButtonColors(
                backgroundColor = if (isRunning) MaterialTheme.colors.error else MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            ),
            enabled = !isProcessingFile
        ) {
            Text(text = if (isRunning) "Stop" else "Start")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onOpenFile,
            modifier = Modifier.fillMaxWidth(0.75f),
            colors = ButtonDefaults.secondaryButtonColors(),
            enabled = !isRunning && !isProcessingFile
        ) {
            Text(text = if (isProcessingFile) "Running..." else "Open File")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                isRunning -> "Collecting..."
                isProcessingFile -> "Processing..."
                else -> "Ready"
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.body1
        )
    }
}