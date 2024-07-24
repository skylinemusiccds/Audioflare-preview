package com.universe.kotlinytmusicscraper.models

import com.universe.kotlinytmusicscraper.models.response.BrowseResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class SectionListRenderer(
    val header: Header?,
    val contents: List<Content>?,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Header(
        val chipCloudRenderer: ChipCloudRenderer?,
    ) {
        @Serializable
        data class ChipCloudRenderer(
            val chips: List<Chip>,
        ) {
            @Serializable
            data class Chip(
                val chipCloudChipRenderer: ChipCloudChipRenderer,
            ) {
                @Serializable
                data class ChipCloudChipRenderer(
                    val isSelected: Boolean? = null,
                    val navigationEndpoint: NavigationEndpoint,
                    // The close button doesn't have the following two fields
                    val text: Runs?,
                    val uniqueId: String?,
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class Content(
        @JsonNames("musicImmersiveCarouselShelfRenderer")
        val musicCarouselShelfRenderer: MusicCarouselShelfRenderer?,
        val musicShelfRenderer: MusicShelfRenderer?,
        val musicCardShelfRenderer: MusicCardShelfRenderer?,
        val musicPlaylistShelfRenderer: MusicPlaylistShelfRenderer?,
        val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer?,
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer?,
        val musicEditablePlaylistDetailHeaderRenderer: BrowseResponse.Header.MusicEditablePlaylistDetailHeaderRenderer?,
        val gridRenderer: GridRenderer?,
    ) {
        @Serializable
        data class MusicResponsiveHeaderRenderer(
            val description: Description?,
            val straplineTextOne: StraplineTextOne?,
            val straplineThumbnail: StraplineThumbnail?,
            val subtitle: MusicShelfRenderer.Content.MusicMultiRowListItemRenderer.Subtitle?,
            val thumbnail: ThumbnailRenderer?,
            val title: Title?,
            val secondSubtitle: MusicShelfRenderer.Content.MusicMultiRowListItemRenderer.Subtitle?
        ) {
            @Serializable
            data class Description(
                val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer?,
            )

            @Serializable
            data class StraplineTextOne(
                val runs: List<Run>?,
            )

            @Serializable
            data class StraplineThumbnail(
                val musicThumbnailRenderer: ThumbnailRenderer.MusicThumbnailRenderer?,
            )

            @Serializable
            data class Title(
                val runs: List<Run>?,
            )
        }
    }
}
