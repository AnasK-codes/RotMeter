package com.rotmeter.app.blocking

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/** A final fail-open guard while the user is in RotMeter, including its settings dialogs. */
object BlockingAppVisibility : Application.ActivityLifecycleCallbacks {
    private val resumedActivities = AtomicInteger(0)
    val isVisible: Boolean get() = resumedActivities.get() > 0

    fun register(application: Application) = application.registerActivityLifecycleCallbacks(this)
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) { resumedActivities.incrementAndGet() }
    override fun onActivityPaused(activity: Activity) { resumedActivities.updateAndGet { (it - 1).coerceAtLeast(0) } }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
