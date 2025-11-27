package com.vicm.sensorlogger

import android.content.Context

object SettingsManager {
    private const val PREFS_NAME = "sensor_logger_prefs"
    private const val KEY_HEIGHT = "height"
    private const val KEY_WEIGHT = "weight"

    fun saveHeight(context: Context, height: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY_HEIGHT, height).apply()
    }

    fun getHeight(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_HEIGHT, 1.0f)
    }

    fun saveWeight(context: Context, weight: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY_WEIGHT, weight).apply()
    }

    fun getWeight(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_WEIGHT, 1.0f)
    }
}
