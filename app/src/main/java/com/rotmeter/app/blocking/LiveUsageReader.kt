package com.rotmeter.app.blocking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.rotmeter.app.usage.UsagePermission
import java.util.Calendar

object LiveUsageReader {
    data class Reading(val minutes: Long, val foregroundPackage: String?)

    /** Blocking platform queries. Reads real usage only; never seeds or writes usage_log. */
    @Suppress("DEPRECATION")
    fun read(context: Context, packageName: String, now: Long = System.currentTimeMillis(),
             operationAllowed: () -> Boolean = { true }): Reading? {
        if (!operationAllowed() || !UsagePermission.hasAccess(context)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val midnight = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = midnight.timeInMillis
        // Yesterday's events establish a session already running across midnight.
        val lookBack = (midnight.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        return try {
            val reportedMillis = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .orEmpty().filter { it.packageName == packageName }
                .sumOf { it.totalTimeInForeground.coerceAtLeast(0L) }
            if (!operationAllowed()) return null
            val events = manager.queryEvents(lookBack, now) ?: return null
            val accumulator = ForegroundUsageAccumulator(packageName, start, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                if (!operationAllowed()) return null
                events.getNextEvent(event)
                val pkg = event.packageName ?: ""
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> accumulator.resume(pkg, event.className.orEmpty(), event.timeStamp)
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> accumulator.pause(pkg, event.className.orEmpty(), event.timeStamp)
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) accumulator.screenOff(event.timeStamp)
                }
            }
            if (!operationAllowed() || !UsagePermission.hasAccess(context)) null else Reading(
                // The platform may already include the ongoing session. Do not add it twice.
                maxOf(reportedMillis / 60_000L, accumulator.minutes()), accumulator.foregroundPackage
            )
        } catch (_: SecurityException) { null }
    }
}
