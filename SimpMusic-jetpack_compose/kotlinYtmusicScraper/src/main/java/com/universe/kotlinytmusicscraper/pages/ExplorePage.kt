package com.universe.kotlinytmusicscraper.pages

import com.universe.kotlinytmusicscraper.models.PlaylistItem
import com.universe.kotlinytmusicscraper.models.VideoItem

data class ExplorePage(
    val released: List<PlaylistItem>,
    val musicVideo: List<VideoItem>,
)
