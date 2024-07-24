package com.universe.audioflare.data.model.explore.mood.genre

import com.universe.audioflare.data.model.searchResult.songs.Artist

data class ItemsSong(
    val title: String,
    val artist: List<Artist>?,
    val videoId: String,
)
