# Release guide

## GitHub Releases (recommended)

Push a version tag to trigger `.github/workflows/release.yml`:

```bash
# ensure main is green
git checkout main
git pull

# tag must match vX.Y.Z (optional suffix: -alpha.1 / -beta.1 / -rc.1)
git tag v0.1.0
git push origin v0.1.0
```

The workflow will:

1. Run unit tests + lint
2. Build debug and release APKs
3. Create a GitHub Release named `OpenCode Android vX.Y.Z`
4. Attach APKs under the repository **Releases** tab

Prerelease tags (`v0.2.0-beta.1`, `v1.0.0-rc.1`, etc.) are marked as GitHub prereleases.

### Optional signed release APK

Without secrets, CI uploads an **unsigned** release APK (fine for testing).

To ship a **signed** APK from CI, add repository secrets:

| Secret | Value |
|--------|--------|
| `RELEASE_KEYSTORE_BASE64` | `base64 -i opencode-android-release.jks \| tr -d '\n'` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias (e.g. `opencode-android`) |
| `RELEASE_KEY_PASSWORD` | key password |

`app/build.gradle.kts` reads `OPENCODE_STORE_*` from env/gradle properties when present.

## Local signed build

1. Create a keystore (once):

```bash
keytool -genkey -v \
  -keystore opencode-android-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias opencode-android
```

2. Export for one build:

```bash
export OPENCODE_STORE_FILE=/absolute/path/opencode-android-release.jks
export OPENCODE_STORE_PASSWORD=...
export OPENCODE_KEY_ALIAS=opencode-android
export OPENCODE_KEY_PASSWORD=...
./gradlew assembleRelease
```

Or add the same keys to `~/.gradle/gradle.properties` (do not commit).

3. Verify:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Versioning

- `versionName` / `versionCode` live in `app/build.gradle.kts`
- Tag releases as `vX.Y.Z` matching `versionName` when possible
- Put user-facing notes in the GitHub Release body (workflow seeds a short install blurb)

## Pre-release checklist

- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease`
- [ ] Manual smoke: local install, chat, permission approve/reject, remote connect
- [ ] `THIRD_PARTY_NOTICES.md` still accurate
- [ ] No secrets in git history
- [ ] Tag pushed: `git push origin vX.Y.Z`
