package com.rotmeter.app.util

import com.rotmeter.app.db.RotRepository
import java.time.LocalDate
import kotlin.random.Random

object DemoData {
    /** Blocking: call on a background executor. Replaces usage, alerts, and credits. */
    fun seed(repo: RotRepository) {
        if (repo.getAllApps().none { it.dailyLimitMin != null }) {
            repo.setLimit("com.instagram.android", 60)
            repo.setLimit("com.google.android.youtube", 60)
            repo.setLimit("com.snapchat.android", 30)
            repo.setLimit("com.twitter.android", 30)
            repo.setLimit("com.reddit.frontpage", 45)
            repo.setLimit("com.netflix.mediaclient", 60)
        }

        repo.clearUsageData()
        val random = Random(42)
        // Anchor the whole seed to one date, even if execution crosses midnight.
        val today = DateUtils.todayString()
        val endDate = LocalDate.parse(today, DateUtils.formatter)
        for (daysBack in 13 downTo 0) {
            val date = endDate.minusDays(daysBack.toLong()).format(DateUtils.formatter)
            repo.upsertUsage(date, "com.instagram.android", random.nextInt(20, 111))
            repo.upsertUsage(date, "com.google.android.youtube", random.nextInt(15, 91))
            repo.upsertUsage(date, "com.snapchat.android", random.nextInt(0, 46))
            repo.upsertUsage(date, "com.twitter.android", random.nextInt(0, 41))
            repo.upsertUsage(date, "com.reddit.frontpage", random.nextInt(0, 61))
            repo.upsertUsage(date, "com.netflix.mediaclient", random.nextInt(0, 71))
            repo.upsertUsage(date, "com.supercell.clashofclans", random.nextInt(0, 51))
            repo.upsertUsage(date, "com.leetcode.app", random.nextInt(0, 91))
            repo.upsertUsage(date, "com.amazon.kindle", random.nextInt(0, 41))
            repo.upsertUsage(date, "com.duolingo", random.nextInt(0, 21))
        }

        repo.markOldAlertsNotified(today)
    }
}
