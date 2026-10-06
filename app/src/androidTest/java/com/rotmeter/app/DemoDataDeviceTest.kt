package com.rotmeter.app

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.util.DateUtils
import com.rotmeter.app.util.DemoData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoDataDeviceTest {
    @Test
    fun demoLoadsAndHelperClosesOnDevice() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testDatabaseName = "rotmeter-demo-device-test.db"
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
            // Exercises the same use() call that previously threw on Android 9.
            RotDbHelper(isolatedContext).use { helper ->
                val repo = RotRepository(helper)
                DemoData.seed(repo)
                val today = DateUtils.todayString()
                assertEquals(97, repo.getTodayRot(today).rotScore)
                assertEquals(58, repo.getTotalCredits(today))
                val thisWeek = repo.getWeekScores(today).sumOf { it.rotScore }
                val lastWeek = repo.getWeekScores(DateUtils.daysAgo(7)).sumOf { it.rotScore }
                assertEquals(thisWeek to lastWeek, repo.getWeekOverWeek(today))
                assertEquals(
                    listOf("Instagram", "YouTube", "X"),
                    repo.getTopRotApps(today, 3).map { it.label }
                )
                assertTrue(repo.getAlerts().isNotEmpty())
                assertTrue(repo.getUnnotifiedAlerts().all { it.alertDate == today })
                val firstDaily = repo.getTodayRot(today)
                DemoData.seed(repo)
                assertEquals(firstDaily, repo.getTodayRot(today))
            }
        } finally {
            appContext.deleteDatabase(testDatabaseName)
        }
    }
}
