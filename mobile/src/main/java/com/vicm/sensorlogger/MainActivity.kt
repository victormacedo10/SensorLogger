package com.vicm.sensorlogger

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    // TextViews for each metric
    private lateinit var tvXMean: TextView
    private lateinit var tvXMin: TextView
    private lateinit var tvXMax: TextView
    private lateinit var tvXStd: TextView
    private lateinit var tvYMean: TextView
    private lateinit var tvYMin: TextView
    private lateinit var tvYMax: TextView
    private lateinit var tvYStd: TextView
    private lateinit var tvZMean: TextView
    private lateinit var tvZMin: TextView
    private lateinit var tvZMax: TextView
    private lateinit var tvZStd: TextView
    
    // Charts for each metric
    private lateinit var chartXMean: ScatterChart
    private lateinit var chartXMin: ScatterChart
    private lateinit var chartXMax: ScatterChart
    private lateinit var chartXStd: ScatterChart
    private lateinit var chartYMean: ScatterChart
    private lateinit var chartYMin: ScatterChart
    private lateinit var chartYMax: ScatterChart
    private lateinit var chartYStd: ScatterChart
    private lateinit var chartZMean: ScatterChart
    private lateinit var chartZMin: ScatterChart
    private lateinit var chartZMax: ScatterChart
    private lateinit var chartZStd: ScatterChart
    
    private lateinit var btnToggle: Button


    
    private var timeIndex = 0f
    private var isCollecting = false
    
    // Store entries for each chart
    private val chartEntries = mutableMapOf<ScatterChart, ArrayList<Pair<Entry, Int>>>()
    
    // Color mapping for categories
    private val colorLow = Color.rgb(255, 152, 0)   // Orange
    private val colorMid = Color.rgb(76, 175, 80)   // Green
    private val colorHigh = Color.rgb(33, 150, 243) // Blue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize TextViews
        tvXMean = findViewById(R.id.tvXMean)
        tvXMin = findViewById(R.id.tvXMin)
        tvXMax = findViewById(R.id.tvXMax)
        tvXStd = findViewById(R.id.tvXStd)
        tvYMean = findViewById(R.id.tvYMean)
        tvYMin = findViewById(R.id.tvYMin)
        tvYMax = findViewById(R.id.tvYMax)
        tvYStd = findViewById(R.id.tvYStd)
        tvZMean = findViewById(R.id.tvZMean)
        tvZMin = findViewById(R.id.tvZMin)
        tvZMax = findViewById(R.id.tvZMax)
        tvZStd = findViewById(R.id.tvZStd)
        
        // Initialize Charts
        chartXMean = findViewById(R.id.chartXMean)
        chartXMin = findViewById(R.id.chartXMin)
        chartXMax = findViewById(R.id.chartXMax)
        chartXStd = findViewById(R.id.chartXStd)
        chartYMean = findViewById(R.id.chartYMean)
        chartYMin = findViewById(R.id.chartYMin)
        chartYMax = findViewById(R.id.chartYMax)
        chartYStd = findViewById(R.id.chartYStd)
        chartZMean = findViewById(R.id.chartZMean)
        chartZMin = findViewById(R.id.chartZMin)
        chartZMax = findViewById(R.id.chartZMax)
        chartZStd = findViewById(R.id.chartZStd)
        
        btnToggle = findViewById(R.id.btnToggle)

        // Setup all charts
        setupChart(chartXMean)
        setupChart(chartXMin)
        setupChart(chartXMax)
        setupChart(chartXStd)
        setupChart(chartYMean)
        setupChart(chartYMin)
        setupChart(chartYMax)
        setupChart(chartYStd)
        setupChart(chartZMean)
        setupChart(chartZMin)
        setupChart(chartZMax)
        setupChart(chartZStd)

        btnToggle.setOnClickListener {
            if (isCollecting) {
                sendCommand("/stop_collection")
            } else {
                sendCommand("/start_collection")
            }
        }
        
        updateButtonState()
    }

    private fun setupChart(chart: ScatterChart) {
        // Remove description text
        chart.description.isEnabled = false
        
        // Configure interaction
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setDrawGridBackground(false)
        chart.setPinchZoom(true)
        chart.setBackgroundColor(Color.WHITE)
        
        // Remove legend
        chart.legend.isEnabled = false
        
        // Configure X axis - only show bottom
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(true)
        
        // Configure Y axis - only show left
        chart.axisLeft.setDrawGridLines(true)
        chart.axisRight.isEnabled = false
    }

    private fun sendCommand(path: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
                for (node in nodes) {
                    Wearable.getMessageClient(this@MainActivity).sendMessage(node.id, path, null).await()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to send command", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/sensor_data") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val xMean = dataMap.getFloat("mean_x")
                    val yMean = dataMap.getFloat("mean_y")
                    val zMean = dataMap.getFloat("mean_z")
                    val xMin = dataMap.getFloat("min_x")
                    val yMin = dataMap.getFloat("min_y")
                    val zMin = dataMap.getFloat("min_z")
                    val xMax = dataMap.getFloat("max_x")
                    val yMax = dataMap.getFloat("max_y")
                    val zMax = dataMap.getFloat("max_z")
                    val xStd = dataMap.getFloat("std_x")
                    val yStd = dataMap.getFloat("std_y")
                    val zStd = dataMap.getFloat("std_z")
                    
                    // Get categories
                    val catXMean = dataMap.getInt("cat_mean_x")
                    val catYMean = dataMap.getInt("cat_mean_y")
                    val catZMean = dataMap.getInt("cat_mean_z")
                    val catXMin = dataMap.getInt("cat_min_x")
                    val catYMin = dataMap.getInt("cat_min_y")
                    val catZMin = dataMap.getInt("cat_min_z")
                    val catXMax = dataMap.getInt("cat_max_x")
                    val catYMax = dataMap.getInt("cat_max_y")
                    val catZMax = dataMap.getInt("cat_max_z")
                    val catXStd = dataMap.getInt("cat_std_x")
                    val catYStd = dataMap.getInt("cat_std_y")
                    val catZStd = dataMap.getInt("cat_std_z")
                    
                    updateCharts(
                        xMean, xMin, xMax, xStd,
                        yMean, yMin, yMax, yStd,
                        zMean, zMin, zMax, zStd,
                        catXMean, catXMin, catXMax, catXStd,
                        catYMean, catYMin, catYMax, catYStd,
                        catZMean, catZMin, catZMax, catZStd
                    )
                } else if (path == "/collection_state") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val wasCollecting = isCollecting
                    isCollecting = dataMap.getBoolean("is_collecting")
                    runOnUiThread {
                        updateButtonState()
                        // If we just started collecting, clear everything
                        if (!wasCollecting && isCollecting) {
                            resetCharts()
                        }
                        // If we just stopped collecting, reset x-axis to show full range and show means
                        else if (wasCollecting && !isCollecting) {
                            fitAllCharts()
                            showSessionMeans()
                        }
                    }
                }
            }
        }
    }

    private fun fitAllCharts() {
        chartXMean.fitScreen()
        chartXMin.fitScreen()
        chartXMax.fitScreen()
        chartXStd.fitScreen()
        chartYMean.fitScreen()
        chartYMin.fitScreen()
        chartYMax.fitScreen()
        chartYStd.fitScreen()
        chartZMean.fitScreen()
        chartZMin.fitScreen()
        chartZMax.fitScreen()
        chartZStd.fitScreen()
    }

    private fun updateButtonState() {
        btnToggle.text = if (isCollecting) "Stop Collection" else "Start Collection"
    }

    private fun updateCharts(
        xMean: Float, xMin: Float, xMax: Float, xStd: Float,
        yMean: Float, yMin: Float, yMax: Float, yStd: Float,
        zMean: Float, zMin: Float, zMax: Float, zStd: Float,
        catXMean: Int, catXMin: Int, catXMax: Int, catXStd: Int,
        catYMean: Int, catYMin: Int, catYMax: Int, catYStd: Int,
        catZMean: Int, catZMin: Int, catZMax: Int, catZStd: Int
    ) {
        runOnUiThread {
            // Update TextViews
            tvXMean.text = "Mean: $xMean"
            tvXMin.text = "Min: $xMin"
            tvXMax.text = "Max: $xMax"
            tvXStd.text = "Std: $xStd"
            tvYMean.text = "Mean: $yMean"
            tvYMin.text = "Min: $yMin"
            tvYMax.text = "Max: $yMax"
            tvYStd.text = "Std: $yStd"
            tvZMean.text = "Mean: $zMean"
            tvZMin.text = "Min: $zMin"
            tvZMax.text = "Max: $zMax"
            tvZStd.text = "Std: $zStd"

            // Update Charts with categories from Wear
            addEntry(chartXMean, xMean, catXMean)
            addEntry(chartXMin, xMin, catXMin)
            addEntry(chartXMax, xMax, catXMax)
            addEntry(chartXStd, xStd, catXStd)
            
            addEntry(chartYMean, yMean, catYMean)
            addEntry(chartYMin, yMin, catYMin)
            addEntry(chartYMax, yMax, catYMax)
            addEntry(chartYStd, yStd, catYStd)
            
            addEntry(chartZMean, zMean, catZMean)
            addEntry(chartZMin, zMin, catZMin)
            addEntry(chartZMax, zMax, catZMax)
            addEntry(chartZStd, zStd, catZStd)

            timeIndex += 1f
        }
    }

    private fun addEntry(chart: ScatterChart, value: Float, category: Int) {
        // Get or create entry list for this chart
        val entries = chartEntries.getOrPut(chart) { ArrayList() }
        
        // Add new entry with its category
        entries.add(Pair(Entry(timeIndex, value), category))
        
        // Split entries into three lists based on category
        val lowEntries = ArrayList<Entry>()
        val midEntries = ArrayList<Entry>()
        val highEntries = ArrayList<Entry>()
        
        for ((entry, cat) in entries) {
            when (cat) {
                0 -> lowEntries.add(entry)
                2 -> highEntries.add(entry)
                else -> midEntries.add(entry)
            }
        }
        
        // Create datasets for each category
        val dataSetLow = createDataSet(lowEntries, "Low", colorLow)
        val dataSetMid = createDataSet(midEntries, "Mid", colorMid)
        val dataSetHigh = createDataSet(highEntries, "High", colorHigh)
        
        // Combine into ScatterData
        val scatterData = ScatterData(dataSetLow, dataSetMid, dataSetHigh)
        chart.data = scatterData
        
        // If collecting, show only last 20 seconds
        if (isCollecting && entries.size > 20) {
            chart.setVisibleXRangeMaximum(20f)
            chart.moveViewToX(timeIndex)
        }
        
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private fun createDataSet(entries: ArrayList<Entry>, label: String, color: Int): ScatterDataSet {
        val dataSet = ScatterDataSet(entries, label)
        dataSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE)
        dataSet.scatterShapeSize = 8f
        dataSet.setDrawValues(false)
        dataSet.color = color
        return dataSet
    }

    private fun getValueCategory(value: Float): Int {
        return when {
            value < -4f -> 0  // LOW
            value > 4f -> 2   // HIGH
            else -> 1         // MID
        }
    }

    private fun getCategoryColor(category: Int): Int {
        return when (category) {
            0 -> colorLow    // Orange for LOW (< -4)
            2 -> colorHigh   // Blue for HIGH (> 4)
            else -> colorMid // Green for MID (-4 to 4)
        }
    }

    private fun resetCharts() {
        timeIndex = 0f
        chartEntries.clear()
        
        val charts = listOf(
            chartXMean, chartXMin, chartXMax, chartXStd,
            chartYMean, chartYMin, chartYMax, chartYStd,
            chartZMean, chartZMin, chartZMax, chartZStd
        )
        
        for (chart in charts) {
            chart.clear()
        }
    }

    private fun showSessionMeans() {
        tvXMean.text = "Avg Mean: %.2f".format(calculateChartMean(chartXMean))
        tvXMin.text = "Avg Min: %.2f".format(calculateChartMean(chartXMin))
        tvXMax.text = "Avg Max: %.2f".format(calculateChartMean(chartXMax))
        tvXStd.text = "Avg Std: %.2f".format(calculateChartMean(chartXStd))
        
        tvYMean.text = "Avg Mean: %.2f".format(calculateChartMean(chartYMean))
        tvYMin.text = "Avg Min: %.2f".format(calculateChartMean(chartYMin))
        tvYMax.text = "Avg Max: %.2f".format(calculateChartMean(chartYMax))
        tvYStd.text = "Avg Std: %.2f".format(calculateChartMean(chartYStd))
        
        tvZMean.text = "Avg Mean: %.2f".format(calculateChartMean(chartZMean))
        tvZMin.text = "Avg Min: %.2f".format(calculateChartMean(chartZMin))
        tvZMax.text = "Avg Max: %.2f".format(calculateChartMean(chartZMax))
        tvZStd.text = "Avg Std: %.2f".format(calculateChartMean(chartZStd))
    }

    private fun calculateChartMean(chart: ScatterChart): Float {
        val data = chart.data ?: return 0f
        if (data.dataSetCount == 0) return 0f
        
        var sum = 0f
        var count = 0
        
        for (i in 0 until data.dataSetCount) {
            val dataSet = data.getDataSetByIndex(i)
            for (j in 0 until dataSet.entryCount) {
                sum += dataSet.getEntryForIndex(j).y
                count++
            }
        }
        
        return if (count > 0) sum / count else 0f
    }
}