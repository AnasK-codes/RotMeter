package com.rotmeter.app

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.util.DateUtils
import com.rotmeter.app.util.DemoData
import com.rotmeter.app.util.Gamification
import com.rotmeter.app.views.RotRingView
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GamificationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext = instrumentation.targetContext

    private fun withDatabase(block: (RotDbHelper) -> Unit) {
        val name = "rotmeter-gamification-test.db"
        val isolated = object : ContextWrapper(appContext) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(ignored: String): File = appContext.getDatabasePath(name)
            override fun openOrCreateDatabase(ignored: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) =
                appContext.openOrCreateDatabase(name, mode, factory, errorHandler)
            override fun openOrCreateDatabase(ignored: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                appContext.openOrCreateDatabase(name, mode, factory)
        }
        appContext.deleteDatabase(name)
        try { RotDbHelper(isolated).use(block) } finally { appContext.deleteDatabase(name) }
    }

    @Test fun severalAlertsDoNotMultiplyDailyProductiveMinutesOrXp() = withDatabase { helper ->
        val repo = RotRepository(helper)
        repo.setLimit("com.instagram.android", 1)
        repo.setLimit("com.snapchat.android", 1)
        repo.upsertUsage("2026-01-01", "com.instagram.android", 2)
        repo.upsertUsage("2026-01-01", "com.snapchat.android", 2)
        repo.upsertUsage("2026-01-01", "com.leetcode.app", 5)
        repo.upsertUsage("2026-01-02", "com.leetcode.app", 30)
        val days = repo.getGamificationDays()
        assertEquals(2, days.size)
        assertEquals(2, days.first().alertCount)
        assertEquals(5.0, days.first().productiveMinutes, 0.0)
        assertEquals(0.0, days.first().averageRotScore, 0.0)
        assertEquals(100L, Gamification.calculate(days, "2026-01-02", 0 to 0, null).player.totalXp)
        assertNull(repo.getTodayTopAppLimit("2026-01-02"))
    }

    @Test fun cancellingTheViewRollsBackTheWholeDemoTransaction() = withDatabase { helper ->
        val active = AtomicBoolean(true)
        val repo = RotRepository(helper) { active.get() }
        repo.setLimit("com.instagram.android", 60)
        repo.upsertUsage("2026-01-01", "com.instagram.android", 10)
        try {
            repo.runInTransaction {
                clearUsageData()
                upsertUsage("2026-01-02", "com.instagram.android", 80)
                active.set(false)
                getGamificationDays()
            }
            fail("A cancelled view must stop subsequent repository work")
        } catch (_: CancellationException) {
            active.set(true)
            assertEquals(10, repo.getTodayRot("2026-01-01").totalMinutes)
            assertEquals(0, repo.getTodayRot("2026-01-02").totalMinutes)
            assertTrue(repo.getAlerts().isEmpty())
            assertEquals(60, repo.getAllApps().first { it.label == "Instagram" }.dailyLimitMin)
        }
    }

    @Test fun atomicDemoProducesRepeatableGamification() = withDatabase { helper ->
        val repo = RotRepository(helper)
        repo.runInTransaction { DemoData.seed(this) }
        val today = DateUtils.todayString()
        val days = repo.getGamificationDays()
        val state = Gamification.calculate(days, today, repo.getWeekOverWeek(today), repo.getTodayTopAppLimit(today))
        assertEquals(14, days.size)
        assertEquals(97, repo.getTodayRot(today).rotScore)
        assertEquals(58, repo.getTotalCredits(today))
        assertTrue(state.productiveQuest.complete)
        assertTrue(state.limitQuest.complete)
        assertTrue(state.roastQuest.complete)
        Log.d("RotGameTest", "DEMO: XP=${state.player.totalXp}, Lv=${state.player.level}, remainder=${state.player.xpInLevel}, productive=${state.productiveMinutes}, streak=${state.currentStreak}, best=${state.bestStreak}, roasts=${state.todayAlertCount}, badges=${state.badges.joinToString { "${it.badge}:${it.unlocked}" }}")
        repo.runInTransaction { DemoData.seed(this) }
        assertEquals(state, Gamification.calculate(repo.getGamificationDays(), today, repo.getWeekOverWeek(today), repo.getTodayTopAppLimit(today)))
    }

    @Test fun todayUsageJoinKeepsAppsWithNoUsageAndNoLimit() = withDatabase { helper ->
        val repo = RotRepository(helper)
        repo.setLimit("com.instagram.android", 60)
        repo.upsertUsage("2026-01-01", "com.instagram.android", 90)
        repo.upsertUsage("2026-01-02", "com.instagram.android", 56)
        val apps = repo.getAppsWithTodayUsage("2026-01-02")
        assertEquals(12, apps.size)
        assertEquals(12, apps.map { it.packageName }.toSet().size)
        val instagram = apps.first { it.label == "Instagram" }
        assertEquals(56, instagram.todayMinutes)
        assertEquals(60, instagram.dailyLimitMin)
        val kindle = apps.first { it.label == "Kindle" }
        assertEquals(0, kindle.todayMinutes)
        assertNull(kindle.dailyLimitMin)
        assertTrue(repo.getAllApps().all { it.todayMinutes == 0 })
    }

    @Test fun blockingLimitsFollowChangesRemovalAndUnknownPackages() = withDatabase { helper ->
        val repo = RotRepository(helper)
        assertNull(repo.getBlockingLimit("com.instagram.android"))
        assertNull(repo.getBlockingLimit("com.rotmeter.app"))
        repo.setLimit("com.instagram.android", 5)
        assertEquals("Instagram", repo.getBlockingLimit("com.instagram.android")?.label)
        assertEquals(5, repo.getBlockingLimit("com.instagram.android")?.minutes)
        repo.setLimit("com.instagram.android", 60)
        assertEquals(60, repo.getBlockingLimit("com.instagram.android")?.minutes)
        repo.removeLimit("com.instagram.android")
        assertNull(repo.getBlockingLimit("com.instagram.android"))
    }

    @Test fun zeroRingHasOnlyATrackAndHugeScoresFillTheRing() {
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(appContext, R.style.Theme_RotMeter)
            val ring = RotRingView(context)
            ring.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY))
            ring.layout(0, 0, 250, 250)
            val radius = (125 - 14 * context.resources.displayMetrics.density).toInt()
            val points = listOf(125 to 125 - radius, 125 + radius to 125,
                125 to 125 + radius, 125 - radius to 125)
            val bitmap = Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888)
            try {
                ring.setScore(0, "Fresh Brain", context.getColor(R.color.rot_fresh))
                ring.draw(Canvas(bitmap))
                points.forEach { (x, y) -> assertEquals(context.getColor(R.color.rot_surface_variant), bitmap.getPixel(x, y)) }
                ring.setScore(Int.MAX_VALUE, "Terminally Online", context.getColor(R.color.rot_terminal))
                bitmap.eraseColor(0)
                ring.draw(Canvas(bitmap))
                points.forEach { (x, y) -> assertNotEquals(context.getColor(R.color.rot_surface_variant), bitmap.getPixel(x, y)) }
                assertTrue(ring.contentDescription.toString().contains(Int.MAX_VALUE.toString()))
                ring.stopAnimation()
            } finally { bitmap.recycle() }
        }
    }
}
