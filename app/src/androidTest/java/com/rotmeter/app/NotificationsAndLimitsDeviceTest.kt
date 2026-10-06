package com.rotmeter.app

import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.notify.NotificationHelper
import com.rotmeter.app.util.DateUtils
import com.rotmeter.app.work.SyncWorker
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsAndLimitsDeviceTest {
    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun startupCreatesChannelAndSchedulingKeepsOneWorker() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        SyncWorker.schedule(appContext).result.get(10, TimeUnit.SECONDS)
        SyncWorker.schedule(appContext).result.get(10, TimeUnit.SECONDS)
        val work = WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(SyncWorker.UNIQUE_WORK_NAME).get(10, TimeUnit.SECONDS)
        assertEquals(1, work.size)
    }

    @Test
    fun limitChangesPersistAndBlockedAlertsRemainPending() {
        val databaseName = "rotmeter-notify-limits-test.db"
        val isolated = object : ContextWrapper(appContext) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = appContext.getDatabasePath(databaseName)
            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?
            ): SQLiteDatabase = appContext.openOrCreateDatabase(databaseName, mode, factory, errorHandler)
            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?
            ): SQLiteDatabase = appContext.openOrCreateDatabase(databaseName, mode, factory)
        }
        val blockedNotifications = object : ContextWrapper(appContext) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.NOTIFICATION_SERVICE) null else super.getSystemService(name)
        }
        appContext.deleteDatabase(databaseName)
        try {
            RotDbHelper(isolated).use { helper ->
                val repo = RotRepository(helper)
                val instagram = "com.instagram.android"
                repo.setLimit(instagram, 5)
                assertEquals(5, repo.getAllApps().first { it.packageName == instagram }.dailyLimitMin)
                repo.upsertUsage(DateUtils.todayString(), instagram, 6)
                assertEquals(1, repo.getUnnotifiedAlerts().size)
                repo.setLimit(instagram, 600)
                assertEquals(600, repo.getAllApps().first { it.packageName == instagram }.dailyLimitMin)
                NotificationHelper.notifyUnnotifiedAlerts(blockedNotifications, repo)
                assertEquals(1, repo.getUnnotifiedAlerts().size)
                repo.removeLimit(instagram)
                assertNull(repo.getAllApps().first { it.packageName == instagram }.dailyLimitMin)
            }
        } finally {
            appContext.deleteDatabase(databaseName)
        }
    }

    @Test
    fun notificationUsesRoastChannelAndMessage() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val notificationId = -9001
        try {
            val shown = NotificationHelper.show(appContext, notificationId, "RotMeter", "Test roast")
            if (shown) {
                val deadline = SystemClock.elapsedRealtime() + 3_000L
                var posted = manager.activeNotifications.firstOrNull { it.id == notificationId }
                while (posted == null && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(50)
                    posted = manager.activeNotifications.firstOrNull { it.id == notificationId }
                }
                assertNotNull(posted)
                val notification = requireNotNull(posted).notification
                assertEquals(NotificationHelper.CHANNEL_ID, notification.channelId)
                assertEquals("RotMeter", notification.extras.getString("android.title"))
                assertEquals("Test roast", notification.extras.getString("android.text"))
                assertTrue(manager.areNotificationsEnabled())
            } else {
                assertFalse(manager.activeNotifications.any { it.id == notificationId })
            }
        } finally {
            manager.cancel(notificationId)
        }
    }
}
