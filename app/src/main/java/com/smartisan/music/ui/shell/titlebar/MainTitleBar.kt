package com.smartisan.music.ui.shell.titlebar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartisan.music.R
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.artist.ArtistTarget
import com.smartisan.music.ui.artist.showsAlbumSwitch
import com.smartisan.music.ui.navigation.MusicDestination

@Composable
internal fun MainTitleBar(
    destination: MusicDestination,
    songsEditMode: Boolean,
    selectedSongCount: Int,
    albumEditMode: Boolean,
    selectedAlbumCount: Int,
    albumDetailTitle: String?,
    albumViewMode: AlbumViewMode,
    artistTarget: ArtistTarget?,
    artistAlbumViewMode: AlbumViewMode,
    onEnterSongsEditMode: () -> Unit,
    onExitSongsEditMode: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onEnterAlbumEditMode: () -> Unit,
    onExitAlbumEditMode: () -> Unit,
    onToggleAlbumViewMode: () -> Unit,
    onAlbumDetailBack: () -> Unit,
    onArtistBack: () -> Unit,
    onToggleArtistAlbumViewMode: () -> Unit,
    onRootBack: (() -> Unit)?,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val backLabel = resources.getString(R.string.back)
    val title = albumDetailTitle ?: artistTarget?.title ?: resources.getString(destination.labelRes)
    fun icon(
        resource: Int,
        description: String,
        onClick: () -> Unit,
        enabled: Boolean = true,
        checked: Boolean? = null,
    ) =
        com.smartisan.music.ui.components.SmartisanTitleBarAction(
            resource,
            description,
            onClick,
            enabled,
            checked,
        )
    val left = mutableListOf<com.smartisan.music.ui.components.SmartisanTitleBarAction>()
    val right = mutableListOf<com.smartisan.music.ui.components.SmartisanTitleBarAction>()
    when {
        destination == MusicDestination.Album && albumDetailTitle != null ->
            left += icon(R.drawable.standard_icon_back_selector, backLabel, onAlbumDetailBack)
        destination == MusicDestination.Artist && artistTarget != null -> {
            left += icon(R.drawable.standard_icon_back_selector, backLabel, onArtistBack)
            if (artistTarget.showsAlbumSwitch)
                right +=
                    icon(
                        R.drawable.album_switch_selector,
                        resources.getString(R.string.tab_album),
                        onToggleArtistAlbumViewMode,
                        checked = artistAlbumViewMode == AlbumViewMode.List,
                    )
        }
        destination == MusicDestination.Songs && songsEditMode -> {
            left +=
                icon(
                    R.drawable.standard_icon_cancel_selector,
                    resources.getString(R.string.cancel),
                    onExitSongsEditMode,
                )
            right +=
                icon(
                    R.drawable.titlebar_btn_delete_selector,
                    resources.getString(R.string.delete),
                    onRequestDeleteSelected,
                    selectedSongCount > 0,
                )
        }
        destination == MusicDestination.Album && albumEditMode -> {
            left +=
                icon(
                    R.drawable.standard_icon_cancel_selector,
                    resources.getString(R.string.cancel),
                    onExitAlbumEditMode,
                )
            right +=
                icon(
                    R.drawable.titlebar_btn_delete_selector,
                    resources.getString(R.string.delete),
                    {},
                    selectedAlbumCount > 0,
                )
        }
        else -> {
            when {
                onRootBack != null ->
                    left += icon(R.drawable.standard_icon_back_selector, backLabel, onRootBack)
                destination == MusicDestination.More ->
                    left +=
                        icon(
                            R.drawable.standard_icon_settings_selector,
                            resources.getString(R.string.setting),
                            {},
                        )
                destination != MusicDestination.Artist ->
                    left +=
                        icon(
                            R.drawable.standard_icon_multi_select_selector,
                            resources.getString(R.string.edit),
                            if (destination == MusicDestination.Album) onEnterAlbumEditMode
                            else onEnterSongsEditMode,
                        )
            }
            if (
                onRootBack != null &&
                    (destination == MusicDestination.Songs || destination == MusicDestination.Album)
            ) {
                right +=
                    icon(
                        R.drawable.standard_icon_multi_select_selector,
                        resources.getString(R.string.edit),
                        if (destination == MusicDestination.Album) onEnterAlbumEditMode
                        else onEnterSongsEditMode,
                    )
            }
            right +=
                icon(
                    R.drawable.search_btn_selector,
                    resources.getString(R.string.tab_local_search),
                    onSearchClick,
                )
            if (destination == MusicDestination.Album)
                right +=
                    icon(
                        R.drawable.album_switch_selector,
                        resources.getString(R.string.tab_album),
                        onToggleAlbumViewMode,
                        checked = albumViewMode == AlbumViewMode.List,
                    )
        }
    }
    com.smartisan.music.ui.components.SmartisanTitleBar(
        title,
        modifier,
        navigationActions = left,
        actions = right,
        showShadow = false,
    )
}

@Composable
internal fun SearchDetailTitleBar(
    destination: MusicDestination,
    albumDetailTitle: String?,
    artistTarget: ArtistTarget?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    artistAlbumViewMode: AlbumViewMode = AlbumViewMode.List,
    onToggleArtistAlbumViewMode: () -> Unit = {},
) {
    MainTitleBar(
        destination = destination,
        songsEditMode = false,
        selectedSongCount = 0,
        albumEditMode = false,
        selectedAlbumCount = 0,
        albumDetailTitle = albumDetailTitle,
        albumViewMode = AlbumViewMode.List,
        artistTarget = artistTarget,
        artistAlbumViewMode = artistAlbumViewMode,
        onEnterSongsEditMode = {},
        onExitSongsEditMode = {},
        onRequestDeleteSelected = {},
        onEnterAlbumEditMode = {},
        onExitAlbumEditMode = {},
        onToggleAlbumViewMode = {},
        onAlbumDetailBack = onBack,
        onArtistBack = onBack,
        onToggleArtistAlbumViewMode = onToggleArtistAlbumViewMode,
        onRootBack = null,
        onSearchClick = {},
        modifier = modifier,
    )
}
