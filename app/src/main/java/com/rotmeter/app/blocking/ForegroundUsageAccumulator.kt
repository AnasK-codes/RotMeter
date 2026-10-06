package com.rotmeter.app.blocking

/** Reconstruct foreground intervals, including an ongoing session and midnight clipping. */
class ForegroundUsageAccumulator(
    private val targetPackage: String,
    private val startOfToday: Long,
    private val now: Long
) {
    var foregroundPackage: String? = null
        private set
    private val activities = mutableSetOf<String>()
    private var sessionStart = 0L
    private var completedMillis = 0L

    fun resume(packageName: String, activity: String, timestamp: Long) {
        if (foregroundPackage != packageName) {
            endSession(timestamp)
            foregroundPackage = packageName
            sessionStart = timestamp
        }
        activities.add(activity)
    }

    fun pause(packageName: String, activity: String, timestamp: Long) {
        if (foregroundPackage != packageName) return
        if (activity.isEmpty()) activities.clear() else activities.remove(activity)
        if (activities.isEmpty()) endSession(timestamp)
    }

    fun screenOff(timestamp: Long) = endSession(timestamp)

    private fun endSession(timestamp: Long) {
        if (foregroundPackage == targetPackage) {
            completedMillis += (minOf(timestamp, now) - maxOf(sessionStart, startOfToday)).coerceAtLeast(0)
        }
        foregroundPackage = null
        activities.clear()
    }

    fun minutes(): Long {
        val ongoing = if (foregroundPackage == targetPackage)
            (now - maxOf(sessionStart, startOfToday)).coerceAtLeast(0) else 0L
        return (completedMillis + ongoing) / 60_000L
    }
}
