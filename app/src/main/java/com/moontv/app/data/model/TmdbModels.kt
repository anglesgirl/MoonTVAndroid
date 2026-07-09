package com.moontv.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TMDB 影视元数据（替代豆瓣爬取的合规方案）
 * 文档: https://developer.themoviedb.org/reference/intro
 */
@Serializable
data class TmdbSearchResponse(
    val page: Int = 1,
    val results: List<TmdbItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0
)

@Serializable
data class TmdbItem(
    val id: Long = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    val overview: String = "",
    @SerialName("media_type") val mediaType: String = "movie"
) {
    val displayTitle: String get() = title ?: name ?: originalTitle ?: originalName ?: ""
    val displayYear: String get() = releaseDate.take(4).ifEmpty { firstAirDate.take(4) }
}

@Serializable
data class TmdbDetail(
    val id: Long = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    val genres: List<TmdbGenre> = emptyList(),
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val seasons: List<TmdbSeason> = emptyList()
) {
    val displayTitle: String get() = title ?: name ?: ""
    val displayYear: String get() = releaseDate.take(4).ifEmpty { firstAirDate.take(4) }
}

@Serializable
data class TmdbGenre(val id: Int = 0, val name: String = "")

@Serializable
data class TmdbSeason(
    val id: Int = 0,
    val name: String = "",
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String = ""
)
