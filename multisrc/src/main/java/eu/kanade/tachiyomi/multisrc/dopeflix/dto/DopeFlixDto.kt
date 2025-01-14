package eu.kanade.tachiyomi.multisrc.dopeflix.dto

import kotlinx.serialization.Serializable

@Serializable
data class VideoDto(
    val sources: List<VideoLink>,
    val tracks: List<TrackDto>?,
)

@Serializable
data class VideoLink(val file: String = "")

@Serializable
data class TrackDto(val file: String, val kind: String, val label: String = "")
