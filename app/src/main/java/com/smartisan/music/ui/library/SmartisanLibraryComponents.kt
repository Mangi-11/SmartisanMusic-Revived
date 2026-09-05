package com.smartisan.music.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.artwork.AlbumArtworkLoader
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.components.smartisanStateColor
import com.smartisan.music.ui.components.smartisanTextSize

@Composable
internal fun rememberAlbumArtworkLoader(): AlbumArtworkLoader {
    val context = LocalContext.current
    val loader =
        remember(context.applicationContext) { AlbumArtworkLoader(context.applicationContext) }
    DisposableEffect(loader) { onDispose { loader.clear() } }
    return loader
}

@Composable
internal fun SmartisanMediaArtwork(
    mediaItem: MediaItem?,
    sizePx: Int,
    @DrawableRes fallbackRes: Int,
    modifier: Modifier = Modifier,
    loader: AlbumArtworkLoader = rememberAlbumArtworkLoader(),
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by
        key(loader, mediaItem, sizePx) {
            produceState(mediaItem?.let(loader::cached)) {
                value = mediaItem?.let { loader.load(it, sizePx.coerceAtLeast(1)) }
            }
        }
    val image = bitmap
    if (image != null) {
        Image(
            image.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Image(
            rememberSmartisanDrawablePainter(fallbackRes),
            null,
            modifier,
            contentScale = contentScale,
        )
    }
}

@Composable
internal fun SmartisanAlbumArtwork(
    album: AlbumSummary?,
    sizePx: Int,
    @DrawableRes fallbackRes: Int,
    modifier: Modifier = Modifier,
    loader: AlbumArtworkLoader = rememberAlbumArtworkLoader(),
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by
        key(loader, album, sizePx) {
            produceState(album?.let(loader::cached)) {
                value = album?.let { loader.load(it, sizePx.coerceAtLeast(1)) }
            }
        }
    val image = bitmap
    if (image != null) {
        Image(image.asImageBitmap(), null, modifier, contentScale = contentScale)
    } else {
        Image(
            rememberSmartisanDrawablePainter(fallbackRes),
            null,
            modifier,
            contentScale = contentScale,
        )
    }
}

@Composable
internal fun Modifier.libraryTexture(
    @DrawableRes resource: Int = R.drawable.account_background
): Modifier = smartisanPainterBackground(rememberSmartisanDrawablePainter(resource))

@Composable
internal fun LibraryDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.listview_dividerHeight))
            .background(colorResource(R.color.listview_divider_color))
    )
}

@Composable
internal fun LibraryFooter(@PluralsRes resource: Int, count: Int, modifier: Modifier = Modifier) {
    if (count < 8) return
    BasicText(
        pluralStringResource(resource, count, count),
        modifier
            .fillMaxWidth()
            .background(colorResource(R.color.page_background))
            .padding(vertical = dimensionResource(R.dimen.footer_padding)),
        style =
            TextStyle(
                color = colorResource(R.color.footer_text_color),
                fontSize = smartisanTextSize(R.dimen.footer_text_size),
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = true),
            ),
    )
}

@Composable
internal fun LibraryBlank(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().libraryTexture(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            rememberSmartisanDrawablePainter(icon),
            null,
            Modifier.graphicsLayer { alpha = .42f }.padding(bottom = 16.dp),
        )
        BasicText(
            title,
            style =
                TextStyle(
                    color = colorResource(R.color.text_disabled_gray),
                    fontSize = 25.sp,
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
        )
        if (subtitle.isNotBlank())
            BasicText(
                subtitle,
                Modifier.padding(top = 10.dp),
                style =
                    TextStyle(
                        color = colorResource(R.color.text_disabled_gray),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
            )
    }
}

@Composable
internal fun LibrarySummaryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = false,
    primarySizeRes: Int = R.dimen.text_size_medium,
    secondarySizeRes: Int = R.dimen.text_size_small,
    lineSpacing: androidx.compose.ui.unit.Dp =
        dimensionResource(R.dimen.listview_items_margin_top1),
    titleColor: Color = colorResource(R.color.setting_item_text_color),
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    textInset: androidx.compose.ui.unit.Dp = dimensionResource(R.dimen.listview_items_margin_left),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.listview_item_height))
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(
                    R.drawable.listview_selector,
                    pressed = pressed,
                    focused = focused,
                )
            )
            .clickable(interaction, null, role = Role.Button, onClick = smartisanClick(onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.invoke(this)
        Column(
            Modifier.weight(1f)
                .padding(
                    start = textInset,
                    end = dimensionResource(R.dimen.listview_items_padding_left_left1),
                )
        ) {
            BasicText(
                title,
                style =
                    TextStyle(
                        color = titleColor,
                        fontSize = smartisanTextSize(primarySizeRes),
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                subtitle,
                Modifier.padding(top = lineSpacing),
                style =
                    TextStyle(
                        color = colorResource(R.color.list_text_color_small),
                        fontSize = smartisanTextSize(secondarySizeRes),
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent?.invoke(this)
        if (showArrow)
            Image(
                rememberSmartisanDrawablePainter(
                    R.drawable.selector_list_content_item_arrow,
                    pressed = pressed,
                    focused = focused,
                ),
                null,
                Modifier.padding(end = 6.dp),
            )
    }
}

@Composable
internal fun LibraryPlayActions(
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(colorResource(R.color.page_background))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LibraryRedAction(
            stringResource(R.string.cd_play_all),
            R.drawable.btn_icon_play_selector,
            enabled,
            onPlay,
            Modifier.weight(1f),
        )
        LibraryRedAction(
            stringResource(R.string.cd_shuffle),
            R.drawable.btn_icon_shuffle_selector,
            enabled,
            onShuffle,
            Modifier.weight(1f),
        )
    }
    LibraryDivider()
}

@Composable
private fun LibraryRedAction(
    title: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .height(30.dp)
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(
                    R.drawable.btn_red_bg_selector,
                    enabled = enabled,
                    pressed = pressed,
                )
            )
            .clickable(
                interaction,
                null,
                enabled = enabled,
                role = Role.Button,
                onClick = smartisanClick(onClick),
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            rememberSmartisanDrawablePainter(icon, enabled = enabled, pressed = pressed),
            null,
            Modifier.padding(end = 10.dp),
        )
        BasicText(
            title,
            Modifier.padding(bottom = .67.dp),
            style =
                TextStyle(
                    color =
                        smartisanStateColor(
                            R.color.red_btn_text_color_selector,
                            enabled = enabled,
                            pressed = pressed,
                        ),
                    fontSize = smartisanTextSize(R.dimen.settings_item_tips_text_size),
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
        )
    }
}

@Composable
internal fun LibraryIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Image(
        rememberSmartisanDrawablePainter(
            icon,
            enabled = enabled,
            pressed = pressed,
            focused = focused,
        ),
        description,
        modifier.clickable(
            interaction,
            null,
            enabled = enabled,
            role = Role.Button,
            onClick = smartisanClick(onClick),
        ),
    )
}
