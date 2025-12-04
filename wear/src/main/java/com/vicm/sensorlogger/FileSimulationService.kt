package com.vicm.sensorlogger

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.LinkedList

class FileSimulationService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
        startSimulation()
        return START_NOT_STICKY
    }

    private fun startSimulation() {
        serviceScope.launch {
            try {
                sendBroadcast(Intent(ACTION_SIMULATION_STARTED).setPackage(packageName))
                
                val root = getExternalFilesDir(null)
                val dir = File(root, "Documents/SENSOR_LOGGER")
                
                if (!dir.exists() || !dir.isDirectory) {
                    Log.e(TAG, "Directory not found")
                    sendBroadcast(Intent(ACTION_SIMULATION_ENDED).setPackage(packageName).apply {
                        putExtra(EXTRA_ERROR, "No files found")
                    })
                    stopSelf()
                    return@launch
                }

                // Find last sensor file
                val lastSensorFile = dir.listFiles { file -> 
                    file.name.endsWith("_sensor.csv") 
                }?.maxByOrNull { it.lastModified() }

                if (lastSensorFile == null) {
                    Log.e(TAG, "No sensor file found")
                    sendBroadcast(Intent(ACTION_SIMULATION_ENDED).setPackage(packageName).apply {
                        putExtra(EXTRA_ERROR, "No sensor file found")
                    })
                    stopSelf()
                    return@launch
                }

                // Create new metrics file
                val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val metricsFile = File(dir, "${timeStamp}_metrics.csv")
                val metricsWriter = FileWriter(metricsFile)
                metricsWriter.append("timestamp,mean_x,mean_y,mean_z,min_x,min_y,min_z,max_x,max_y,max_z,std_x,std_y,std_z\n")

                // Read and process sensor file
                val reader = BufferedReader(FileReader(lastSensorFile))
                // Skip header
                reader.readLine()

                val xValues = mutableListOf<Float>()
                val yValues = mutableListOf<Float>()
                val zValues = mutableListOf<Float>()
                
                var line: String? = reader.readLine()
                var startTime = -1L
                
                // Buffer for 1 second of data (approx 100 samples)
                // Since we are simulating, we can just read 100 lines or group by timestamp if available.
                // The sensor file format is: timestamp,acc_x,acc_y,acc_z
                // We should group by 1 second windows based on timestamp.
                
                while (line != null) {
                    val parts = line.split(",")
                    if (parts.size >= 4) {
                        try {
                            val timestamp = parts[0].toLong()
                            val x = parts[1].toFloat()
                            val y = parts[2].toFloat()
                            val z = parts[3].toFloat()

                            if (startTime == -1L) startTime = timestamp

                            // Check if 1 second has passed
                            if (timestamp - startTime >= 1000) {
                                processWindow(xValues, yValues, zValues, metricsWriter)
                                startTime = timestamp
                            }

                            xValues.add(x)
                            yValues.add(y)
                            zValues.add(z)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing line: $line", e)
                        }
                    }
                    line = reader.readLine()
                }
                
                // Process remaining data
                processWindow(xValues, yValues, zValues, metricsWriter)
                
                reader.close()
                metricsWriter.flush()
                metricsWriter.close()

                // Send files
                sendFilesToPhone(lastSensorFile, metricsFile)
                
                sendBroadcast(Intent(ACTION_SIMULATION_ENDED).setPackage(packageName))
            } catch (e: Exception) {
                Log.e(TAG, "Error in simulation", e)
                sendBroadcast(Intent(ACTION_SIMULATION_ENDED).setPackage(packageName).apply {
                    putExtra(EXTRA_ERROR, e.message)
                })
            } finally {
                stopSelf()
            }
        }
    }

    private fun processWindow(
        xValues: MutableList<Float>,
        yValues: MutableList<Float>,
        zValues: MutableList<Float>,
        writer: FileWriter
    ) {
        if (xValues.isEmpty()) return

        val meanX = xValues.average().toFloat()
        val meanY = yValues.average().toFloat()
        val meanZ = zValues.average().toFloat()

        val minX = xValues.minOrNull() ?: 0f
        val minY = yValues.minOrNull() ?: 0f
        val minZ = zValues.minOrNull() ?: 0f

        val maxX = xValues.maxOrNull() ?: 0f
        val maxY = yValues.maxOrNull() ?: 0f
        val maxZ = zValues.maxOrNull() ?: 0f

        val stdX = SensorUtils.calculateStd(xValues, meanX)
        val stdY = SensorUtils.calculateStd(yValues, meanY)
        val stdZ = SensorUtils.calculateStd(zValues, meanZ)

        try {
            writer.append("${System.currentTimeMillis()},$meanX,$meanY,$meanZ,$minX,$minY,$minZ,$maxX,$maxY,$maxZ,$stdX,$stdY,$stdZ\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing metrics", e)
        }

        xValues.clear()
        yValues.clear()
        zValues.clear()
    }

    private suspend fun sendFilesToPhone(sensorFile: File, metricsFile: File) {
        try {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            nodes.firstOrNull()?.let { node ->
                sendFile(node.id, sensorFile, "/sensor")
                sendFile(node.id, metricsFile, "/metrics")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending files", e)
        }
    }

    private suspend fun sendFile(nodeId: String, file: File, pathPrefix: String) {
        if (!file.exists()) return
        val channelClient = Wearable.getChannelClient(this)
        val fullPath = "$pathPrefix/${file.name}"
        val channel = channelClient.openChannel(nodeId, fullPath).await()
        channelClient.sendFile(channel, android.net.Uri.fromFile(file)).await()
        channelClient.close(channel)
        Log.d(TAG, "Sent file: ${file.name} to $nodeId")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceJob.cancel()
    }

    companion object {
        const val TAG = "FileSimulationService"
        const val ACTION_SIMULATION_STARTED = "com.vicm.sensorlogger.SIMULATION_STARTED"
        const val ACTION_SIMULATION_ENDED = "com.vicm.sensorlogger.SIMULATION_ENDED"
        const val EXTRA_ERROR = "extra_error"
        var isServiceRunning = false
    }
}
