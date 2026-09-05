package com.smartisan.music.ui.more

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.smartisan.music.R
import com.smartisan.music.ui.components.SmartisanTitleBar
import com.smartisan.music.ui.components.SmartisanTitleBarAction
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.components.smartisanStateColor
import com.smartisan.music.ui.components.smartisanTextSize
import com.smartisan.music.ui.navigation.MusicDestination
import kotlin.math.roundToInt

@Composable
internal fun SmartisanMoreRootPage(
    active: Boolean,
    destinations: List<MusicDestination>,
    onDestinationSelected: (MusicDestination) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep scroll ownership outside the visible subtree: inactive tabs must have no
    // focus, touch targets or accessibility nodes, but return to the same position.
    val listState = rememberLazyListState()
    val entrance = remember { Animatable(0f) }
    val isPreview = LocalInspectionMode.current
    var entranceFinished by remember { mutableStateOf(isPreview) }
    val fadeDuration = integerResource(R.integer.item_flip)
    val staggerMillis = (fadeDuration * MoreRowEntranceStaggerFraction).toInt()
    var entranceDuration by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active || entranceFinished) return@LaunchedEffect
        if (entranceDuration == 0) {
            entranceDuration =
                fadeDuration + (destinations.size - 1).coerceAtLeast(0) * staggerMillis
        }
        // Hidden tabs do not spend their entrance animation. A quick tab switch
        // cancels this effect; returning resumes only the remaining visible time.
        entrance.animateTo(
            targetValue = entranceDuration.toFloat(),
            animationSpec =
                tween(
                    durationMillis =
                        (entranceDuration - entrance.value).roundToInt().coerceAtLeast(1),
                    easing = LinearEasing,
                ),
        )
        entranceFinished = true
    }

    if (!active) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    Column(modifier = modifier.fillMaxSize().background(colorResource(R.color.page_background))) {
        SmartisanTitleBar(
            title = stringResource(R.string.tab_more),
            navigationIcon =
                SmartisanTitleBarAction(
                    iconRes = R.drawable.standard_icon_settings_selector,
                    contentDescription = stringResource(R.string.setting),
                    onClick = onSettingsClick,
                ),
            action =
                SmartisanTitleBarAction(
                    iconRes = R.drawable.search_btn_selector,
                    contentDescription = stringResource(R.string.tab_local_search),
                    onClick = onSearchClick,
                ),
        )
        LazyColumn(
            state = listState,
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .smartisanPainterBackground(
                        painter = rememberSmartisanDrawablePainter(R.drawable.account_background)
                    ),
        ) {
            itemsIndexed(destinations, key = { _, destination -> destination.route }) {
                index,
                destination ->
                SmartisanMoreDestinationRow(
                    destination = destination,
                    onClick = { onDestinationSelected(destination) },
                    modifier =
                        Modifier.graphicsLayer {
                            // list_anim_layout.xml: 20% stagger; fade_in.xml uses the
                            // platform AccelerateInterpolator's default quadratic curve.
                            val fraction =
                                if (entranceFinished) {
                                    1f
                                } else {
                                    ((entrance.value - index * staggerMillis) / fadeDuration)
                                        .coerceIn(0f, 1f)
                                }
                            alpha = fraction * fraction
                        },
                )
            }
        }
    }
}

@Composable
private fun SmartisanMoreDestinationRow(
    destination: MusicDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val iconArea = dimensionResource(R.dimen.left_icon_area_width)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // more_item.xml / list_content_item_layout.xml. The old ListView uses
    // AbsListView.LayoutParams, so the style's horizontal margins were not applied.
    // The XML uses physical left/right placement even in RTL; only text follows locale.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.list_item_min_height))
                    .smartisanPainterBackground(
                        painter =
                            rememberSmartisanDrawablePainter(
                                R.drawable.group_list_item_bg_mid,
                                pressed = pressed,
                                focused = focused,
                            )
                    )
                    .clickable(
                        interactionSource = interactions,
                        indication = null,
                        role = Role.Button,
                        onClick = smartisanClick(onClick),
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(iconArea),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter =
                        rememberSmartisanDrawablePainter(
                            destination.overflowIconRes,
                            pressed = pressed,
                            focused = focused,
                        ),
                    contentDescription = null,
                    modifier =
                        Modifier.sizeIn(
                            maxWidth = MoreIconMaximumSize,
                            maxHeight = MoreIconMaximumSize,
                        ),
                )
            }
            BasicText(
                text = stringResource(destination.labelRes),
                modifier =
                    Modifier.weight(1f)
                        .padding(
                            vertical = dimensionResource(R.dimen.mid_container_top_bottom_padding)
                        ),
                style =
                    TextStyle(
                        color =
                            smartisanStateColor(
                                R.color.setting_item_text_colorlist,
                                pressed = pressed,
                                focused = focused,
                            ),
                        fontSize = smartisanTextSize(R.dimen.primary_text_size),
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        textDirection =
                            if (isRtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Image(
                painter =
                    rememberSmartisanDrawablePainter(
                        R.drawable.selector_list_content_item_arrow,
                        pressed = pressed,
                        focused = focused,
                    ),
                contentDescription = null,
                modifier =
                    Modifier.padding(
                        start = dimensionResource(R.dimen.flexible_space),
                        end = dimensionResource(R.dimen.right_container_margin),
                    ),
            )
        }
    }
}

// list_content_left_image_view.xml and list_anim_layout.xml, respectively.
private val MoreIconMaximumSize = 36.dp
private const val MoreRowEntranceStaggerFraction = 0.2f

@Preview(name = "More · day", widthDp = 360, heightDp = 640)
@Preview(
    name = "More · night",
    widthDp = 360,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "More · large text", widthDp = 360, heightDp = 640, fontScale = 1.5f)
@Composable
private fun SmartisanMoreRootPagePreview() {
    SmartisanMoreRootPage(
        active = true,
        destinations =
            listOf(MusicDestination.Genre, MusicDestination.LovedSongs, MusicDestination.Folder),
        onDestinationSelected = {},
        onSettingsClick = {},
        onSearchClick = {},
    )
}
