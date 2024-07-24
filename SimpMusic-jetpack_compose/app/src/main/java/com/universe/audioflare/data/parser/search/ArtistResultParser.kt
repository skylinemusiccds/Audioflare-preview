package com.universe.audioflare.data.parser.search

import com.universe.kotlinytmusicscraper.models.ArtistItem
import com.universe.kotlinytmusicscraper.pages.SearchResult
import com.universe.audioflare.data.model.searchResult.artists.ArtistsResult
import com.universe.audioflare.data.model.searchResult.songs.Thumbnail

fun parseSearchArtist(result: SearchResult): ArrayList<ArtistsResult>{
    val artistsResult: ArrayList<ArtistsResult> = arrayListOf()
    result.items.forEach {
        val artist = it as ArtistItem
        artistsResult.add(
            ArtistsResult(
                artist = artist.title,
                browseId = artist.id,
                category = "Artist",
                radioId = artist.radioEndpoint?.playlistId ?: "",
                resultType = "Artist",
                shuffleId = artist.shuffleEndpoint?.playlistId ?: "",
                thumbnails = listOf(Thumbnail(544, Regex("([wh])120").replace(artist.thumbnail, "$1544"), 544))
            )
        )
    }
    return artistsResult
}