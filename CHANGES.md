# Change log — store release wiring

This file records every change made to connect the store with the two app repositories.

## 1. Store app — `E:\ayman\Development\My_progicte\ME\Store SOMO`

| File | Change |
|---|---|
| `app/src/main/java/com/example/myappstore/AppCatalogConfig.kt` | Replaced placeholder catalog entries with the real apps: `Aymansomo/SOPlayer` (`paige.navic.debug`) and `Aymansomo/ayman-macro-deck` (`com.ayman.macrodeck.mobile`). Updated the tag-format documentation. `GITHUB_TOKEN` stays empty (public repos). |
| `app/src/main/AndroidManifest.xml` | `<queries>` now lists `paige.navic.debug` and `com.ayman.macrodeck.mobile` so Android 11+ can see installed versions. |
| `app/src/main/java/com/example/myappstore/domain/VersionUtils.kt` | `normalize()` now strips `v`, `android-v` and `mobile-v` tag prefixes instead of only a leading `v`. |
| `app/src/main/java/com/example/myappstore/data/GitHubModels.kt` | Added `draft` to `GitHubReleaseDto` so drafts can be filtered out. |
| `app/src/main/java/com/example/myappstore/data/GitHubReleaseService.kt` | Switched from `/releases/latest` to `/releases?per_page=30`; filters out drafts/prereleases and picks the newest release that actually contains an `.apk`. Added explanatory error message. |
| `.gitignore` (new) | Ignores `local.properties`, build outputs, IDE/tool caches, APKs. |
| `README.md` (new) | Project overview, catalog table, build command. |
| `RELEASE-PLAYBOOK.md` (new) | One-time setup commands, daily release loop, important caveats. |
| `CHANGES.md` (new) | This file. |

Verified: `.\gradlew.bat compileDebugKotlin` succeeds.

## 2. Music app — `E:\ayman\Development\My_progicte\ME\Music_app\SOPlayer-store` (repo `Aymansomo/SOPlayer`)

| File | Change |
|---|---|
| `Navic/app/build.gradle.kts` | `versionCode` 64 → 65, `versionName` `v1.0.0-beta05` → `v1.0.0-beta07`. Added a `getByName("debug")` signing config pointing to the committed `debug.keystore` so CI releases keep one signature. |
| `Navic/app/debug.keystore` (new) | Fixed debug signing key for APK updates (alias `androiddebugkey`, store/key password `android`). |
| `.github/workflows/android-apk.yml` | Set `draft: false` and `prerelease: false` explicitly on the release step and documented the keystore/version-bump requirement. |

Important finding: the CI APK is the `defaultDebug` variant, so its real package is `paige.navic.debug` (not `paige.navic`), and `versionName` inside the published `beta06` APK was still `v1.0.0-beta05`. Old releases were signed with a fresh key on every runner; users of `beta05`/`beta06` must uninstall once before `beta07`.

Verified: `./gradlew.bat :app:tasks` configuration succeeds.

## 3. Macro app — `E:\ayman\Development\My_progicte\ME\macro app\ayman-macro-deck` (repo `Aymansomo/ayman-macro-deck`)

| File | Change |
|---|---|
| `.github/workflows/mobile-apk.yml` (new) | On tag `mobile-v*`: `npm ci` → `npm run build` → `npx cap sync android` → signed `assembleDebug` → upload artifact → publish a release with `AYMAN-DECK.apk` and its SHA-256. |
| `mobile-app/android/app/build.gradle` | Added `signingConfigs.debug` using the committed keystore; `versionCode` 1 → 2, `versionName` `1.0` → `0.1.9`. |
| `mobile-app/android/app/debug.keystore` (new) | Fixed debug signing key for APK updates. |
| `mobile-app/package.json` | `version` `0.1.0` → `0.1.9`. |
| `mobile-app/.gitignore` | Added `releases/` and `*.apk` to keep local release builds out of git. |
| `.gitignore` (root) | Added `.freebuff/` (local logs) to the ignore list. |
| `mobile-app/` (whole folder) | Was untracked and never pushed; now added to the repo (source, Capacitor Android project, lockfile, config). |

Verified: `npm run build` succeeds (TypeScript check + Vite build).
