package com.rotmeter.app

import android.content.ComponentName
import android.app.NotificationManager
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotmeter.app.blocking.BlockingPreferences
import com.rotmeter.app.blocking.BlockingPolicy
import com.rotmeter.app.blocking.SoftBlockService
import com.rotmeter.app.notify.NotificationHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockingDeviceTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun defaultOffSelectedOnlyAndMasterOffTakeEffectImmediately() {
        val preferenceFile = "rotmeter-blocking-instrumentation-test"
        val isolated = object : ContextWrapper(appContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                appContext.getSharedPreferences(preferenceFile, mode)
        }
        appContext.deleteSharedPreferences(preferenceFile)
        try {
            assertFalse(BlockingPreferences.isEnabled(isolated))
            BlockingPreferences.setSelected(isolated, "com.instagram.android", true)
            assertFalse(BlockingPreferences.shouldMonitor(isolated, "com.instagram.android"))
            BlockingPreferences.setEnabled(isolated, true)
            assertTrue(BlockingPreferences.shouldMonitor(isolated, "com.instagram.android"))
            assertFalse(BlockingPreferences.shouldMonitor(isolated, "com.google.android.youtube"))
            assertTrue(BlockingPolicy.shouldBlock(true, true, 5, 5))
            BlockingPreferences.setEnabled(isolated, false)
            assertFalse(BlockingPreferences.shouldMonitor(isolated, "com.instagram.android"))
            assertFalse(BlockingPolicy.shouldBlock(BlockingPreferences.isEnabled(isolated), true, 5, 500))
            BlockingPreferences.setEnabled(isolated, true)
            BlockingPreferences.setSelected(isolated, "com.instagram.android", false)
            assertFalse(BlockingPreferences.shouldMonitor(isolated, "com.instagram.android"))
            BlockingPreferences.setSelected(isolated, appContext.packageName, true)
            assertFalse(BlockingPreferences.shouldMonitor(isolated, appContext.packageName))
        } finally { appContext.deleteSharedPreferences(preferenceFile) }
    }

    @Test fun notificationChannelUsesCustomSoundAndPreservesLegacyMute() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        val legacy = manager.getNotificationChannel("rot_alerts")
        val legacyUserChoice = legacy != null && (legacy.sound == null ||
            legacy.sound != android.provider.Settings.System.DEFAULT_NOTIFICATION_URI ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && legacy.hasUserSetSound()))
        assertEquals(if (legacyUserChoice) legacy?.sound else NotificationHelper.soundUri(appContext), channel.sound)
        assertEquals(android.media.AudioAttributes.USAGE_NOTIFICATION, channel.audioAttributes.usage)
    }

    @Suppress("DEPRECATION")
    @Test fun accessibilityServiceIsProtectedAndCustomAudioCanBeDecoded() {
        val info = appContext.packageManager.getServiceInfo(
            ComponentName(appContext, SoftBlockService::class.java), PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", info.permission)
        assertTrue(info.exported)
        assertNotNull(info.metaData.get("android.accessibilityservice"))
        assertEquals("android.resource://${appContext.packageName}/raw/fahhhhh", NotificationHelper.soundUri(appContext).toString())
        val player = MediaPlayer.create(appContext, NotificationHelper.soundUri(appContext))
        assertNotNull(player)
        try { assertTrue(player.duration > 0) } finally { player.release() }
    }
}
