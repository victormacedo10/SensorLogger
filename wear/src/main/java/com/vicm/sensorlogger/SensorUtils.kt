package com.vicm.sensorlogger

object SensorUtils {
    fun getCategory(value: Float): Int {
        return when {
            value < -4f -> 0  // LOW
            value > 4f -> 2   // HIGH
            else -> 1         // MID
        }
    }

    fun calculateStd(values: List<Float>, mean: Float): Float {
        if (values.isEmpty()) return 0f
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }
}
