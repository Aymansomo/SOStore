# Store release playbook

## What is wired

| App | Repo | Installed package (the store checks this) | Tag pattern | APK asset |
|---|---|---|---|---|
| SOPlayer (music) | `Aymansomo/SOPlayer` | `paige.navic.debug` | `android-v1.0.0-beta07` | `SOPlayer.apk` |
| AYMAN DECK (macro) | `Aymansomo/SOMacro-mobile` | `com.ayman.macrodeck.mobile` | `mobile-v0.1.9` | `AYMAN-DECK.apk` |

The store fetches `/repos/{owner}/{repo}/releases`, ignores drafts/prereleases, picks the newest release that actually contains an `.apk`, and compares its tag with the installed `versionName`.

## One-time setup (after these edits)

Music app — working copy is `..\Music_app\SOPlayer-store`:

```powershell
cd "E:\ayman\Development\My_progicte\ME\Music_app\SOPlayer-store"
git add .github/workflows/android-apk.yml Navic/app/build.gradle.kts Navic/app/debug.keystore
git commit -m "Signed debug APK and version bump for store updates"
git push origin main
git tag android-v1.0.0-beta07
git push origin android-v1.0.0-beta07
```

Macro app — already pushed to `SOMacro-mobile`; just tag to publish the APK:

```powershell
cd "E:\ayman\Development\My_progicte\ME\macro app\ayman-macro-deck\mobile-app"
git tag mobile-v0.1.9
git push origin mobile-v0.1.9
```

Store itself — `Aymansomo/SOStore` now has `.github/workflows/store-apk.yml`, which publishes `SOStore.apk` on a `v*` tag:

```powershell
cd "E:\ayman\Development\My_progicte\ME\Store SOMO"
# commit and push any store changes first, then:
git tag v1.0.0
git push origin v1.0.0
```

Locally you can still build/install directly:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

## Daily loop for an update

1. Change the app code.
2. Bump `versionName` / `versionCode`:
   - Music: `Navic/app/build.gradle.kts` (currently `v1.0.0-beta07`, code `65`).
   - Macro: `mobile-app/android/app/build.gradle` and `mobile-app/package.json`.
3. Commit and push to `main`.
4. Push a matching tag (`android-v…`, `mobile-v…`, or `v…` for the store).
5. Wait for the **Android APK** workflow to finish; it publishes a release with the APK.
6. Open the store, refresh, tap **Update**.

## Important notes

- Both apps and the store now use a committed debug keystore so APK signatures stay identical. Keep all three `debug.keystore` files; if one is lost, existing installs must be uninstalled before the next update.
- Old SOPlayer releases (`beta05`, `beta06`) were signed with different per-run keys. Anyone who installed them must uninstall once before installing `beta07`. After that, updates work normally.
- `paige.navic.debug` (not `paige.navic`) is correct: CI builds the `defaultDebug` variant, which adds the `.debug` suffix.
- Releases must stay **published** and **non-prerelease**; drafts and prereleases are ignored by the store.
- Keep `GITHUB_TOKEN` empty while the repos are public. If they become private, add a token with `repo` scope.
