package com.universe.kotlinytmusicscraper.pages

import com.universe.kotlinytmusicscraper.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
