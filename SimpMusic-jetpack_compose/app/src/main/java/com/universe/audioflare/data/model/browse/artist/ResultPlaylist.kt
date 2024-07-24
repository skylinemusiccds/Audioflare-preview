package com.universe.audioflare.data.model.browse.artist

import com.universe.audioflare.data.model.searchResult.songs.Thumbnail

data class ResultPlaylist(
    val id: String,
    val author: String,
    val thumbnails: List<Thumbnail>,
    val title: String,
) {
}