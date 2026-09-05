package com.smartisan.music.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisan.music.R

/** Empty hint used by the calibrated song and collection layouts. */
@Composable
internal fun SmartisanEmptyHint(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            rememberSmartisanDrawablePainter(icon),
            null,
            Modifier.padding(top = 40.dp).size(120.dp),
        )
        BasicText(
            title,
            Modifier.padding(start = 60.dp, end = 60.dp, top = 18.dp),
            style =
                TextStyle(
                    color = colorResource(R.color.editor_hint_text_color),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
        )
        if (subtitle != null)
            BasicText(
                subtitle,
                Modifier.padding(start = 60.dp, end = 60.dp, top = 5.dp, bottom = 40.dp),
                style =
                    TextStyle(
                        color = colorResource(R.color.editor_hint_text_color),
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
            )
    }
}
