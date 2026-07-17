package com.smartisan.music.ui.shell

import android.content.Context
import android.view.View

internal fun View.dpPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.dpPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()
