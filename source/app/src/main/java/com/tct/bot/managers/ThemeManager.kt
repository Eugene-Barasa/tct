package com.tct.bot.managers

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences("TCTBotPrefs", Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean("DARK_MODE", true)
        set(value) {
            prefs.edit().putBoolean("DARK_MODE", value).apply()
            applyTheme()
        }

    fun applyTheme() {
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
