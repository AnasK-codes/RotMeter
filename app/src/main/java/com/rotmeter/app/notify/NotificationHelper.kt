package com.rotmeter.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rotmeter.app.R
import com.rotmeter.app.db.RotRepository

object NotificationHelper {
    // A versioned channel applies the custom default even on existing installations.
    const val CHANNEL_ID = "rot_alerts_custom_v1"
    private const val LEGACY_CHANNEL_ID = "rot_alerts"

    fun soundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/raw/fahhhhh")

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val legacy = manager.getNotificationChannel(LEGACY_CHANNEL_ID)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // Preserve a muted old channel and explicit user sound choices during migration.
        val preserveSound = legacy != null && (legacy.sound == null ||
            legacy.sound != Settings.System.DEFAULT_NOTIFICATION_URI ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && legacy.hasUserSetSound()))
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, context.getString(R.string.notification_custom_channel_name),
                legacy?.importance ?: NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setSound(if (preserveSound) legacy?.sound else soundUri(context),
                    if (preserveSound) legacy?.audioAttributes ?: audio else audio)
                legacy?.let {
                    enableVibration(it.shouldVibrate())
                    it.vibrationPattern?.let { pattern -> setVibrationPattern(pattern) }
                    enableLights(it.shouldShowLights())
                    lightColor = it.lightColor
                }
            }
        )
    }

    /** Returns false when notifications are blocked, so the alert can remain pending. */
    fun show(context: Context, id: Int, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return false
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alerts)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            // Permission may have been revoked after the check.
            false
        }
    }

    /** Blocking database work; call from a worker or executor. Serializes alert delivery. */
    @Synchronized
    fun notifyUnnotifiedAlerts(context: Context, repo: RotRepository) {
        for (alert in repo.getUnnotifiedAlerts()) {
            if (show(context, alert.id.toInt(), context.getString(R.string.app_name), alert.message)) {
                repo.markAlertNotified(alert.id)
            }
        }
    }
}
