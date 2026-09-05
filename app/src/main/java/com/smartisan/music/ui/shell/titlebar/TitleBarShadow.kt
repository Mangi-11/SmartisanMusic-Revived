package com.smartisan.music.ui.shell.titlebar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartisan.music.R
import com.smartisan.music.ui.components.SmartisanDrawableBackground

@Composable
internal fun TitleBarShadow(modifier: Modifier = Modifier) {
    SmartisanDrawableBackground(R.drawable.title_bar_shadow, modifier)
}
