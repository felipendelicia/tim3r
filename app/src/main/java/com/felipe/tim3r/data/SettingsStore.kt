package com.felipe.tim3r.data

import android.content.Context
import androidx.core.content.edit

data class AppSettings(
    val prepSeconds: Int = 10,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("tim3r_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        prepSeconds = preferences.getInt("prep_seconds", 10),
        soundEnabled = preferences.getBoolean("sound_enabled", true),
        vibrationEnabled = preferences.getBoolean("vibration_enabled", true),
        keepScreenOn = preferences.getBoolean("keep_screen_on", true),
    )

    fun save(settings: AppSettings) {
        preferences.edit {
            putInt("prep_seconds", settings.prepSeconds)
            putBoolean("sound_enabled", settings.soundEnabled)
            putBoolean("vibration_enabled", settings.vibrationEnabled)
            putBoolean("keep_screen_on", settings.keepScreenOn)
        }
    }
}
