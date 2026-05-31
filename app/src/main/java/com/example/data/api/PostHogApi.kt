package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import com.example.BuildConfig
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class RemoteDashboard(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "tiles") val tiles: Any? = null,
    @Json(name = "items") val items: Any? = null
)

@JsonClass(generateAdapter = true)
data class PostHogDashboardsResponse(
    @Json(name = "results") val results: List<RemoteDashboard>
)

@JsonClass(generateAdapter = true)
data class RemoteInsightSeries(
    @Json(name = "data") val data: List<Double>?,
    @Json(name = "labels") val labels: List<String>?,
    @Json(name = "label") val label: String?
)

@JsonClass(generateAdapter = true)
data class RemoteInsight(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "result") val result: Any?,
    @Json(name = "display") val display: String?,
    @Json(name = "dashboard") val dashboard: Int?,
    @Json(name = "dashboards") val dashboards: List<Int>?
)

@JsonClass(generateAdapter = true)
data class PostHogInsightsResponse(
    @Json(name = "results") val results: List<RemoteInsight>
)

interface PostHogApiService {
    @GET("api/projects/{projectId}/dashboards/")
    suspend fun getDashboards(
        @Path("projectId") projectId: String,
        @Header("Authorization") authHeader: String
    ): PostHogDashboardsResponse

    @GET("api/projects/{projectId}/dashboards/{id}/")
    suspend fun getDashboardDetail(
        @Path("projectId") projectId: String,
        @Path("id") id: Int,
        @Header("Authorization") authHeader: String,
        @Query("refresh") refresh: String? = "true"
    ): RemoteDashboard

    @GET("api/projects/{projectId}/insights/")
    suspend fun getInsights(
        @Path("projectId") projectId: String,
        @Header("Authorization") authHeader: String,
        @Query("dashboard") dashboardId: Int? = null,
        @Query("refresh") refresh: String? = "true"
    ): PostHogInsightsResponse
}

object PostHogClient {
    fun createService(baseUrl: String): PostHogApiService {
        // Sanitize base URL (ensure trailing slash)
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(PostHogApiService::class.java)
    }
}
