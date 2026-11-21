package com.example.myappstore.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myappstore.AppCatalogConfig
import com.example.myappstore.CatalogApp
import com.example.myappstore.data.GitHubReleaseService
import com.example.myappstore.data.ReleaseInfo
import com.example.myappstore.domain.PackageUtils
import com.example.myappstore.domain.VersionUtils
import com.example.myappstore.installer.ApkInstallerManager
import com.example.myappstore.installer.InstallEventBus
import com.example.myappstore.installer.InstallOutcome
import com.example.myappstore.installer.InstallStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InstallPhase { IDLE, DOWNLOADING, INSTALLING }

enum class StoreAction(val label: String) {
    INSTALL("Install"),
    UPDATE("Update"),
    OPEN("Open"),
    WORKING("Working…")
}

data class AppUiState(
    val app: CatalogApp,
    val installedVersion: String? = null,
    val latestVersion: String? = null,
    val changelog: String? = null,
    val publishedAt: String? = null,
    val apkUrl: String? = null,
    val apkSizeBytes: Long = 0L,
    val isLoadingRelease: Boolean = false,
    val releaseError: String? = null,
    val phase: InstallPhase = InstallPhase.IDLE,
    val progress: Int? = null,
    val statusMessage: String? = null,
    val changelogExpanded: Boolean = false
) {
    val action: StoreAction
        get() = when {
            phase != InstallPhase.IDLE -> StoreAction.WORKING
            installedVersion == null -> StoreAction.INSTALL
            latestVersion != null && VersionUtils.compare(latestVersion, installedVersion) > 0 ->
                StoreAction.UPDATE
            else -> StoreAction.OPEN
        }

    val isEnabled: Boolean
        get() = when (action) {
            StoreAction.OPEN -> true
            StoreAction.INSTALL, StoreAction.UPDATE ->
                phase == InstallPhase.IDLE && !apkUrl.isNullOrBlank()
            StoreAction.WORKING -> false
        }
}

data class Banner(val message: String, val isError: Boolean)

data class StoreUiState(
    val apps: List<AppUiState> = emptyList(),
    val isRefreshing: Boolean = false,
    val banner: Banner? = null
)

/**
 * Owns catalog state: installed versions, newest GitHub releases, download/install progress
 * and the single action (Install / Update / Open) exposed per app.
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val installer = ApkInstallerManager(application)
    private val releaseService = GitHubReleaseService()

    private val _uiState = MutableStateFlow(
        StoreUiState(apps = AppCatalogConfig.apps.map { AppUiState(app = it) })
    )
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            InstallEventBus.events.collect(::handleInstallOutcome)
        }
        refresh()
    }

    /** Reloads installed versions and every GitHub release. Safe to call while refreshing. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { doRefresh() }
    }

    private suspend fun doRefresh() {
        _uiState.update { current ->
            current.copy(
                isRefreshing = true,
                banner = null,
                apps = current.apps.map { it.copy(isLoadingRelease = true) }
            )
        }

        updateInstalledVersions()

        val results: Map<String, Result<ReleaseInfo>> = coroutineScope {
            AppCatalogConfig.apps
                .map { app -> async { app.id to releaseService.fetchLatestRelease(app) } }
                .awaitAll()
                .toMap()
        }

        val errors = results.values.mapNotNull { it.exceptionOrNull()?.message }

        _uiState.update { current ->
            current.copy(
                apps = current.apps.map { appState ->
                    val result = results[appState.app.id]
                        ?: return@map appState.copy(isLoadingRelease = false)

                    result.fold(
                        onSuccess = { release ->
                            appState.copy(
                                latestVersion = release.version,
                                changelog = release.changelog,
                                publishedAt = release.publishedAt,
                                apkUrl = release.apkUrl,
                                apkSizeBytes = release.apkSizeBytes,
                                isLoadingRelease = false,
                                releaseError = null
                            )
                        },
                        onFailure = { error ->
                            appState.copy(
                                isLoadingRelease = false,
                                releaseError = error.message ?: "Couldn't load the latest release."
                            )
                        }
                    )
                },
                isRefreshing = false,
                banner = when {
                    errors.isEmpty() -> null
                    errors.size >= AppCatalogConfig.apps.size -> Banner(errors.first(), true)
                    else -> Banner(
                        "${errors.size} of ${AppCatalogConfig.apps.size} apps couldn't be refreshed. " +
                            errors.first(),
                        true
                    )
                }
            )
        }
    }

    /** Primary button handler for one card. */
    fun onAction(appId: String) {
        val state = _uiState.value.apps.firstOrNull { it.app.id == appId } ?: return
        if (state.phase != InstallPhase.IDLE) return

        when (state.action) {
            StoreAction.OPEN -> openApp(state)
            StoreAction.INSTALL, StoreAction.UPDATE -> installOrUpdate(state)
            StoreAction.WORKING -> Unit
        }
    }

    fun toggleChangelog(appId: String) {
        updateApp(appId) { it.copy(changelogExpanded = !it.changelogExpanded) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(banner = null) }
    }

    /** Called when a package is added, replaced or removed on the device. */
    fun onPackagesChanged() {
        updateInstalledVersions()
    }

    private fun openApp(state: AppUiState) {
        val context = getApplication<Application>()
        val intent = PackageUtils.getLaunchIntent(context, state.app.packageName)
        if (intent == null) {
            showBanner("Couldn't open ${state.app.name}: no launchable activity was found.")
            return
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { error ->
                showBanner("Couldn't open ${state.app.name}: ${error.message ?: "unknown error"}.")
            }
    }

    private fun installOrUpdate(state: AppUiState) {
        val context = getApplication<Application>()
        val apkUrl = state.apkUrl
        if (apkUrl.isNullOrBlank()) {
            showBanner("No APK is available for ${state.app.name} yet. Upload one to the release and refresh.")
            return
        }

        if (!installer.canInstallPackages()) {
            showBanner(
                "Allow \"My App Store\" to install unknown apps, then tap " +
                    "${state.action.label} again."
            )
            runCatching {
                context.startActivity(
                    installer.unknownSourcesSettingsIntent()
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { error ->
                showBanner("Couldn't open the install settings: ${error.message ?: "unknown error"}.")
            }
            return
        }

        updateApp(state.app.id) {
            it.copy(
                phase = InstallPhase.DOWNLOADING,
                progress = 0,
                statusMessage = "Downloading…",
                changelogExpanded = false
            )
        }

        viewModelScope.launch {
            try {
                val apkFile = installer.downloadApk(
                    packageName = state.app.packageName,
                    url = apkUrl
                ) { _, _, percent ->
                    updateApp(state.app.id) { app ->
                        app.copy(progress = percent ?: app.progress)
                    }
                }

                updateApp(state.app.id) {
                    it.copy(
                        phase = InstallPhase.INSTALLING,
                        progress = 100,
                        statusMessage = "Installing…"
                    )
                }

                installer.installApk(apkFile, state.app.packageName)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateApp(state.app.id) {
                    it.copy(phase = InstallPhase.IDLE, progress = null, statusMessage = null)
                }
                showBanner("Couldn't install ${state.app.name}: ${error.message ?: "unknown error"}")
            }
        }
    }

    private fun handleInstallOutcome(outcome: InstallOutcome) {
        val state = _uiState.value.apps.firstOrNull { it.app.packageName == outcome.packageName }
            ?: return

        when (outcome.status) {
            InstallStatus.PENDING -> updateApp(state.app.id) {
                it.copy(
                    phase = InstallPhase.INSTALLING,
                    progress = 100,
                    statusMessage = outcome.message ?: "Confirm the installation…"
                )
            }

            InstallStatus.SUCCESS -> {
                val wasInstalled = state.installedVersion != null
                updateApp(state.app.id) {
                    it.copy(phase = InstallPhase.IDLE, progress = null, statusMessage = null)
                }
                updateInstalledVersions()

                val version = _uiState.value.apps
                    .firstOrNull { it.app.id == state.app.id }
                    ?.installedVersion
                val verb = if (wasInstalled) "updated" else "installed"
                val suffix = version?.let { " to v$it" }.orEmpty()
                _uiState.update {
                    it.copy(banner = Banner("${state.app.name} was $verb$suffix.", false))
                }
            }

            InstallStatus.FAILURE -> {
                updateApp(state.app.id) {
                    it.copy(phase = InstallPhase.IDLE, progress = null, statusMessage = null)
                }
                showBanner(
                    outcome.message ?: "Installing ${state.app.name} failed."
                )
            }
        }
    }

    private fun updateInstalledVersions() {
        val context = getApplication<Application>()
        val installedVersions = AppCatalogConfig.apps.associate { app ->
            app.id to PackageUtils.getInstalledApp(context, app.packageName)?.versionName
        }
        _uiState.update { current ->
            current.copy(
                apps = current.apps.map { appState ->
                    appState.copy(installedVersion = installedVersions[appState.app.id])
                }
            )
        }
    }

    private fun updateApp(appId: String, transform: (AppUiState) -> AppUiState) {
        _uiState.update { current ->
            current.copy(
                apps = current.apps.map { appState ->
                    if (appState.app.id == appId) transform(appState) else appState
                }
            )
        }
    }

    private fun showBanner(message: String) {
        _uiState.update { it.copy(banner = Banner(message, true)) }
    }
}
