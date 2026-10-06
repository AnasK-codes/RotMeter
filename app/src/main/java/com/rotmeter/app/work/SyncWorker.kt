package com.rotmeter.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rotmeter.app.db.DbExecutor
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.notify.NotificationHelper
import com.rotmeter.app.usage.UsagePermission
import com.rotmeter.app.usage.UsageSync
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(DbExecutor.dispatcher) {
        try {
            RotDbHelper(applicationContext).use { helper ->
                val repo = RotRepository(helper)
                if (UsagePermission.hasAccess(applicationContext)) {
                    UsageSync.syncToday(applicationContext, repo)
                }
                NotificationHelper.notifyUnnotifiedAlerts(applicationContext, repo)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("RotSyncWorker", "Could not sync usage or deliver alerts", error)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "rot_usage_sync"

        fun schedule(context: Context): Operation {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            return WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
