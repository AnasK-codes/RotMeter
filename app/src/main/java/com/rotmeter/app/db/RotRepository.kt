package com.rotmeter.app.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate
import java.util.concurrent.CancellationException

/** All methods block. Call them on a background executor; dates use yyyy-MM-dd. */
class RotRepository(
    private val dbHelper: RotDbHelper,
    private val operationAllowed: () -> Boolean = { true }
) {
    private fun ensureActive() {
        if (!operationAllowed()) throw CancellationException("The requesting view was destroyed")
    }

    private val readableDatabase: SQLiteDatabase
        get() {
            ensureActive()
            val db = dbHelper.readableDatabase
            ensureActive()
            return db
        }

    private val writableDatabase: SQLiteDatabase
        get() {
            ensureActive()
            val db = dbHelper.writableDatabase
            ensureActive()
            return db
        }

    /** Demo seeding is atomic: lifecycle cancellation rolls the entire seed back. */
    fun runInTransaction(block: RotRepository.() -> Unit) {
        inTransaction { block(this) }
    }

    fun getTodayRot(date: String): DailyRot =
        readableDatabase.rawQuery(Sql.SELECT_DAILY_ROT, arrayOf(date)).use { cursor ->
            if (cursor.moveToFirst()) {
                DailyRot(
                    totalMinutes = cursor.getInt(0),
                    rotScore = cursor.getInt(1),
                    productiveMinutes = cursor.getDouble(2)
                )
            } else {
                DailyRot(0, 0, 0.0)
            }
        }

    fun getTopRotApps(date: String, limit: Int): List<AppUsage> {
        require(limit >= 0) { "Limit must not be negative" }
        return readableDatabase.rawQuery(
            Sql.SELECT_TOP_ROT_APPS, arrayOf(date, limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toAppUsage())
            }
        }
    }

    /** Returns seven days in chronological order, including days without usage. */
    fun getWeekScores(endDate: String): List<DayScore> {
        val end = LocalDate.parse(endDate)
        val start = end.minusDays(6)
        val scores = readableDatabase.rawQuery(
            Sql.SELECT_WEEK_SCORES, arrayOf(start.toString(), end.toString())
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1))
            }
        }
        return (0L..6L).map { offset ->
            val date = start.plusDays(offset).toString()
            DayScore(date, scores[date] ?: 0)
        }
    }

    fun getWeekOverWeek(endDate: String): Pair<Int, Int> {
        val end = LocalDate.parse(endDate)
        val thisWeekStart = end.minusDays(6).toString()
        val lastWeekStart = end.minusDays(13).toString()
        val lastWeekEnd = end.minusDays(7).toString()
        return readableDatabase.rawQuery(
            Sql.SELECT_WEEK_OVER_WEEK,
            arrayOf(thisWeekStart, end.toString(), lastWeekStart, lastWeekEnd, lastWeekStart, end.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) to cursor.getInt(1) else 0 to 0
        }
    }

    fun getWorstApp(startDate: String, endDate: String): AppUsage? =
        readableDatabase.rawQuery(
            Sql.SELECT_WORST_APP, arrayOf(startDate, endDate)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAppUsage() else null
        }

    fun getWeekTotalsPerApp(startDate: String, endDate: String): List<AppUsage> =
        readableDatabase.rawQuery(
            Sql.SELECT_WEEK_TOTALS_PER_APP, arrayOf(startDate, endDate)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toAppUsage())
            }
        }

    fun getBeatAverageDays(): Int =
        readableDatabase.rawQuery(Sql.SELECT_BEAT_AVERAGE_DAYS, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    fun upsertUsage(date: String, packageName: String, minutes: Int) {
        require(minutes >= 0) { "Usage minutes must not be negative" }
        inTransaction { db ->
            val updatedRows = db.compileStatement(Sql.UPDATE_USAGE).use { statement ->
                statement.bindLong(1, minutes.toLong())
                statement.bindString(2, date)
                statement.bindString(3, packageName)
                statement.executeUpdateDelete()
            }
            if (updatedRows == 0) {
                db.execSQL(Sql.INSERT_USAGE, arrayOf<Any>(date, packageName, minutes))
            }
        }
    }

    fun getAllApps(): List<AppInfo> = readApps(Sql.SELECT_ALL_APPS, null)

    /** One join returns every app, its optional limit, and today's minutes (zero if absent). */
    fun getAppsWithTodayUsage(date: String): List<AppInfo> =
        readApps(Sql.SELECT_ALL_APPS_TODAY, arrayOf(date))

    private fun readApps(sql: String, args: Array<String>?): List<AppInfo> =
        readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        AppInfo(
                            packageName = cursor.getString(0),
                            label = cursor.getString(1),
                            categoryId = cursor.getLong(2),
                            categoryName = cursor.getString(3),
                            rotWeight = cursor.getDouble(4),
                            dailyLimitMin = if (cursor.isNull(5)) null else cursor.getInt(5),
                            todayMinutes = if (cursor.columnCount > 6) cursor.getInt(6) else 0
                        )
                    )
                }
            }
        }

    fun getBlockingLimit(packageName: String): BlockingLimit? =
        readableDatabase.rawQuery(Sql.SELECT_BLOCKING_LIMIT, arrayOf(packageName)).use { cursor ->
            if (cursor.moveToFirst()) BlockingLimit(cursor.getString(0), cursor.getInt(1)) else null
        }

    fun setLimit(packageName: String, minutes: Int) {
        require(minutes > 0) { "Daily limit must be positive" }
        inTransaction { db ->
            val updatedRows = db.compileStatement(Sql.UPDATE_LIMIT).use { statement ->
                statement.bindLong(1, minutes.toLong())
                statement.bindString(2, packageName)
                statement.executeUpdateDelete()
            }
            if (updatedRows == 0) {
                db.execSQL(Sql.INSERT_LIMIT, arrayOf<Any>(packageName, minutes))
            }
        }
    }

    fun removeLimit(packageName: String) {
        writableDatabase.execSQL(Sql.DELETE_LIMIT_FOR_APP, arrayOf(packageName))
    }

    fun getAlerts(): List<AlertItem> = readAlerts(Sql.SELECT_ALERTS)

    fun getUnnotifiedAlerts(): List<AlertItem> = readAlerts(Sql.SELECT_UNNOTIFIED_ALERTS)

    fun markAlertNotified(id: Long) {
        writableDatabase.execSQL(Sql.MARK_ALERT_NOTIFIED, arrayOf(id))
    }

    fun markOldAlertsNotified(beforeDate: String) {
        writableDatabase.execSQL(Sql.MARK_OLD_ALERTS_NOTIFIED, arrayOf(beforeDate))
    }

    fun getTotalCredits(date: String): Int =
        readableDatabase.rawQuery(Sql.SELECT_TOTAL_CREDITS, arrayOf(date)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    fun getGamificationDays(): List<GamificationDay> =
        readableDatabase.rawQuery(Sql.SELECT_GAMIFICATION_DAYS, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    ensureActive()
                    add(GamificationDay(
                        date = cursor.getString(0), totalMinutes = cursor.getInt(1),
                        rotScore = cursor.getInt(2), productiveMinutes = cursor.getDouble(3),
                        alertCount = cursor.getInt(4), averageRotScore = cursor.getDouble(5)
                    ))
                }
            }
        }

    fun getTodayTopAppLimit(date: String): TopAppLimit? =
        readableDatabase.rawQuery(Sql.SELECT_TODAY_TOP_APP_LIMIT, arrayOf(date)).use { cursor ->
            if (cursor.moveToFirst()) {
                TopAppLimit(cursor.toAppUsage(), if (cursor.isNull(4)) null else cursor.getInt(4))
            } else null
        }

    fun clearUsageData() {
        inTransaction { db ->
            db.execSQL(Sql.DELETE_ALL_USAGE)
            db.execSQL(Sql.DELETE_ALL_ALERTS)
            db.execSQL(Sql.DELETE_ALL_CREDITS)
        }
    }

    private fun readAlerts(sql: String): List<AlertItem> =
        readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        AlertItem(
                            id = cursor.getLong(0),
                            alertDate = cursor.getString(1),
                            packageName = cursor.getString(2),
                            label = cursor.getString(3),
                            message = cursor.getString(4),
                            createdAt = cursor.getString(5),
                            notified = cursor.getInt(6) != 0
                        )
                    )
                }
            }
        }

    private fun Cursor.toAppUsage(): AppUsage = AppUsage(
        packageName = getString(0),
        label = getString(1),
        minutes = getInt(2),
        rotWeight = getDouble(3)
    )

    private fun inTransaction(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            block(db)
            ensureActive()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
