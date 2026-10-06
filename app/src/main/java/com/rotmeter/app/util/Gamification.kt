package com.rotmeter.app.util

import androidx.annotation.StringRes
import com.rotmeter.app.R
import com.rotmeter.app.db.GamificationDay
import com.rotmeter.app.db.TopAppLimit
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pure calculations; feed this the repository's daily rows on a background thread. */
object Gamification {
    const val XP_PER_LEVEL = 200
    private const val EPSILON = 0.0000001

    data class Player(
        val totalXp: Long, val level: Long, val xpInLevel: Int,
        @get:StringRes val titleRes: Int
    )

    data class Quest(val progress: Int, val complete: Boolean)

    enum class Badge(@get:StringRes val nameRes: Int, @get:StringRes val descriptionRes: Int) {
        FIRST_BLOOD(R.string.badge_first_blood, R.string.badge_first_blood_description),
        TOUCH_GRASS(R.string.badge_touch_grass, R.string.badge_touch_grass_description),
        BOOKWORM(R.string.badge_bookworm, R.string.badge_bookworm_description),
        THREE_DAY_STREAK(R.string.badge_three_day_streak, R.string.badge_three_day_streak_description),
        SEVEN_DAY_STREAK(R.string.badge_seven_day_streak, R.string.badge_seven_day_streak_description),
        COMEBACK_KID(R.string.badge_comeback_kid, R.string.badge_comeback_kid_description)
    }

    data class BadgeState(val badge: Badge, val unlocked: Boolean)

    data class State(
        val player: Player, val currentStreak: Int, val bestStreak: Int,
        val productiveMinutes: Double, val todayAlertCount: Int, val topApp: TopAppLimit?,
        val productiveQuest: Quest, val limitQuest: Quest, val roastQuest: Quest,
        val badges: List<BadgeState>
    )

    fun calculate(days: List<GamificationDay>, today: String, weeks: Pair<Int, Int>, topApp: TopAppLimit?): State {
        val ordered = days.sortedBy { it.date }
        val todayDate = LocalDate.parse(today, DateUtils.formatter)
        // Preserve fractional weighted XP across days, then display whole XP (rounded down).
        val xp = floor(ordered.sumOf { day ->
            day.productiveMinutes * 2.0 + (if (day.alertCount == 0) 30 else 0) +
                (if (day.rotScore < day.averageRotScore) 50 else 0)
        } + EPSILON).toLong().coerceAtLeast(0L)
        val level = 1L + xp / XP_PER_LEVEL
        val title = when (level) {
            in 1L..2L -> R.string.player_doomscroller
            in 3L..5L -> R.string.player_recovering
            in 6L..9L -> R.string.player_focused
            else -> R.string.player_monk
        }

        var previous: LocalDate? = null
        var run = 0
        var best = 0
        var lastPastDate: LocalDate? = null
        var lastPastRun = 0
        for (day in ordered) {
            val date = LocalDate.parse(day.date, DateUtils.formatter)
            val clean = day.rotScore < day.averageRotScore && day.alertCount == 0
            run = if (clean) {
                if (previous?.plusDays(1) == date) run + 1 else 1
            } else 0
            best = maxOf(best, run)
            previous = date
            if (!date.isAfter(todayDate)) {
                lastPastDate = date
                lastPastRun = run
            }
        }
        // Keep yesterday's streak while today has no row; gaps and a dirty today reset it.
        val current = if (lastPastDate == todayDate || lastPastDate == todayDate.minusDays(1)) lastPastRun else 0
        val day = ordered.firstOrNull { it.date == today }
        val productive = day?.productiveMinutes ?: 0.0
        val alerts = day?.alertCount ?: 0
        val productiveComplete = productive + EPSILON >= 20.0
        val limitComplete = topApp?.dailyLimitMin?.let { topApp.app.minutes <= it } ?: false
        val roastsComplete = alerts == 0

        return State(
            player = Player(xp, level, (xp % XP_PER_LEVEL).toInt(), title),
            currentStreak = current, bestStreak = best,
            productiveMinutes = productive, todayAlertCount = alerts, topApp = topApp,
            productiveQuest = Quest((productive / 20.0 * 100.0).coerceIn(0.0, 100.0).roundToInt(), productiveComplete),
            limitQuest = Quest(if (limitComplete) 100 else 0, limitComplete),
            roastQuest = Quest(if (roastsComplete) 100 else 0, roastsComplete),
            badges = listOf(
                BadgeState(Badge.FIRST_BLOOD, ordered.isNotEmpty()),
                BadgeState(Badge.TOUCH_GRASS, ordered.any { it.rotScore == 0 && it.totalMinutes > 0 }),
                BadgeState(Badge.BOOKWORM, ordered.any { it.productiveMinutes + EPSILON >= 60.0 }),
                BadgeState(Badge.THREE_DAY_STREAK, best >= 3),
                BadgeState(Badge.SEVEN_DAY_STREAK, best >= 7),
                BadgeState(Badge.COMEBACK_KID, weeks.first < weeks.second)
            )
        )
    }
}
