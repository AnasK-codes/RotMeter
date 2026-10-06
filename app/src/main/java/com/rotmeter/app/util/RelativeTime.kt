package com.rotmeter.app.util

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object RelativeTime {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun format(createdAt: String, now: LocalDateTime = LocalDateTime.now()): String {
        val created = try { LocalDateTime.parse(createdAt, formatter) }
            catch (_: DateTimeParseException) { return createdAt }
        // SQLite stores local wall-clock timestamps; use the same local clock here.
        val seconds = Duration.between(created, now).seconds.coerceAtLeast(0L)
        return when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }
}
