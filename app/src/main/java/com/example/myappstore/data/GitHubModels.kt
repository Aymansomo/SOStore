package com.example.myappstore.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Subset of the GitHub release JSON that the store needs. */
@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
internal data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null
)

/** Clean domain model of the newest release of a catalog app. */
data class ReleaseInfo(
    val version: String,
    val changelog: String,
    val publishedAt: String?,
    val apkUrl: String?,
    val apkSizeBytes: Long,
    val releaseName: String?
)

/** A failure that is already phrased for the UI. */
class StoreException(override val message: String) : Exception(message)
