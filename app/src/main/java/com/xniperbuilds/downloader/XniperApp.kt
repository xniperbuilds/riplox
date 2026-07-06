package com.xniperbuilds.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

/**
 * App-level class. Engine init + notification channel.
 * NOTE: engine auto-update yahan NAHI hota (share-popup ke waqt download se takrata tha) —
 * wo MainActivity kholne par hota hai (din me ek dafa).
 */
class XniperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.ensureV3Defaults(this)
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Aria2c.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            Log.e("XniperApp", "Engine init failed", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "downloads"
    }
}
