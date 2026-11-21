# SOStore

Personal Android app store for my own apps. It reads GitHub Releases for each configured app, downloads the newest `.apk`, and offers Install / Update / Open.

## Apps in the catalog

| App | GitHub repo | Installed package |
|---|---|---|
| SOPlayer | `Aymansomo/SOPlayer` | `paige.navic.debug` |
| AYMAN DECK | `Aymansomo/ayman-macro-deck` | `com.ayman.macrodeck.mobile` |

## How it works

`AppCatalogConfig.kt` holds the catalog. `GitHubReleaseService.kt` fetches the release list, skips drafts/prereleases, and picks the newest release containing an `.apk`. `StoreViewModel.kt` compares the tag with the installed `versionName` and drives the download/install flow. `VersionUtils.kt` understands `v`, `android-v` and `mobile-v` tags.

## Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

See `RELEASE-PLAYBOOK.md` for the release workflow and `CHANGES.md` for the full change log.
