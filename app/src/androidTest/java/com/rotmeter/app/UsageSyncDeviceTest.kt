package com.rotmeter.app

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.usage.UsagePermission
import com.rotmeter.app.usage.UsageSync
import com.rotmeter.app.util.DateUtils
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageSyncDeviceTest {
    @Test
    fun syncHonorsDeviceUsageAccess() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testDatabaseName = "rotmeter-usage-device-test.db"
        val isolatedContext = object : ContextWrapper(appContext) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = appContext.getDatabasePath(testDatabaseName)
            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?
            ): SQLiteDatabase = appContext.openOrCreateDatabase(testDatabaseName, mode, factory, errorHandler)
            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?
            ): SQLiteDatabase = appContext.openOrCreateDatabase(testDatabaseName, mode, factory)
        }
        appContext.deleteDatabase(testDatabaseName)
        try {
            RotDbHelper(isolatedContext).use { helper ->
                val repo = RotRepository(helper)
                val today = DateUtils.todayString()
                repo.upsertUsage(today, "com.instagram.android", 17)
                val hasAccess = UsagePermission.hasAccess(appContext)
                val updated = UsageSync.syncToday(appContext, repo)
                if (hasAccess) {
                    assertTrue(updated >= 0 && updated <= repo.getAllApps().size)
                } else {
                    assertEquals(-1, updated)
                    assertEquals(17, repo.getTodayRot(today).totalMinutes)
                }
            }
        } finally {
            appContext.deleteDatabase(testDatabaseName)
        }
    }
}
