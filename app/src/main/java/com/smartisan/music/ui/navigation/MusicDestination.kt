package com.smartisan.music.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.smartisan.music.R

enum class MusicDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val bottomIconRes: Int,
    @param:DrawableRes val overflowIconRes: Int,
    val movable: Boolean = true,
) {
    Playlist(
        route = "playlist",
        labelRes = R.string.tab_play_list,
        bottomIconRes = R.drawable.tabbar_playlist_selector,
        overflowIconRes = R.drawable.morepage_playlist_selector,
    ),
    Artist(
        route = "artist",
        labelRes = R.string.tab_artist,
        bottomIconRes = R.drawable.tabbar_artist_selector,
        overflowIconRes = R.drawable.morepage_artist_selector,
    ),
    Album(
        route = "album",
        labelRes = R.string.tab_album,
        bottomIconRes = R.drawable.tabbar_album_selector,
        overflowIconRes = R.drawable.morepage_album_selector,
    ),
    Songs(
        route = "songs",
        labelRes = R.string.tab_song,
        bottomIconRes = R.drawable.tabbar_song_selector,
        overflowIconRes = R.drawable.morepage_song_selector,
    ),
    Genre(
        route = "genre",
        labelRes = R.string.tab_style,
        bottomIconRes = R.drawable.tabbar_style_selector,
        overflowIconRes = R.drawable.morepage_style_selector,
    ),
    LovedSongs(
        route = "loved_songs",
        labelRes = R.string.collect_music,
        bottomIconRes = R.drawable.tabbar_like_selector,
        overflowIconRes = R.drawable.morepage_like_selector,
    ),
    Folder(
        route = "folder",
        labelRes = R.string.tab_directory,
        bottomIconRes = R.drawable.tabbar_folder_selector,
        overflowIconRes = R.drawable.morepage_folder_selector,
    ),
    More(
        route = "more",
        labelRes = R.string.tab_more,
        bottomIconRes = R.drawable.tabbar_more_selector,
        overflowIconRes = R.drawable.tabbar_more_selector,
        movable = false,
    );

    companion object {
        val movableEntries: List<MusicDestination> = entries.filter(MusicDestination::movable)

        fun fromRoute(route: String): MusicDestination? = entries.firstOrNull { it.route == route }
    }
}
