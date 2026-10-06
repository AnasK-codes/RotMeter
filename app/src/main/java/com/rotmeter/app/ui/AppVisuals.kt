package com.rotmeter.app.ui

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.rotmeter.app.R
import java.util.Locale

/** Presentation only; colours do not affect Rot Score or limit calculations. */
object AppVisuals {
    fun colorRes(weight: Double): Int = when {
        weight < 0 -> R.color.rot_cyan_text
        weight >= 0.7 -> R.color.rot_error_text
        weight > 0 -> R.color.rot_mild
        else -> R.color.rot_accent_text
    }

    fun showInitial(view: TextView, label: String, weight: Double) {
        val color = ContextCompat.getColor(view.context, colorRes(weight))
        view.text = label.trim().take(1).uppercase(Locale.US).ifEmpty { "?" }
        view.setTextColor(color)
        view.backgroundTintList = ColorStateList.valueOf(ColorUtils.blendARGB(
            color, ContextCompat.getColor(view.context, R.color.rot_surface), 0.88f))
    }
}
