package com.smartisan.music.ui.shell.titlebar

import android.view.View
import android.widget.CheckBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartisan.music.R
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.shell.LegacyArtistTarget
import com.smartisan.music.ui.shell.showsAlbumSwitch
import com.smartisan.music.ui.widgets.legacy.TitleBar

@Composable
internal fun LegacyPortTitleBar(
    destination: MusicDestination,
    songsEditMode: Boolean,
    selectedSongCount: Int,
    albumEditMode: Boolean,
    selectedAlbumCount: Int,
    albumDetailTitle: String?,
    albumViewMode: AlbumViewMode,
    artistTarget: LegacyArtistTarget?,
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
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LegacyPortSmartisanTitleBar(modifier = modifier) { titleBar ->
        titleBar.setupLegacyMainTitleBar(
            destination = destination,
            songsEditMode = songsEditMode,
            selectedSongCount = selectedSongCount,
            albumEditMode = albumEditMode,
            selectedAlbumCount = selectedAlbumCount,
            albumDetailTitle = albumDetailTitle,
            albumViewMode = albumViewMode,
            artistTarget = artistTarget,
            artistAlbumViewMode = artistAlbumViewMode,
            onEnterSongsEditMode = onEnterSongsEditMode,
            onExitSongsEditMode = onExitSongsEditMode,
            onRequestDeleteSelected = onRequestDeleteSelected,
            onEnterAlbumEditMode = onEnterAlbumEditMode,
            onExitAlbumEditMode = onExitAlbumEditMode,
            onToggleAlbumViewMode = onToggleAlbumViewMode,
            onAlbumDetailBack = onAlbumDetailBack,
            onArtistBack = onArtistBack,
            onToggleArtistAlbumViewMode = onToggleArtistAlbumViewMode,
            onSearchClick = onSearchClick,
        )
    }
}

@Composable
internal fun LegacyPortSearchDetailTitleBar(
    destination: MusicDestination,
    albumDetailTitle: String?,
    artistTarget: LegacyArtistTarget?,
    onBack: () -> Unit,
    artistAlbumViewMode: AlbumViewMode = AlbumViewMode.List,
    onToggleArtistAlbumViewMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LegacyPortTitleBar(
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
        onSearchClick = {},
        modifier = modifier,
    )
}

private fun TitleBar.setupLegacyMainTitleBar(
    destination: MusicDestination,
    songsEditMode: Boolean,
    selectedSongCount: Int,
    albumEditMode: Boolean,
    selectedAlbumCount: Int,
    albumDetailTitle: String?,
    albumViewMode: AlbumViewMode,
    artistTarget: LegacyArtistTarget?,
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
    onSearchClick: () -> Unit,
) {
    removeAllLeftViews()
    removeAllRightViews()
    setShadowVisible(false)
    val centerText = when {
        albumDetailTitle != null -> albumDetailTitle
        artistTarget != null -> artistTarget.title
        else -> context.getString(destination.labelRes)
    }
    setCenterText(centerText)

    if (destination == MusicDestination.Album && albumDetailTitle != null) {
        addLeftImageView(R.drawable.standard_icon_back_selector).apply {
            setOnClickListener {
                onAlbumDetailBack()
            }
        }
        return
    }

    if (destination == MusicDestination.Artist && artistTarget != null) {
        addLeftImageView(R.drawable.standard_icon_back_selector).apply {
            setOnClickListener {
                onArtistBack()
            }
        }
        if (artistTarget.showsAlbumSwitch) {
            val switchButton = CheckBox(context, null).apply {
                setButtonDrawable(R.drawable.album_switch_selector)
                background = null
                isChecked = artistAlbumViewMode == AlbumViewMode.List
                setOnClickListener {
                    onToggleArtistAlbumViewMode()
                }
            }
            addRightView(switchButton)
        }
        return
    }

    if (destination == MusicDestination.Songs && songsEditMode) {
        addLeftImageView(R.drawable.standard_icon_cancel_selector).apply {
            setOnClickListener {
                onExitSongsEditMode()
            }
        }
        addRightImageView(R.drawable.titlebar_btn_delete_selector).apply {
            isEnabled = selectedSongCount > 0
            setOnClickListener {
                if (selectedSongCount > 0) {
                    onRequestDeleteSelected()
                }
            }
        }
        return
    }

    if (destination == MusicDestination.Album && albumEditMode) {
        addLeftImageView(R.drawable.standard_icon_cancel_selector).apply {
            setOnClickListener {
                onExitAlbumEditMode()
            }
        }
        addRightImageView(R.drawable.titlebar_btn_delete_selector).apply {
            isEnabled = selectedAlbumCount > 0
        }
        return
    }

    when (destination) {
        MusicDestination.More -> {
            addLeftImageView(R.drawable.standard_icon_settings_selector)
            addRightImageView(R.drawable.search_btn_selector).apply {
                setOnClickListener {
                    onSearchClick()
                }
            }
        }
        MusicDestination.Artist -> {
            addLeftImageView(R.drawable.standard_icon_multi_select_selector).visibility = View.INVISIBLE
            addRightImageView(R.drawable.search_btn_selector).apply {
                setOnClickListener {
                    onSearchClick()
                }
            }
        }
        else -> {
            addLeftImageView(R.drawable.standard_icon_multi_select_selector).apply {
                setOnClickListener {
                    when (destination) {
                        MusicDestination.Songs -> {
                            onEnterSongsEditMode()
                        }
                        MusicDestination.Album -> {
                            onEnterAlbumEditMode()
                        }
                        else -> Unit
                    }
                }
            }
            addRightImageView(R.drawable.search_btn_selector).apply {
                setOnClickListener {
                    onSearchClick()
                }
            }
            if (destination == MusicDestination.Album) {
                val switchButton = CheckBox(context, null).apply {
                    setButtonDrawable(R.drawable.album_switch_selector)
                    background = null
                    isChecked = albumViewMode == AlbumViewMode.List
                    setOnClickListener {
                        onToggleAlbumViewMode()
                    }
                }
                addRightView(switchButton)
            }
        }
    }
}
