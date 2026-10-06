package com.rotmeter.app

import com.rotmeter.app.db.AppUsage
import com.rotmeter.app.db.GamificationDay
import com.rotmeter.app.db.TopAppLimit
import com.rotmeter.app.util.Gamification
import org.junit.Assert.*
import org.junit.Test

class GamificationTest {
    private fun day(date: String, score: Int, productive: Double = 0.0, alerts: Int = 0) =
        GamificationDay(date, 100, score, productive, alerts, 0.0)

    private fun average(vararg days: GamificationDay): List<GamificationDay> {
        val mean = days.map { it.rotScore }.average()
        return days.map { it.copy(averageRotScore = mean) }
    }

    @Test fun emptyDataDoesNotInventXpOrBadges() {
        val state = Gamification.calculate(emptyList(), "2026-01-02", 0 to 0, null)
        assertEquals(0L, state.player.totalXp)
        assertEquals(1L, state.player.level)
        assertEquals(0, state.bestStreak)
        assertTrue(state.badges.none { it.unlocked })
        assertFalse(state.productiveQuest.complete)
        assertFalse(state.limitQuest.complete)
        assertTrue(state.roastQuest.complete)
    }

    @Test fun xpBonusesQuestsAndBadgeConditionsAreIndependent() {
        val days = average(day("2026-01-01", 0, 15.0), day("2026-01-02", 20, 30.0, 2))
        val top = TopAppLimit(AppUsage("app", "Example", 61, 1.0), 60)
        val state = Gamification.calculate(days, "2026-01-02", 10 to 20, top)
        assertEquals(170L, state.player.totalXp)
        assertEquals(0, state.currentStreak)
        assertEquals(1, state.bestStreak)
        assertTrue(state.productiveQuest.complete)
        assertEquals(100, state.productiveQuest.progress)
        assertFalse(state.limitQuest.complete)
        assertFalse(state.roastQuest.complete)
        assertTrue(state.badges.first { it.badge == Gamification.Badge.TOUCH_GRASS }.unlocked)
        assertTrue(state.badges.first { it.badge == Gamification.Badge.COMEBACK_KID }.unlocked)
    }

    @Test fun fractionalXpIsSummedBeforeRoundingDown() {
        val days = average(day("2026-01-01", 0, 1.4, 1), day("2026-01-02", 0, 1.4, 1))
        assertEquals(5L, Gamification.calculate(days, "2026-01-02", 0 to 0, null).player.totalXp)
    }

    @Test fun levelBoundariesAndTitlesUseTwoHundredXpSteps() {
        for ((xp, level, title) in listOf(
            Triple(199, 1, R.string.player_doomscroller),
            Triple(200, 2, R.string.player_doomscroller),
            Triple(400, 3, R.string.player_recovering),
            Triple(1000, 6, R.string.player_focused),
            Triple(1800, 10, R.string.player_monk)
        )) {
            val state = Gamification.calculate(average(day("2026-01-01", 0, xp / 2.0, 1)), "2026-01-01", 0 to 0, null)
            assertEquals(level.toLong(), state.player.level)
            assertEquals(xp % 200, state.player.xpInLevel)
            assertEquals(title, state.player.titleRes)
        }
    }

    @Test fun streaksSortDatesAndBreakAtCalendarGapsAndDirtyDays() {
        val days = average(day("2026-01-01", 0), day("2026-01-02", 0),
            day("2026-01-03", 0), day("2026-01-07", 400)).reversed()
        val yesterday = Gamification.calculate(days, "2026-01-04", 0 to 0, null)
        assertEquals(3, yesterday.currentStreak)
        assertEquals(3, yesterday.bestStreak)
        assertTrue(yesterday.badges.first { it.badge == Gamification.Badge.THREE_DAY_STREAK }.unlocked)
        assertEquals(0, Gamification.calculate(days, "2026-01-05", 0 to 0, null).currentStreak)
        assertEquals(0, Gamification.calculate(days, "2026-01-07", 0 to 0, null).currentStreak)
        val gap = average(day("2026-01-01", 0), day("2026-01-03", 0), day("2026-01-04", 300))
        assertEquals(1, Gamification.calculate(gap, "2026-01-03", 0 to 0, null).bestStreak)
    }

    @Test fun equalityIsNotCleanAndLimitEqualityIsAllowed() {
        val top = TopAppLimit(AppUsage("app", "Example", 60, 1.0), 60)
        val state = Gamification.calculate(average(day("2026-01-01", 100, 60.0)), "2026-01-01", 100 to 100, top)
        assertEquals(0, state.bestStreak)
        assertTrue(state.limitQuest.complete)
        assertTrue(state.badges.first { it.badge == Gamification.Badge.BOOKWORM }.unlocked)
        assertFalse(state.badges.first { it.badge == Gamification.Badge.COMEBACK_KID }.unlocked)
        assertFalse(Gamification.calculate(emptyList(), "2026-01-01", 0 to 0, top.copy(dailyLimitMin = null)).limitQuest.complete)
    }
}
