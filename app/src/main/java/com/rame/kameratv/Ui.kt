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
            addState(intArrayOf(android.R.attr.state_hovered), focused)
            addState(intArrayOf(), normal)
        }
    }

    /** Focus/hover-aware background for buttons (accent fill when focused, hovered or pressed). */
    fun buttonBg(accent: Int): StateListDrawable {
        val active = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(accent)
            setStroke(dp(3), Color.WHITE)
        }
        // slightly lighter tint used only for mouse hover, so it is distinct from focus
        val hover = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(lighten(accent, 0.25f))
            setStroke(dp(2), Color.WHITE)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(0xFF23262F.toInt())
            setStroke(dp(1), BORDER)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), active)
            addState(intArrayOf(android.R.attr.state_focused), active)
            addState(intArrayOf(android.R.attr.state_hovered), hover)
            addState(intArrayOf(), normal)
        }
    }

    /**
     * Background for a solid colored action button (e.g. blue "Dodaj kameru").
     * Normal: the given color. Focused / hovered / pressed: a brighter shade with a
     * thick white ring, so it is obvious which button the remote or mouse is on.
     */
    fun actionBg(color: Int): StateListDrawable {
        val active = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(lighten(color, 0.30f))
            setStroke(dp(4), Color.WHITE)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(color)
            setStroke(dp(1), 0x33000000)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), active)
            addState(intArrayOf(android.R.attr.state_focused), active)
            addState(intArrayOf(android.R.attr.state_hovered), active)
            addState(intArrayOf(), normal)
        }
    }

    /**
     * Keeps a button's own [base] color (used for the column and theme-color buttons),
     * but adds a bright white ring when focused / hovered / pressed.
     */
    fun focusRing(base: Int): StateListDrawable {
        val active = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(base)
            setStroke(dp(4), Color.WHITE)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(base)
            setStroke(dp(1), 0x33000000)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), active)
            addState(intArrayOf(android.R.attr.state_focused), active)
            addState(intArrayOf(android.R.attr.state_hovered), active)
            addState(intArrayOf(), normal)
        }
    }

    /** Blend a color toward white by [amount] (0..1). */
    private fun lighten(color: Int, amount: Float): Int {
        val r = Color.red(color) + ((255 - Color.red(color)) * amount).toInt()
        val g = Color.green(color) + ((255 - Color.green(color)) * amount).toInt()
        val b = Color.blue(color) + ((255 - Color.blue(color)) * amount).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
