package com.rotmeter.app

import android.content.Context
import com.rotmeter.app.db.DbExecutor
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.selfTest

internal object DebugStartupChecks {
    fun run(context: Context) {
        val appContext = context.applicationContext
        DbExecutor.executor.execute {
            RotDbHelper(appContext).use { it.selfTest() }
        }
    }
}
