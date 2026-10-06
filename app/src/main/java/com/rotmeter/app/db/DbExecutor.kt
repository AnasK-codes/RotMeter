package com.rotmeter.app.db

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

/** Serialize app DB operations, including startup, demo writes, fragment reads, and workers. */
object DbExecutor {
    val executor = Executors.newSingleThreadExecutor()
    val dispatcher = executor.asCoroutineDispatcher()
}
