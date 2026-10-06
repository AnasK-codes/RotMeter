package com.rotmeter.app.util

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.rotmeter.app.R

enum class RotLevel(
    @get:StringRes val labelRes: Int,
    @get:ColorRes val colorRes: Int,
    @get:StringRes val roastRes: Int
) {
    FRESH_BRAIN(R.string.level_fresh, R.color.rot_fresh, R.string.roast_fresh),
    MILD_ROT(R.string.level_mild, R.color.rot_mild, R.string.roast_mild),
    SKIBIDI_TIER(R.string.level_skibidi, R.color.rot_skibidi, R.string.roast_skibidi),
    TERMINALLY_ONLINE(R.string.level_terminal, R.color.rot_terminal, R.string.roast_terminal);

    companion object {
        fun fromScore(score: Int): RotLevel = when {
            score < 50 -> FRESH_BRAIN
            score < 150 -> MILD_ROT
            score < 300 -> SKIBIDI_TIER
            else -> TERMINALLY_ONLINE
        }
    }
}
