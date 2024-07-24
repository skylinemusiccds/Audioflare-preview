package com.universe.audioflare.data.model.explore.mood.genre


import com.google.gson.annotations.SerializedName
import com.universe.audioflare.data.model.searchResult.songs.Thumbnail

data class Content(
    @SerializedName("playlistBrowseId")
    val playlistBrowseId: String,
    @SerializedName("thumbnail")
    val thumbnail: List<Thumbnail>?,
    @SerializedName("title")
    val title: Title
)