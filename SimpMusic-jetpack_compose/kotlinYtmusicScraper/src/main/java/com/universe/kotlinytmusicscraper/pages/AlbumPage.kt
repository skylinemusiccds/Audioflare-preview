package com.universe.kotlinytmusicscraper.pages

import com.universe.kotlinytmusicscraper.models.Album
import com.universe.kotlinytmusicscraper.models.AlbumItem
import com.universe.kotlinytmusicscraper.models.Artist
import com.universe.kotlinytmusicscraper.models.MusicResponsiveListItemRenderer
import com.universe.kotlinytmusicscraper.models.SongItem
import com.universe.kotlinytmusicscraper.models.Thumbnail
import com.universe.kotlinytmusicscraper.models.Thumbnails
import com.universe.kotlinytmusicscraper.models.oddElements
import com.universe.kotlinytmusicscraper.utils.parseTime

data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem>,
    val description: String?,
    val thumbnails: Thumbnails?,
    val duration: String?,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer?, album: AlbumItem): SongItem? {
            if (renderer == null) return null
            else {
                return SongItem(
                    id = renderer.playlistItemData?.videoId ?: return null,
                    title = renderer.flexColumns.firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                        ?.firstOrNull()?.text ?: return null,
                    artists = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.oddElements()
                        ?.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId
                            )
                        } ?: album.artists ?: emptyList(),
                    album = Album(
                        name = album.title,
                        id = album.id
                    ),
                    duration = renderer.fixedColumns?.firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                        ?.text?.parseTime() ?: return null,
                    thumbnail = album.thumbnail,
                    thumbnails = Thumbnails(
                        thumbnails = listOf(Thumbnail(
                            url = album.thumbnail,
                            width = 544,
                            height = 544
                        ))
                    ),
                    explicit = renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null
                )
            }
        }
    }
}