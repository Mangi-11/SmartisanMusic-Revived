package com.smartisan.music.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.smartisan.music.R

enum class MusicDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val selectedIconRes: Int,
) {
    Playlist(
        route = "playlist",
        labelRes = R.string.tab_play_list,
        iconRes = R.drawable.tabbar_playlist,
        selectedIconRes = R.drawable.tabbar_playlist_down,
    ),
    Artist(
        route = "artist",
        labelRes = R.string.tab_artist,
        iconRes = R.drawable.tabbar_artist,
        selectedIconRes = R.drawable.tabbar_artist_down,
    ),
    Album(
        route = "album",
        labelRes = R.string.tab_album,
        iconRes = R.drawable.tabbar_album,
        selectedIconRes = R.drawable.tabbar_album_down,
    ),
    Songs(
        route = "songs",
        labelRes = R.string.tab_song,
        iconRes = R.drawable.tabbar_song,
        selectedIconRes = R.drawable.tabbar_song_down,
    ),
    More(
        route = "more",
        labelRes = R.string.tab_more,
        iconRes = R.drawable.tabbar_more,
        selectedIconRes = R.drawable.tabbar_more_down,
    );
}
