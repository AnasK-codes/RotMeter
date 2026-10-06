package com.rotmeter.app.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.util.DateUtils
import java.util.Calendar

object UsageSync {
    /** Blocking: caller supplies a background thread. Returns -1 when usage access is missing. */
    fun syncToday(context: Context, repo: RotRepository): Int {
        if (!UsagePermission.hasAccess(context)) return -1
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return 0
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val stats = try {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfToday.timeInMillis, now)
                ?: emptyList()
        } catch (_: SecurityException) {
            return -1
        }
        // Access can be revoked between the initial check and the platform query.
        if (!UsagePermission.hasAccess(context)) return -1

        val trackedPackages = repo.getAllApps().map { it.packageName }.toSet()
        val foregroundMillis = mutableMapOf<String, Long>()
        for (stat in stats) {
            val packageName = stat.packageName
            if (packageName in trackedPackages) {
                foregroundMillis[packageName] =
                    (foregroundMillis[packageName] ?: 0L) + stat.totalTimeInForeground
            }
        }
        val date = startOfToday.toInstant().atZone(startOfToday.timeZone.toZoneId())
            .toLocalDate().format(DateUtils.formatter)
        for ((packageName, milliseconds) in foregroundMillis) {
            repo.upsertUsage(date, packageName, (milliseconds / 60_000L).toInt())
        }
        return foregroundMillis.size
    }
}
