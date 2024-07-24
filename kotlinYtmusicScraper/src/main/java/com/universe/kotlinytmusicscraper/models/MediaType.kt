package com.universe.kotlinytmusicscraper.models

sealed class MediaType {
    data object Song : MediaType()
    data object Video : MediaType()
}