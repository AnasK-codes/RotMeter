package com.rotmeter.app.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.rotmeter.app.R
import kotlin.math.roundToInt

class RotRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val stroke = 14f * density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = ContextCompat.getColor(context, R.color.rot_surface_variant)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = ContextCompat.getColor(context, R.color.rot_text_secondary)
    }
    private val bounds = RectF()
    private val gradientMatrix = Matrix()
    private var targetScore = 0
    private var shownScore = "0"
    private var levelName = context.getString(R.string.level_fresh)
    private var ringColor = ContextCompat.getColor(context, R.color.rot_fresh)
    private var sweep = 0f
    private var targetSweep = 0f
    private var animator: ValueAnimator? = null

    fun setScore(score: Int, level: String, color: Int) {
        stopAnimation()
        targetScore = score.coerceAtLeast(0)
        targetSweep = (targetScore.toDouble() / 300.0).coerceIn(0.0, 1.0).toFloat() * 360f
        levelName = level
        ringColor = color
        rebuildGradient()
        contentDescription = context.getString(R.string.ring_description, targetScore, level)
        shownScore = "0"
        sweep = 0f
        if (!isAttachedToWindow || windowVisibility != VISIBLE) {
            shownScore = targetScore.toString()
            sweep = targetSweep
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Float
                shownScore = (targetScore.toDouble() * progress).roundToInt().toString()
                sweep = targetSweep * progress
                invalidate()
            }
            start()
        }
    }

    /** Called by Home before releasing its view, and by the view when it is detached. */
    fun stopAnimation() {
        animator?.removeAllUpdateListeners()
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        shownScore = targetScore.toString()
        sweep = targetSweep
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferred = (280 * density).roundToInt()
        setMeasuredDimension(resolveSize(preferred, widthMeasureSpec), resolveSize(preferred, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = (paddingLeft + w - paddingRight) / 2f
        val cy = (paddingTop + h - paddingBottom) / 2f
        val radius = (minOf(w - paddingLeft - paddingRight, h - paddingTop - paddingBottom) / 2f - stroke).coerceAtLeast(0f)
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        rebuildGradient()
    }

    private fun rebuildGradient() {
        val surface = ContextCompat.getColor(context, R.color.rot_surface)
        val text = ContextCompat.getColor(context, R.color.rot_text_primary)
        val gradient = SweepGradient(bounds.centerX(), bounds.centerY(), intArrayOf(
            ColorUtils.blendARGB(ringColor, surface, 0.45f), ringColor,
            ColorUtils.blendARGB(ringColor, text, 0.2f), ringColor
        ), floatArrayOf(0f, 0.55f, 0.85f, 1f))
        gradientMatrix.setRotate(-90f, bounds.centerX(), bounds.centerY())
        gradient.setLocalMatrix(gradientMatrix)
        arc.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bounds.width() <= 0f) return
        canvas.drawOval(bounds, track)
        if (sweep > 0f) canvas.drawArc(bounds, -90f, sweep.coerceAtMost(360f), false, arc)
        number.color = ringColor
        number.textSize = sp(64f)
        val available = (bounds.width() - stroke - 28 * density).coerceAtLeast(1f)
        val numberWidth = number.measureText(shownScore)
        if (numberWidth > available) number.textSize *= available / numberWidth
        val metrics = number.fontMetrics
        val baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f - 8 * density
        canvas.drawText(shownScore, bounds.centerX(), baseline, number)
        label.textSize = sp(14f)
        val labelWidth = label.measureText(levelName)
        if (labelWidth > available) label.textSize *= available / labelWidth
        canvas.drawText(levelName, bounds.centerX(), baseline + metrics.descent + 28 * density, label)
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
