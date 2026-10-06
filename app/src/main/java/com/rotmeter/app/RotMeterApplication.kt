package com.rotmeter.app

import android.app.Application
import com.rotmeter.app.notify.NotificationHelper
import com.rotmeter.app.blocking.BlockingAppVisibility
import com.rotmeter.app.work.SyncWorker

class RotMeterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BlockingAppVisibility.register(this)
        NotificationHelper.createChannel(this)
        if (BuildConfig.DEBUG) DebugStartupChecks.run(this)
        SyncWorker.schedule(this)
    }
}
