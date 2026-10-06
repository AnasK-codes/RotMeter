package com.rotmeter.app.blocking

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.rotmeter.app.R
import com.rotmeter.app.db.DbExecutor
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.usage.UsagePermission
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Opt-in soft blocking; only window package metadata is used, never screen text or gestures. */
class SoftBlockService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val alive = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private var preferences: SharedPreferences? = null
    private var candidate: String? = null
    private var candidateSince = 0L
    private var keyboardPackage: String? = null
    private val nextCheck = Runnable { candidate?.let { check(it) } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        alive.set(true)
        preferences = BlockingPreferences.preferences(this).also {
            it.registerOnSharedPreferenceChangeListener(this)
        }
        keyboardPackage = readKeyboardPackage()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (!alive.get() || BlockingAppVisibility.isVisible || !BlockingPreferences.isEnabled(this)) {
            stopChecks()
            return
        }
        // RotMeter, system screens, and all unselected apps cancel pending block actions immediately.
        if (!BlockingPreferences.shouldMonitor(this, pkg)) {
            if (pkg == keyboardPackage && candidate != null) return // Keep tracking while typing.
            stopChecks()
            return
        }
        keyboardPackage = readKeyboardPackage()
        if (candidate != pkg) candidateSince = SystemClock.elapsedRealtime()
        candidate = pkg
        check(pkg)
    }

    private fun readKeyboardPackage(): String? = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
    } catch (_: SecurityException) { null }

    private fun canCheck(pkg: String): Boolean = alive.get() &&
        !BlockingAppVisibility.isVisible && candidate == pkg &&
        BlockingPreferences.shouldMonitor(this, pkg) && UsagePermission.hasAccess(this) &&
        getSystemService(PowerManager::class.java)?.isInteractive == true &&
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false

    private fun check(pkg: String) {
        mainHandler.removeCallbacks(nextCheck)
        if (!canCheck(pkg)) { stopChecks(); return }
        val ticket = generation.incrementAndGet()
        val appContext = applicationContext
        DbExecutor.executor.execute {
            fun stillAllowed() = alive.get() && generation.get() == ticket &&
                !BlockingAppVisibility.isVisible && BlockingPreferences.shouldMonitor(appContext, pkg)
            if (!stillAllowed()) return@execute
            val result = runCatching {
                RotDbHelper(appContext).use { helper ->
                    val limit = RotRepository(helper) { stillAllowed() }.getBlockingLimit(pkg)
                        ?: return@use null
                    val reading = LiveUsageReader.read(appContext, pkg, operationAllowed = { stillAllowed() }) ?: return@use null
                    limit to reading
                }
            }
            if (!stillAllowed()) return@execute
            mainHandler.post {
                if (generation.get() != ticket || !canCheck(pkg)) return@post
                val data = result.getOrNull()
                if (data == null) {
                    result.exceptionOrNull()?.let { Log.e("RotBlocking", "Could not check app limit", it) }
                    stopChecks() // No permission, no limit, or a failed query always allows access.
                    return@post
                }
                val (limit, reading) = data
                if (reading.foregroundPackage != pkg) {
                    // Wait for Android's foreground history; never act on an unconfirmed app.
                    // Keep a slow retry after the initial grace period for devices that batch events.
                    val delay = if (SystemClock.elapsedRealtime() - candidateSince < 2_000L) 250L else 5_000L
                    mainHandler.postDelayed(nextCheck, delay)
                    return@post
                }
                // Read switches again just before acting: queued checks cannot outlive disabling.
                if (BlockingPolicy.shouldBlock(BlockingPreferences.isEnabled(this),
                        BlockingPreferences.isSelected(this, pkg), limit.minutes, reading.minutes) && canCheck(pkg)) {
                    if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                        stopChecks()
                        Toast.makeText(this, getString(R.string.soft_blocked_message, limit.label), Toast.LENGTH_LONG).show()
                        return@post
                    }
                }
                mainHandler.postDelayed(nextCheck, 5_000L)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val pkg = candidate
        stopChecks() // Invalidates both queued reads and any posted action.
        if (pkg != null && BlockingPreferences.shouldMonitor(this, pkg) && !BlockingAppVisibility.isVisible) {
            candidate = pkg
            candidateSince = SystemClock.elapsedRealtime()
            check(pkg)
        }
    }

    private fun stopChecks() {
        generation.incrementAndGet()
        candidate = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onInterrupt() = stopChecks()

    override fun onDestroy() {
        alive.set(false)
        stopChecks()
        preferences?.unregisterOnSharedPreferenceChangeListener(this)
        preferences = null
        super.onDestroy()
    }
}
