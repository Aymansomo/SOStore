package com.example.myappstore

/**
 * One personal app that is distributed through GitHub Releases.
 *
 * @param githubOwner GitHub user or organization that owns the repository.
 * @param githubRepo  Repository name (the part after the slash in the URL).
 */
data class CatalogApp(
    val id: String,
    val name: String,
    val packageName: String,
    val githubOwner: String,
    val githubRepo: String,
    val description: String
)

/**
 * The entire catalog of your apps. Edit this file to add, remove or rename entries.
 *
 * Requirements for each GitHub repository:
 *  - It must have at least one published release (draft releases are ignored by the API).
 *  - The release must contain an asset whose name ends with `.apk`.
 *  - The `tag_name` must look like `v1.2.0`, `android-v1.2.0`, `mobile-v1.2.0` or `1.2.0`
 *    (the prefix is stripped before comparing versions).
 *
 * Keep the `<queries>` block in AndroidManifest.xml in sync with [packageNames], otherwise
 * Android 11+ will not let PackageManager see whether these apps are installed.
 */
object AppCatalogConfig {

    /**
     * Optional GitHub personal access token.
     *
     * - Leave empty ("") for public repositories. Unauthenticated requests are limited to
     *   60 per hour per IP address.
     * - Set a token with `repo` scope to read private repositories (and raise the rate limit).
     * - Never commit a real token to a public repository.
     */
    const val GITHUB_TOKEN: String = ""

    val apps: List<CatalogApp> = listOf(
        CatalogApp(
            id = "soplayer",
            name = "SOPlayer",
            packageName = "paige.navic.debug",
            githubOwner = "Aymansomo",
            githubRepo = "SOPlayer",
            description = "Personal music streaming and offline player."
        ),
        CatalogApp(
            id = "ayman-deck",
            name = "AYMAN DECK",
            packageName = "com.ayman.macrodeck.mobile",
            githubOwner = "Aymansomo",
            githubRepo = "SOMacro-mobile",
            description = "Macro deck mobile companion."
        )
    )

    /** Convenience for manifest queries and broadcast filtering. */
    val packageNames: List<String> = apps.map { it.packageName }
}
