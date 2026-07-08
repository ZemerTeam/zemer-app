package com.jtech.zemer.supabase

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseRepository @Inject constructor() {
    private val supabaseUrl = "https://aometextgaovquqcrrgv.supabase.co"
    private val anonKey = "sb_publishable_9wr1SUVQ5sjPY9b-hZnBBQ_SXSvDw3V"

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getTrendingWithComparison(): List<TrendingWithComparisonRow> {
        return client.post("$supabaseUrl/rest/v1/rpc/get_trending_with_comparison") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $anonKey")
            contentType(ContentType.Application.Json)
        }.body()
    }
}