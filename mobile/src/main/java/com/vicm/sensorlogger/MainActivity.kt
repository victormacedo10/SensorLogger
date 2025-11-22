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

    // Data entries for each metric
    private val xMeanEntries = ArrayList<Entry>()
    private val xMinEntries = ArrayList<Entry>()
    private val xMaxEntries = ArrayList<Entry>()
    private val xStdEntries = ArrayList<Entry>()
    private val yMeanEntries = ArrayList<Entry>()
    private val yMinEntries = ArrayList<Entry>()
    private val yMaxEntries = ArrayList<Entry>()
    private val yStdEntries = ArrayList<Entry>()
    private val zMeanEntries = ArrayList<Entry>()
    private val zMinEntries = ArrayList<Entry>()
    private val zMaxEntries = ArrayList<Entry>()
    private val zStdEntries = ArrayList<Entry>()
    
    // Category lists for each metric (to track colors)
    private val xMeanCategories = ArrayList<Int>()
    private val xMinCategories = ArrayList<Int>()
    private val xMaxCategories = ArrayList<Int>()
    private val xStdCategories = ArrayList<Int>()
    private val yMeanCategories = ArrayList<Int>()
    private val yMinCategories = ArrayList<Int>()
    private val yMaxCategories = ArrayList<Int>()
    private val yStdCategories = ArrayList<Int>()
    private val zMeanCategories = ArrayList<Int>()
    private val zMinCategories = ArrayList<Int>()
    private val zMaxCategories = ArrayList<Int>()
    private val zStdCategories = ArrayList<Int>()
    
    private var timeIndex = 0f
    private var isCollecting = false
    
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
                        // If we just stopped collecting, reset x-axis to show full range
                        if (wasCollecting && !isCollecting) {
                            fitAllCharts()
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

            // Update Charts with categories for color coding
            addEntry(chartXMean, xMeanEntries, xMean, xMeanCategories, catXMean)
            addEntry(chartXMin, xMinEntries, xMin, xMinCategories, catXMin)
            addEntry(chartXMax, xMaxEntries, xMax, xMaxCategories, catXMax)
            addEntry(chartXStd, xStdEntries, xStd, xStdCategories, catXStd)
            
            addEntry(chartYMean, yMeanEntries, yMean, yMeanCategories, catYMean)
            addEntry(chartYMin, yMinEntries, yMin, yMinCategories, catYMin)
            addEntry(chartYMax, yMaxEntries, yMax, yMaxCategories, catYMax)
            addEntry(chartYStd, yStdEntries, yStd, yStdCategories, catYStd)
            
            addEntry(chartZMean, zMeanEntries, zMean, zMeanCategories, catZMean)
            addEntry(chartZMin, zMinEntries, zMin, zMinCategories, catZMin)
            addEntry(chartZMax, zMaxEntries, zMax, zMaxCategories, catZMax)
            addEntry(chartZStd, zStdEntries, zStd, zStdCategories, catZStd)

            timeIndex += 1f
        }
    }

    private fun addEntry(chart: ScatterChart, entries: ArrayList<Entry>, value: Float, categories: ArrayList<Int>, category: Int) {
        entries.add(Entry(timeIndex, value))
        categories.add(category)
        
        // Build color array based on categories
        val colors = ArrayList<Int>()
        for (cat in categories) {
            colors.add(when (cat) {
                0 -> colorLow    // Orange
                2 -> colorHigh   // Blue
                else -> colorMid // Green (1)
            })
        }
        
        val dataSet = ScatterDataSet(entries, "Data")
        dataSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE)
        dataSet.scatterShapeSize = 8f
        dataSet.colors = colors  // Set per-point colors
        dataSet.setDrawValues(false) // Remove text labels on data points
        
        val scatterData = ScatterData(dataSet)
        chart.data = scatterData
        
        // If collecting, show only last 20 seconds (20 data points)
        if (isCollecting && entries.size > 20) {
            chart.setVisibleXRangeMaximum(20f)
            chart.moveViewToX(timeIndex)
        }
        
        chart.notifyDataSetChanged()
        chart.invalidate()
    }
}