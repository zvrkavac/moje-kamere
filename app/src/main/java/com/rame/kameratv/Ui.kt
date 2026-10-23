package com.rame.kameratv

/*
 * Moje Kamere - UI theming helpers.
 * Copyright (c) 2026 Izudin Ramic (Rame).
 */

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

object Ui {

    private const val TILE_INNER = 0xFF1C1F27.toInt()
    private const val BORDER = 0xFF2A2E38.toInt()

    private fun dp(v: Int): Int = (v * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    /** Focus-aware background for camera tiles (accent stroke when focused). */
    fun tileBg(accent: Int): StateListDrawable {
        val focused = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(TILE_INNER)
            setStroke(dp(4), accent)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(TILE_INNER)
            setStroke(dp(1), BORDER)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(), normal)
        }
    }

    /** Focus-aware background for control buttons (accent fill when focused). */
    fun buttonBg(accent: Int): StateListDrawable {
        val focused = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(accent)
            setStroke(dp(3), Color.WHITE)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(0xFF23262F.toInt())
            setStroke(dp(1), BORDER)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(), normal)
        }
    }
}
