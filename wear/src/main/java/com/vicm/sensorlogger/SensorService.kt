package com.vicm.sensorlogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val xValues = mutableListOf<Float>()
    private val yValues = mutableListOf<Float>()
    private val zValues = mutableListOf<Float>()

    private var sensorFile: java.io.File? = null
    private var metricsFile: java.io.File? = null
    private var sensorWriter: java.io.FileWriter? = null
    private var metricsWriter: java.io.FileWriter? = null
    private val sensorDataBuffer = StringBuilder()
    
    // Buffer for data transmission (max 60 seconds)
    private val transmissionBuffer = java.util.LinkedList<DataMap>()
    private val MAX_BUFFER_SIZE = 60

    private var isCollecting = false

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Acquire WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorLogger::WakeLock")
        wakeLock?.acquire()

        startForeground(1, createNotification())
        loadUserMetrics(this)
        startCollection()
    }

    private fun createNotification(): Notification {
        val channelId = "SensorLoggerChannel"
        val channel = NotificationChannel(channelId, "Sensor Logger", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sensor Logger")
            .setContentText("Collecting sensor data...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    private fun startCollection() {
        if (isCollecting) return
        isCollecting = true
        createFiles()
        publishState(true)

        val currentHeight = (userHeight * 10f).roundToInt() / 10f
        val currentWeight = (userWeight * 10f).roundToInt() / 10f

        accelerometer?.let {
            sensorManager.registerListener(this, it, 10000) // 10000 microseconds = 100Hz
        }
        
        serviceScope.launch {
            while (isActive && isCollecting) {
                delay(1000)
                processAndSendData()
            }
        }
    }

    private fun processAndSendData() {
        val xCopy: List<Float>
        val yCopy: List<Float>
        val zCopy: List<Float>
        
        synchronized(this) {
            if (xValues.isEmpty()) return
            
            xCopy = xValues.toList()
            yCopy = yValues.toList()
            zCopy = zValues.toList()
            
            xValues.clear()
            yValues.clear()
            zValues.clear()
        }

        // Compute statistics for each axis
        val meanX = xCopy.average().toFloat()
        val meanY = yCopy.average().toFloat()
        val meanZ = zCopy.average().toFloat()
        
        val minX = xCopy.minOrNull() ?: 0f
        val minY = yCopy.minOrNull() ?: 0f
        val minZ = zCopy.minOrNull() ?: 0f
        
        val maxX = xCopy.maxOrNull() ?: 0f
        val maxY = yCopy.maxOrNull() ?: 0f
        val maxZ = zCopy.maxOrNull() ?: 0f
        
        val stdX = calculateStd(xCopy, meanX)
        val stdY = calculateStd(yCopy, meanY)
        val stdZ = calculateStd(zCopy, meanZ)

        // Compute categories for each metric (0: LOW < -4, 1: MID, 2: HIGH > 4)
        val catMeanX = getCategory(meanX)
        val catMeanY = getCategory(meanY)
        val catMeanZ = getCategory(meanZ)
        val catMinX = getCategory(minX)
        val catMinY = getCategory(minY)
        val catMinZ = getCategory(minZ)
        val catMaxX = getCategory(maxX)
        val catMaxY = getCategory(maxY)
        val catMaxZ = getCategory(maxZ)
        val catStdX = getCategory(stdX)
        val catStdY = getCategory(stdY)
        val catStdZ = getCategory(stdZ)

        Log.d("SensorService", "Sending data - X: mean=$meanX, min=$minX, max=$maxX, std=$stdX")

        // Create DataMap for this second
        val dataMap = DataMap()
        dataMap.putFloat("mean_x", meanX)
        dataMap.putFloat("mean_y", meanY)
        dataMap.putFloat("mean_z", meanZ)
        dataMap.putFloat("min_x", minX)
        dataMap.putFloat("min_y", minY)
        dataMap.putFloat("min_z", minZ)
        dataMap.putFloat("max_x", maxX)
        dataMap.putFloat("max_y", maxY)
        dataMap.putFloat("max_z", maxZ)
        dataMap.putFloat("std_x", stdX)
        dataMap.putFloat("std_y", stdY)
        dataMap.putFloat("std_z", stdZ)
        
        // Add categories
        dataMap.putInt("cat_mean_x", catMeanX)
        dataMap.putInt("cat_mean_y", catMeanY)
        dataMap.putInt("cat_mean_z", catMeanZ)
        dataMap.putInt("cat_min_x", catMinX)
        dataMap.putInt("cat_min_y", catMinY)
        dataMap.putInt("cat_min_z", catMinZ)
        dataMap.putInt("cat_max_x", catMaxX)
        dataMap.putInt("cat_max_y", catMaxY)
        dataMap.putInt("cat_max_z", catMaxZ)
        dataMap.putInt("cat_std_x", catStdX)
        dataMap.putInt("cat_std_y", catStdY)
        dataMap.putInt("cat_std_z", catStdZ)
        
        dataMap.putLong("timestamp", System.currentTimeMillis())

        // Add to buffer
        transmissionBuffer.add(dataMap)
        while (transmissionBuffer.size > MAX_BUFFER_SIZE) {
            transmissionBuffer.removeFirst()
        }

        // Send the entire buffer
        val putDataMapReq = PutDataMapRequest.create("/sensor_data")
        putDataMapReq.dataMap.putDataMapArrayList("buffered_data", ArrayList(transmissionBuffer))
        putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis()) // Update timestamp to trigger change
        
        val putDataReq = putDataMapReq.asPutDataRequest()
        putDataReq.setUrgent()
        Wearable.getDataClient(this).putDataItem(putDataReq)

        // Write to files
        try {
            val sensorDataToWrite: String
            synchronized(this) {
                sensorDataToWrite = sensorDataBuffer.toString()
                sensorDataBuffer.setLength(0)
            }
            
            sensorWriter?.append(sensorDataToWrite)
            sensorWriter?.flush()

            metricsWriter?.append("${System.currentTimeMillis()},$meanX,$meanY,$meanZ,$minX,$minY,$minZ,$maxX,$maxY,$maxZ,$stdX,$stdY,$stdZ\n")
            metricsWriter?.flush()
        } catch (e: Exception) {
            Log.e("SensorService", "Error writing to file", e)
        }
    }
    
    private fun getCategory(value: Float): Int {
        return when {
            value < -4f -> 0  // LOW
            value > 4f -> 2   // HIGH
            else -> 1         // MID
        }
    }
    
    private fun calculateStd(values: List<Float>, mean: Float): Float {
        if (values.isEmpty()) return 0f
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }

    private fun publishState(collecting: Boolean) {
        val putDataMapReq = PutDataMapRequest.create("/collection_state")
        putDataMapReq.dataMap.putBoolean("is_collecting", collecting)
        putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
        val putDataReq = putDataMapReq.asPutDataRequest()
        putDataReq.setUrgent()
        Wearable.getDataClient(this).putDataItem(putDataReq)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                synchronized(this) {
                    xValues.add(it.values[0])
                    yValues.add(it.values[1])
                    zValues.add(it.values[2])
                    
                    sensorDataBuffer.append("${System.currentTimeMillis()},${it.values[0]},${it.values[1]},${it.values[2]}\n")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        isCollecting = false
        publishState(false)
        sensorManager.unregisterListener(this)
        serviceJob.cancel()
        
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        closeFiles()
        sendFilesToPhone()
    }

    private fun createFiles() {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val fileNameSensor = "${timeStamp}_sensor.csv"
        val fileNameMetrics = "${timeStamp}_metrics.csv"

        // Use app-specific external storage to avoid permission issues on Wear OS
        val root = getExternalFilesDir(null)
        val dir = java.io.File(root, "Documents/SENSOR_LOGGER")
        if (!dir.exists()) dir.mkdirs()

        sensorFile = java.io.File(dir, fileNameSensor)
        metricsFile = java.io.File(dir, fileNameMetrics)

        try {
            sensorWriter = java.io.FileWriter(sensorFile, true)
            metricsWriter = java.io.FileWriter(metricsFile, true)

            sensorWriter?.append("timestamp,acc_x,acc_y,acc_z\n")
            metricsWriter?.append("timestamp,mean_x,mean_y,mean_z,min_x,min_y,min_z,max_x,max_y,max_z,std_x,std_y,std_z\n")
        } catch (e: Exception) {
            Log.e("SensorService", "Error creating files", e)
        }
    }

    private fun closeFiles() {
        try {
            sensorWriter?.close()
            metricsWriter?.close()
        } catch (e: Exception) {
            Log.e("SensorService", "Error closing files", e)
        }
    }

    private fun sendFilesToPhone() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@SensorService).connectedNodes.await()
                nodes.firstOrNull()?.let { node ->
                    sensorFile?.let { file ->
                        sendFile(node.id, file, "/sensor")
                    }
                    metricsFile?.let { file ->
                        sendFile(node.id, file, "/metrics")
                    }
                }
            } catch (e: Exception) {
                Log.e("SensorService", "Error sending files", e)
            }
        }
    }

    private suspend fun sendFile(nodeId: String, file: java.io.File, pathPrefix: String) {
        if (!file.exists()) return
        val channelClient = Wearable.getChannelClient(this)
        val fullPath = "$pathPrefix/${file.name}"
        val channel = channelClient.openChannel(nodeId, fullPath).await()
        channelClient.sendFile(channel, android.net.Uri.fromFile(file)).await()
        channelClient.close(channel)
        Log.d("SensorService", "Sent file: ${file.name} to $nodeId")
    }

    companion object {
        var userHeight: Double = 170.0 // Default 170 cm in meters
        var userWeight: Double = 60.0 // Default 60 kg

        fun updateUserMetrics(context: Context, height: Double?, weight: Double?) {
            val prefs = com.vicm.sensorlogger.data.UserPreferences(context)
            if (height != null) {
                userHeight = height
                prefs.saveHeight(height)
            }
            if (weight != null) {
                userWeight = weight
                prefs.saveWeight(weight)
            }
            Log.d("SensorService", "Updated user metrics: Height=$userHeight, Weight=$userWeight")
        }
        
        fun loadUserMetrics(context: Context) {
            val prefs = com.vicm.sensorlogger.data.UserPreferences(context)
            userHeight = prefs.getHeight()
            userWeight = prefs.getWeight()
            Log.d("SensorService", "Loaded user metrics: Height=$userHeight, Weight=$userWeight")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
