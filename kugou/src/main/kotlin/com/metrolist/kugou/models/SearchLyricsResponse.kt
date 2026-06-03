package com.metrolist.kugou.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchLyricsResponse(
    val status: Int = 0,
    val info: String = "",
    val errcode: Int = 0,
    val errmsg: String = "",
    val expire: Int = 0,
    val candidates: List<Candidate> = emptyList(),
) {
    @Serializable
    data class Candidate(
        val id: Long,
        @SerialName("product_from") val productFrom: String = "",
        val duration: Long = 0,
        val accesskey: String,
    )
}
