package com.rotmeter.app

import com.rotmeter.app.util.RelativeTime
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = LocalDateTime.of(2026, 10, 6, 12, 0, 0)

    @Test fun relativeTimeUsesWholeMinuteHourAndDayBoundaries() {
        assertEquals("Just now", RelativeTime.format("2026-10-06 11:59:01", now))
        assertEquals("1m ago", RelativeTime.format("2026-10-06 11:59:00", now))
        assertEquals("59m ago", RelativeTime.format("2026-10-06 11:00:01", now))
        assertEquals("1h ago", RelativeTime.format("2026-10-06 11:00:00", now))
        assertEquals("2h ago", RelativeTime.format("2026-10-06 09:59:00", now))
        assertEquals("1d ago", RelativeTime.format("2026-10-05 12:00:00", now))
        assertEquals("365d ago", RelativeTime.format("2025-10-06 12:00:00", now))
    }

    @Test fun clockChangesAndUnexpectedTimestampsDoNotCrashRows() {
        assertEquals("Just now", RelativeTime.format("2026-10-07 12:00:00", now))
        assertEquals("unknown", RelativeTime.format("unknown", now))
        assertEquals("", RelativeTime.format("", now))
    }
}
