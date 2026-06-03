package com.metrolist.kugou.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchSongResponse(
    val status: Int = 0,
    val errcode: Int = 0,
    val error: String = "",
    val data: Data = Data(),
) {
    @Serializable
    data class Data(
        val info: List<Info> = emptyList(),
    ) {
        @Serializable
        data class Info(
            val duration: Int,
            val hash: String,
        )
    }
}
