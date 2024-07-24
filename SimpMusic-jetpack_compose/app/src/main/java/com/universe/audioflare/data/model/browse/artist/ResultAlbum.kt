package com.universe.audioflare.data.model.browse.artist


import com.google.gson.annotations.SerializedName
import com.universe.audioflare.data.model.searchResult.songs.Thumbnail

data class ResultAlbum(
    @SerializedName("browseId")
    val browseId: String,
    @SerializedName("isExplicit")
    val isExplicit: Boolean,
    @SerializedName("thumbnails")
    val thumbnails: List<Thumbnail>,
    @SerializedName("title")
    val title: String,
    @SerializedName("year")
    val year: String
)