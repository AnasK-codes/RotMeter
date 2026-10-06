package com.rotmeter.app

import com.rotmeter.app.blocking.BlockingPolicy
import com.rotmeter.app.blocking.ForegroundUsageAccumulator
import org.junit.Assert.*
import org.junit.Test

class BlockingPolicyTest {
    private val minute = 60_000L
    private val today = 24 * 60 * minute
    private val target = "social.app"

    @Test fun onlyEnabledSelectedAppsWithAValidReachedLimitAreBlocked() {
        assertFalse(BlockingPolicy.shouldBlock(false, true, 5, 100))
        assertFalse(BlockingPolicy.shouldBlock(true, false, 5, 100))
        assertFalse(BlockingPolicy.shouldBlock(true, true, null, 100))
        assertFalse(BlockingPolicy.shouldBlock(true, true, 0, 100))
        assertFalse(BlockingPolicy.shouldBlock(true, true, 5, 4))
        assertTrue(BlockingPolicy.shouldBlock(true, true, 5, 5))
        assertTrue(BlockingPolicy.shouldBlock(true, true, 5, 6))
        assertFalse(BlockingPolicy.shouldBlock(false, true, 5, 6))
    }

    @Test fun runningSessionsIncludeElapsedTimeAndRoundDown() {
        val usage = ForegroundUsageAccumulator(target, today, today + 5 * minute + 59_999)
        usage.resume(target, "Main", today)
        assertEquals(5L, usage.minutes())
        assertEquals(target, usage.foregroundPackage)
    }

    @Test fun overnightSessionsCountOnlyTodaysMinutes() {
        val usage = ForegroundUsageAccumulator(target, today, today + 10 * minute)
        usage.resume(target, "Main", today - 10 * minute)
        assertEquals(10L, usage.minutes())
        val oldDay = ForegroundUsageAccumulator(target, today, today + 10 * minute)
        oldDay.resume(target, "Main", today - 10 * minute)
        oldDay.pause(target, "Main", today - minute)
        assertEquals(0L, oldDay.minutes())
    }

    @Test fun duplicateResumesAndActivityTransitionsDoNotLoseOrDoubleCountTime() {
        val usage = ForegroundUsageAccumulator(target, today, today + 10 * minute)
        usage.resume(target, "Main", today)
        usage.resume(target, "Main", today + minute)
        usage.resume(target, "Detail", today + 5 * minute)
        usage.pause(target, "Main", today + 6 * minute)
        assertEquals(target, usage.foregroundPackage)
        assertEquals(10L, usage.minutes())
        usage.pause(target, "Detail", today + 8 * minute)
        assertNull(usage.foregroundPackage)
        assertEquals(8L, usage.minutes())
    }

    @Test fun otherAppsAndTheirBackgroundEventsCannotKeepCountingTheTarget() {
        val usage = ForegroundUsageAccumulator(target, today, today + 10 * minute)
        usage.resume(target, "Main", today)
        usage.resume("other.app", "Main", today + 3 * minute)
        usage.pause(target, "Main", today + 4 * minute)
        assertEquals("other.app", usage.foregroundPackage)
        assertEquals(3L, usage.minutes())
        usage.resume(target, "Main", today + 8 * minute)
        assertEquals(5L, usage.minutes())
    }

    @Test fun screenLockEndsUsageInsteadOfContinuingToChargeMinutes() {
        val usage = ForegroundUsageAccumulator(target, today, today + 10 * minute)
        usage.resume(target, "Main", today)
        usage.screenOff(today + 2 * minute)
        assertNull(usage.foregroundPackage)
        assertEquals(2L, usage.minutes())
    }
}
