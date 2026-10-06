package com.rotmeter.app.db

import android.util.Log

/** Debug-only, blocking test. Startup runs it on DbExecutor and closes the helper afterward. */
fun RotDbHelper.selfTest() {
    try {
        val db = writableDatabase
        val date = "9999-12-31"
        val packageName = "com.instagram.android"
        db.beginTransaction()
        try {
            // Make the test repeatable even if these rows already exist; all changes roll back.
            db.execSQL(Sql.DELETE_USAGE_FOR_DATE_APP, arrayOf(date, packageName))
            db.execSQL(Sql.DELETE_ALERTS_FOR_DATE_APP, arrayOf(date, packageName))
            db.execSQL(Sql.DELETE_LIMIT_FOR_APP, arrayOf(packageName))
            db.execSQL(Sql.INSERT_LIMIT, arrayOf<Any>(packageName, 1))
            db.execSQL(Sql.INSERT_USAGE, arrayOf<Any>(date, packageName, 2))
            db.rawQuery(Sql.COUNT_ALERTS_FOR_DATE_APP, arrayOf(date, packageName)).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 1) {
                    "Expected exactly one alert after exceeding the limit"
                }
            }
        } finally {
            // Deliberately never mark the transaction successful.
            db.endTransaction()
        }
        Log.d("RotSelfTest", "PASS: limit insert trigger created an alert; test changes rolled back.")
    } catch (error: Exception) {
        Log.d("RotSelfTest", "FAIL: ${error.message}", error)
    }
}
