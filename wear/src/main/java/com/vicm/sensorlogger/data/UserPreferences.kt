package com.vicm.sensorlogger.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun saveHeight(height: Double) {
        sharedPreferences.edit().putFloat("user_height", height.toFloat()).apply()
    }

    fun getHeight(): Double {
        return sharedPreferences.getFloat("user_height", 1.70f).toDouble()
    }

    fun saveWeight(weight: Double) {
        sharedPreferences.edit().putFloat("user_weight", weight.toFloat()).apply()
    }

    fun getWeight(): Double {
        return sharedPreferences.getFloat("user_weight", 60.0f).toDouble()
    }
}
