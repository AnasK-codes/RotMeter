package com.rotmeter.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.rotmeter.app.db.DailyRot
import com.rotmeter.app.db.DbExecutor
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.db.selfTest
import com.rotmeter.app.usage.UsageSync
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardeningDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun isolatedContext(name: String): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(nameIgnored: String): File = context.getDatabasePath(name)
        override fun openOrCreateDatabase(
            nameIgnored: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase = context.openOrCreateDatabase(name, mode, factory, errorHandler)
        override fun openOrCreateDatabase(
            nameIgnored: String, mode: Int, factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase = context.openOrCreateDatabase(name, mode, factory)
    }

    @Test
    fun emptyDatabaseAndMissingUsageAccessAreSafe() {
        val name = "rotmeter-hardening-empty-test.db"
        context.deleteDatabase(name)
        try {
            RotDbHelper(isolatedContext(name)).use { helper ->
                val repo = RotRepository(helper)
                val date = "2026-01-14"
                assertEquals(DailyRot(0, 0, 0.0), repo.getTodayRot(date))
                assertTrue(repo.getTopRotApps(date, 3).isEmpty())
                assertEquals(7, repo.getWeekScores(date).size)
                assertTrue(repo.getWeekScores(date).all { it.rotScore == 0 })
                assertEquals(0 to 0, repo.getWeekOverWeek(date))
                assertNull(repo.getWorstApp("2026-01-08", date))
                assertTrue(repo.getWeekTotalsPerApp("2026-01-08", date).isEmpty())
                assertEquals(0, repo.getBeatAverageDays())
                assertEquals(0, repo.getTotalCredits(date))
                assertTrue(repo.getAlerts().isEmpty())
                assertTrue(repo.getUnnotifiedAlerts().isEmpty())
                assertEquals(12, repo.getAllApps().size)
                assertTrue(repo.getAllApps().all { it.dailyLimitMin == null })
                val deniedContext = object : ContextWrapper(context) {
                    override fun getSystemService(name: String): Any? =
                        if (name == Context.APP_OPS_SERVICE) null else super.getSystemService(name)
                }
                repo.upsertUsage(date, "com.instagram.android", 17)
                assertEquals(-1, UsageSync.syncToday(deniedContext, repo))
                assertEquals(17, repo.getTodayRot(date).rotScore)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun reopeningPersistsDataAndSelfTestRollsBack() {
        val name = "rotmeter-hardening-restart-test.db"
        val isolated = isolatedContext(name)
        context.deleteDatabase(name)
        try {
            RotDbHelper(isolated).use { helper ->
                val repo = RotRepository(helper)
                repo.setLimit("com.instagram.android", 60)
                repo.upsertUsage("2026-01-14", "com.instagram.android", 61)
                repo.upsertUsage("2026-01-14", "com.leetcode.app", 7)
            }
            RotDbHelper(isolated).use { helper ->
                val repo = RotRepository(helper)
                val alertsBefore = repo.getAlerts()
                val appsBefore = repo.getAllApps()
                assertEquals(54, repo.getTodayRot("2026-01-14").rotScore)
                assertEquals(7, repo.getTotalCredits("2026-01-14"))
                helper.selfTest()
                assertEquals(appsBefore, repo.getAllApps())
                assertEquals(alertsBefore, repo.getAlerts())
                assertEquals(DailyRot(0, 0, 0.0), repo.getTodayRot("9999-12-31"))
                repo.upsertUsage("2026-01-14", "com.instagram.android", 90)
                assertEquals(1, repo.getAlerts().size)
                try {
                    repo.upsertUsage("2026-01-14", "unknown.package", 1)
                    fail("Foreign keys must reject an unseeded package")
                } catch (_: SQLiteConstraintException) {
                    assertEquals(97, repo.getTodayRot("2026-01-14").totalMinutes)
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun tabsSurviveRecreationAndLateDatabaseCallbacks() {
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.waitForIdleSync()
        val queueEntered = CountDownLatch(1)
        val releaseQueue = CountDownLatch(1)
        DbExecutor.executor.execute {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            queueEntered.countDown()
            releaseQueue.await(10, TimeUnit.SECONDS)
        }
        assertTrue(queueEntered.await(5, TimeUnit.SECONDS))
        try {
            instrumentation.runOnMainSync {
                val navigation = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                navigation.selectedItemId = R.id.nav_stats
                navigation.selectedItemId = R.id.nav_limits
                navigation.selectedItemId = R.id.nav_alerts
                activity.recreate()
            }
        } finally {
            releaseQueue.countDown()
        }
        // Drains queued database reads after their fragment views were destroyed.
        DbExecutor.executor.submit {}.get(10, TimeUnit.SECONDS)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val recreated = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            assertNotSame(activity, recreated)
            assertEquals(R.id.nav_alerts, recreated.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId)
            assertEquals(R.id.nav_alerts.toString(), recreated.supportFragmentManager.findFragmentById(R.id.fragment_container)?.tag)
            recreated.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_home
        }
    }

    @Test
    fun rotationRetainsSelectedTab() {
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        val originalOrientation = activity.requestedOrientation
        val target = if (activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        try {
            instrumentation.runOnMainSync {
                activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_stats
                activity.requestedOrientation = target
            }
            var rotated: MainActivity? = null
            val deadline = SystemClock.elapsedRealtime() + 10_000L
            while (rotated == null && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    rotated = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                        .firstOrNull { it !== activity }
                }
                if (rotated == null) SystemClock.sleep(50)
            }
            assertNotNull("Rotation must recreate the activity", rotated)
            instrumentation.runOnMainSync {
                val current = requireNotNull(rotated)
                assertEquals(
                    if (target == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT,
                    current.resources.configuration.orientation
                )
                assertEquals(R.id.nav_stats, current.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId)
                assertEquals(R.id.nav_stats.toString(), current.supportFragmentManager.findFragmentById(R.id.fragment_container)?.tag)
            }
        } finally {
            instrumentation.runOnMainSync {
                val current = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                current?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_home
                current?.requestedOrientation = originalOrientation
            }
            instrumentation.waitForIdleSync()
        }
    }
}
