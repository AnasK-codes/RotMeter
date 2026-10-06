package com.rotmeter.app.db

data class DailyRot(
    val totalMinutes: Int,
    val rotScore: Int,
    val productiveMinutes: Double
)

data class AppUsage(
    val packageName: String,
    val label: String,
    val minutes: Int,
    val rotWeight: Double
)

data class DayScore(
    val date: String,
    val rotScore: Int
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val categoryId: Long,
    val categoryName: String,
    val rotWeight: Double,
    val dailyLimitMin: Int?,
    val todayMinutes: Int = 0
)

data class AlertItem(
    val id: Long,
    val alertDate: String,
    val packageName: String,
    val label: String,
    val message: String,
    val createdAt: String,
    val notified: Boolean
)

data class GamificationDay(
    val date: String,
    val totalMinutes: Int,
    val rotScore: Int,
    val productiveMinutes: Double,
    val alertCount: Int,
    val averageRotScore: Double
)

data class TopAppLimit(val app: AppUsage, val dailyLimitMin: Int?)

data class BlockingLimit(val label: String, val minutes: Int)
