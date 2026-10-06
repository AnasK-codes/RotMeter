package com.rotmeter.app

import android.content.Context

/** Source-set counterpart: the trigger self-test implementation exists only in debug. */
internal object DebugStartupChecks {
    fun run(context: Context) = Unit
}
