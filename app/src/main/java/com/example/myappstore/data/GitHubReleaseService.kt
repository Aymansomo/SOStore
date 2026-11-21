package com.example.myappstore.data

import com.example.myappstore.AppCatalogConfig
import com.example.myappstore.CatalogApp
import com.example.myappstore.domain.VersionUtils
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

private interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): List<GitHubReleaseDto>
}

/**
 * Talks to the public GitHub REST API and turns the newest release that actually contains an
 * APK into [ReleaseInfo] (a repo may also publish desktop installers without APKs).
 *
 * All failures are returned as [Result.failure] carrying a [StoreException] whose message is
 * safe to show in the UI (rate limits, offline, 404, ...).
 */
class GitHubReleaseService(
    private val token: String = AppCatalogConfig.GITHUB_TOKEN
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "MyAppStore/1.0 (Android)")
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer ${token.trim()}")
            }
            chain.proceed(builder.build())
        }
        .build()

    private val api: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubApi::class.java)

    suspend fun fetchLatestRelease(app: CatalogApp): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val releases = api.getReleases(app.githubOwner, app.githubRepo)
            val release = releases
                .filter { !it.draft && !it.prerelease }
                .sortedByDescending { it.publishedAt.orEmpty() }
                .firstOrNull { candidate ->
                    candidate.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
                }
                ?: throw StoreException(
                    "No published release with an .apk asset was found for " +
                        "${app.githubOwner}/${app.githubRepo}."
                )

            val version = VersionUtils.normalize(release.tagName)
            if (version.isBlank()) {
                throw StoreException("The latest release of ${app.name} has no version tag.")
            }

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAsset == null) {
                throw StoreException(
                    "The latest release of ${app.name} (v$version) has no .apk asset attached."
                )
            }
            if (apkAsset.browserDownloadUrl.isBlank()) {
                throw StoreException("The .apk asset of ${app.name} v$version has no download URL.")
            }

            val changelog = release.body?.trim().orEmpty()
                .ifBlank { "No release notes were provided for this version." }

            Result.success(
                ReleaseInfo(
                    version = version,
                    changelog = changelog,
                    publishedAt = release.publishedAt,
                    apkUrl = apkAsset.browserDownloadUrl,
                    apkSizeBytes = apkAsset.size,
                    releaseName = release.name?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        } catch (error: Exception) {
            Result.failure(toStoreException(error, app))
        }
    }

    private fun toStoreException(error: Exception, app: CatalogApp): StoreException = when (error) {
        is StoreException -> error
        is HttpException -> {
            val code = error.code()
            when (code) {
                401 -> StoreException(
                    "GitHub rejected the credentials. Check GITHUB_TOKEN in AppCatalogConfig."
                )
                403, 429 -> StoreException(
                    "GitHub rate limit reached or access denied. Add a GITHUB_TOKEN in " +
                        "AppCatalogConfig (private repos need a token) or try again later."
                )
                404 -> StoreException(
                    "No published release found for ${app.githubOwner}/${app.githubRepo}. " +
                        "Check the owner and repository names in AppCatalogConfig."
                )
                else -> StoreException("GitHub request for ${app.name} failed (HTTP $code).")
            }
        }
        is IOException -> StoreException(
            "Couldn't reach GitHub. Check your internet connection and try again."
        )
        else -> StoreException(
            "Unexpected error while loading ${app.name}: ${error.message ?: error.javaClass.simpleName}"
        )
    }
}
