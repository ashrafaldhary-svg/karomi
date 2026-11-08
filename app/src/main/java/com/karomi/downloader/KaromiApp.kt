package com.karomi.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import com.karomi.downloader.data.AppDatabase

class KaromiApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        applyDarkModePreference()
        createDownloadChannel()
    }

    /** تطبيق الوضع الليلي المحفوظ من الإعدادات */
    private fun applyDarkModePreference() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val dark = prefs.getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
    }

    private fun createDownloadChannel() {
        val channel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.download_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val PREFS = "karomi_settings"
        const val KEY_DARK_MODE = "dark_mode"
        const val DOWNLOAD_CHANNEL_ID = "downloads"
    }
}
