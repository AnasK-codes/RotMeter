package com.rotmeter.app.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable

/** Open/read/write this helper on a background executor, since opening can create the DB. */
class RotDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "rotmeter.db", null, 1), Closeable {

    // Explicit Closeable keeps Kotlin use() compatible with SQLiteOpenHelper on API 26–28.

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL(Sql.ENABLE_FOREIGN_KEYS)
    }

    override fun onCreate(db: SQLiteDatabase) {
        listOf(
            Sql.CREATE_CATEGORIES,
            Sql.CREATE_APPS,
            Sql.CREATE_USAGE_LOG,
            Sql.CREATE_LIMITS,
            Sql.CREATE_ALERTS,
            Sql.CREATE_CREDITS,
            Sql.CREATE_DAILY_ROT_VIEW,
            Sql.CREATE_APP_TODAY_RANK_VIEW,
            Sql.CREATE_LIMIT_INSERT_TRIGGER,
            Sql.CREATE_LIMIT_UPDATE_TRIGGER,
            Sql.CREATE_CREDIT_INSERT_TRIGGER,
            Sql.CREATE_CREDIT_UPDATE_TRIGGER,
            Sql.SEED_CATEGORIES,
            Sql.SEED_APPS
        ).forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf(
            Sql.DROP_LIMIT_INSERT_TRIGGER,
            Sql.DROP_LIMIT_UPDATE_TRIGGER,
            Sql.DROP_CREDIT_INSERT_TRIGGER,
            Sql.DROP_CREDIT_UPDATE_TRIGGER,
            Sql.DROP_DAILY_ROT_VIEW,
            Sql.DROP_APP_TODAY_RANK_VIEW,
            Sql.DROP_CREDITS,
            Sql.DROP_ALERTS,
            Sql.DROP_LIMITS,
            Sql.DROP_USAGE_LOG,
            Sql.DROP_APPS,
            Sql.DROP_CATEGORIES
        ).forEach(db::execSQL)
        onCreate(db)
    }
}
