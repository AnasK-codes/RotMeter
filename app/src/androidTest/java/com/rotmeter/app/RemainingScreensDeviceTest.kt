package com.rotmeter.app

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotmeter.app.db.AlertItem
import com.rotmeter.app.db.AppInfo
import com.rotmeter.app.db.AppUsage
import com.rotmeter.app.ui.AlertsAdapter
import com.rotmeter.app.ui.AppUsageAdapter
import com.rotmeter.app.ui.LimitsAdapter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemainingScreensDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun themedContext(night: Boolean): Context {
        val base = instrumentation.targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_RotMeter)
    }

    private fun checkTextFits(view: View, widthDp: Int) {
        val width = (widthDp * view.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, width, view.measuredHeight)
        fun check(child: View) {
            if (child.visibility != View.VISIBLE) return
            if (child is TextView && child.text.isNotEmpty()) {
                val layout = child.layout
                assertTrue("Text must have a layout: ${child.text}", layout != null)
                val available = child.width - child.compoundPaddingLeft - child.compoundPaddingRight
                for (line in 0 until layout.lineCount) {
                    assertTrue("Text clipped: ${child.text}", layout.getLineWidth(line) <= available + 1f)
                    assertTrue("Text ellipsized: ${child.text}", layout.getEllipsisCount(line) == 0)
                }
                assertTrue("Text height clipped: ${child.text}",
                    layout.height <= child.height - child.compoundPaddingTop - child.compoundPaddingBottom)
            }
            if (child is ViewGroup) for (i in 0 until child.childCount) check(child.getChildAt(i))
        }
        check(view)
    }

    @Test fun rowsWrapWithoutClippingAt360DpInBothThemes() {
        instrumentation.runOnMainSync {
            for (night in listOf(false, true)) {
                val context = themedContext(night)
                val parent = FrameLayout(context)
                val limits = LimitsAdapter { }
                limits.submitList(listOf(AppInfo("test", "Clash of Clans", 1, "Productive", 0.8, 600, 601)))
                val limitHolder = limits.onCreateViewHolder(parent, 0)
                limits.onBindViewHolder(limitHolder, 0)
                checkTextFits(limitHolder.itemView, 312) // 360dp minus screen padding.
                val usage = AppUsageAdapter(showWeeklyShare = true)
                usage.submitList(listOf(AppUsage("test", "Clash of Clans", 9999, 0.8)))
                val usageHolder = usage.onCreateViewHolder(parent, 0)
                usage.onBindViewHolder(usageHolder, 0)
                checkTextFits(usageHolder.itemView, 312)
                val alerts = AlertsAdapter()
                alerts.submitList(listOf(AlertItem(1, "2026-10-06", "test", "Clash of Clans",
                    "You crossed your limit on Clash of Clans. Touch grass.", "2026-10-06 12:00:00", false)))
                val alertHolder = alerts.onCreateViewHolder(parent, 0)
                alerts.onBindViewHolder(alertHolder, 0)
                checkTextFits(alertHolder.itemView, 288) // Includes Alerts' outer card padding.
            }
        }
    }
}
