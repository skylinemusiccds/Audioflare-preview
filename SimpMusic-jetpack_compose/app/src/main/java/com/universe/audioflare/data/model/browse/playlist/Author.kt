package com.universe.audioflare.data.model.browse.playlist


import com.google.gson.annotations.SerializedName

data class Author(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String
)