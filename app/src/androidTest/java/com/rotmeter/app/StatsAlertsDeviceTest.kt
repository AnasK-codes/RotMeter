package com.rotmeter.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import com.rotmeter.app.db.AlertItem
import com.rotmeter.app.db.DayScore
import com.rotmeter.app.databinding.ItemAlertBinding
import com.rotmeter.app.ui.AlertsAdapter
import com.rotmeter.app.views.BarChartView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsAlertsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun themedContext(night: Boolean): Context {
        val base = instrumentation.targetContext
        val config = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_RotMeter)
    }

    private fun days(vararg scores: Int): List<DayScore> = (0..6).map {
        DayScore(LocalDate.of(2026, 10, 1).plusDays(it.toLong()).toString(), scores.getOrElse(it) { 0 })
    }

    private fun render(context: Context, scores: List<DayScore>): Bitmap {
        val bitmap = Bitmap.createBitmap(350, 220, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync {
            val chart = BarChartView(context)
            chart.setScores(scores)
            chart.measure(
                View.MeasureSpec.makeMeasureSpec(350, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY)
            )
            chart.layout(0, 0, 350, 220)
            chart.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun barPixels(bitmap: Bitmap, color: Int): IntArray {
        val counts = IntArray(7)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val expected = FloatArray(3)
        val actual = FloatArray(3)
        Color.colorToHSV(color, expected)
        pixels.forEachIndexed { index, pixel ->
            Color.colorToHSV(pixel, actual)
            // Count opaque purple fill across its gradient, excluding text and the dashed line.
            if (Color.alpha(pixel) > 240 && actual[1] > 0.12f && kotlin.math.abs(actual[0] - expected[0]) < 15f)
                counts[(index % bitmap.width) / 50]++
        }
        return counts
    }

    @Test
    fun zeroScoresDrawNoBarsAndPositiveScoresScale() {
        val context = themedContext(false)
        val primary = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, "test")
        val zero = render(context, days())
        val positive = render(context, days(100, 50))
        try {
            assertEquals(0, barPixels(zero, primary).sum())
            val pixels = barPixels(positive, primary)
            assertTrue(pixels[0] > pixels[1] * 1.8)
            assertTrue(pixels[1] > 0)
            assertTrue(pixels.drop(2).all { it == 0 })
            val outline = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, "test")
            assertTrue((0 until zero.height).any { zero.getPixel(0, it) == outline })
        } finally {
            zero.recycle()
            positive.recycle()
        }
    }

    @Test
    fun darkChartUsesDarkThemeColor() {
        val lightContext = themedContext(false)
        val darkContext = themedContext(true)
        val light = MaterialColors.getColor(lightContext, androidx.appcompat.R.attr.colorPrimary, "test")
        val dark = MaterialColors.getColor(darkContext, androidx.appcompat.R.attr.colorPrimary, "test")
        assertEquals(lightContext.getColor(R.color.rot_neon_purple), light)
        assertEquals(light, dark)
        assertNotEquals(
            MaterialColors.getColor(lightContext, com.google.android.material.R.attr.colorOnSurface, "test"),
            MaterialColors.getColor(darkContext, com.google.android.material.R.attr.colorOnSurface, "test")
        )
        val bitmap = render(darkContext, days(100))
        try {
            assertTrue(barPixels(bitmap, dark).sum() > 0)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun todayIsBrighterAndBarsUseAVerticalGradient() {
        val today = LocalDate.now()
        val data = (0L..6L).map { DayScore(today.minusDays(6 - it).toString(), 100) }
        val bitmap = render(themedContext(true), data)
        try {
            val old = bitmap.getPixel(25, 90)
            val current = bitmap.getPixel(325, 90)
            assertTrue(Color.red(current) > Color.red(old))
            assertTrue(Color.blue(current) > Color.blue(old))
            assertNotEquals(bitmap.getPixel(325, 90), bitmap.getPixel(325, 140))
        } finally { bitmap.recycle() }
    }

    @Test
    fun alertRowShowsMessageAppAndCreatedTime() {
        instrumentation.runOnMainSync {
            val context = themedContext(false)
            val adapter = AlertsAdapter()
            val alert = AlertItem(1, "2026-10-06", "com.instagram.android", "Instagram", "Touch grass.",
                LocalDateTime.now().minusHours(2).minusMinutes(1)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), false)
            adapter.submitList(listOf(alert))
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
            val binding = ItemAlertBinding.bind(holder.itemView)
            assertEquals(alert.message, binding.message.text.toString())
            assertEquals(alert.label, binding.appLabel.text.toString())
            assertEquals("2h ago", binding.createdAt.text.toString())
            assertEquals(alert.createdAt, binding.createdAt.contentDescription.toString())
        }
    }
}
