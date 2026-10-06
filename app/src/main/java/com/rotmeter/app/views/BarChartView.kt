package com.rotmeter.app.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.rotmeter.app.R
import com.rotmeter.app.db.DayScore
import com.rotmeter.app.util.DateUtils
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = density }
    private val averagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val barPath = Path()
    private val barBounds = RectF()
    private val radii = FloatArray(8)
    private var scores = emptyList<DayScore>()
    private var labels = emptyList<String>()
    private var today = DateUtils.todayString()
    private var growth = 1f
    private var animator: ValueAnimator? = null

    fun setScores(days: List<DayScore>) {
        stopAnimation()
        scores = days.take(7).toList()
        labels = scores.map {
            LocalDate.parse(it.date).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
        today = DateUtils.todayString()
        contentDescription = scores.joinToString("; ") {
            context.getString(R.string.chart_day_description, it.date, it.rotScore)
        }
        if (!isAttachedToWindow || windowVisibility != VISIBLE) {
            growth = 1f
            invalidate()
            return
        }
        growth = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700L
            interpolator = DecelerateInterpolator()
            addUpdateListener { growth = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun stopAnimation() {
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
        growth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
        val text = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        baselinePaint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
        averagePaint.color = ColorUtils.setAlphaComponent(text, 55)
        labelPaint.color = text
        labelPaint.textSize = sp(12f)
        valuePaint.color = text
        valuePaint.textSize = sp(11f)
        val gap = 8f * density
        val font = labelPaint.fontMetrics
        val left = paddingLeft.toFloat()
        val right = width - paddingRight.toFloat()
        // Reserve space above the highest bar for its value, including larger font scales.
        val top = paddingTop + gap + valuePaint.fontMetrics.run { descent - ascent }
        val labelBaseline = height - paddingBottom.toFloat() - font.descent
        val baseline = labelBaseline + font.ascent - gap
        val plotHeight = baseline - top
        if (right <= left || plotHeight <= 0f) return
        canvas.drawLine(left, baseline, right, baseline, baselinePaint)
        val slotWidth = (right - left) / 7f
        val barWidth = slotWidth * 0.62f
        val maxScore = scores.maxOfOrNull { it.rotScore.coerceAtLeast(0) } ?: 0
        if (maxScore > 0 && scores.isNotEmpty()) {
            val average = scores.map { it.rotScore.coerceAtLeast(0).toDouble() }.average()
            val y = baseline - (average / maxScore * plotHeight).toFloat()
            canvas.drawLine(left, y, right, y, averagePaint)
        }
        scores.forEachIndexed { index, day ->
            val center = left + slotWidth * (index + 0.5f)
            canvas.drawText(labels[index], center, labelBaseline, labelPaint)
            // Preserve the empty chart: baseline and weekdays, with no fake bars or average.
            if (maxScore == 0) return@forEachIndexed
            val barHeight = (day.rotScore.coerceAtLeast(0).toDouble() / maxScore * plotHeight * growth).toFloat()
            val barTop = baseline - barHeight
            val color = if (day.date == today) primary else ColorUtils.blendARGB(primary, surface, 0.52f)
            if (barHeight > 0f) {
                barPaint.shader = LinearGradient(0f, barTop, 0f, baseline, color,
                    ColorUtils.blendARGB(color, surface, 0.48f), Shader.TileMode.CLAMP)
                val radius = minOf(6 * density, barWidth / 2f, barHeight / 2f)
                radii.fill(0f)
                for (i in 0..3) radii[i] = radius
                barBounds.set(center - barWidth / 2f, barTop, center + barWidth / 2f, baseline)
                barPath.reset()
                barPath.addRoundRect(barBounds, radii, Path.Direction.CW)
                canvas.drawPath(barPath, barPaint)
            }
            valuePaint.textSize = sp(11f)
            val value = day.rotScore.coerceAtLeast(0).toString()
            val textWidth = valuePaint.measureText(value)
            if (textWidth > slotWidth * 0.9f) valuePaint.textSize *= slotWidth * 0.9f / textWidth
            canvas.drawText(value, center, barTop - 4 * density, valuePaint)
        }
    }

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) stopAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }
}
