# Third-Party Notices

OpenCode Android includes or depends on the following third-party software.

## AndroidX / Jetpack

- AndroidX Core, Lifecycle, Activity, Navigation, Compose, Security Crypto, DocumentFile
- License: Apache License 2.0
- https://developer.android.com/jetpack

## OkHttp

- com.squareup.okhttp3:okhttp, okhttp-sse, mockwebserver
- License: Apache License 2.0
- https://square.github.io/okhttp/

## Gson

- com.google.code.gson:gson
- License: Apache License 2.0
- https://github.com/google/gson

## ZXing (Barcode Scanning)

- com.journeyapps:zxing-android-embedded, com.google.zxing:core
- License: Apache License 2.0
- https://github.com/journeyapps/zxing-android-embedded
- https://github.com/zxing/zxing

## Apache Commons Compress

- org.apache.commons:commons-compress
- License: Apache License 2.0
- https://commons.apache.org/proper/commons-compress/

## Kotlin / Coroutines

- org.jetbrains.kotlin:kotlin-stdlib
- org.jetbrains.kotlinx:kotlinx-coroutines-android
- License: Apache License 2.0
- https://kotlinlang.org/

## JUnit

- junit:junit (tests)
- License: Eclipse Public License 1.0
- https://junit.org/junit4/

## Termux / PRoot runtime assets

- PRoot runner and related shared libraries prepared at build time from Termux packages
  (see `runtime_tools/termux_assets.lock.json` for pinned package versions and hashes)
- Upstream licenses vary by package (typically GPL/LGPL/MIT/BSD). Consult the lockfile
  and corresponding Termux package metadata for exact license text.

## Alpine Linux minirootfs

- Downloaded at first local-runtime setup (version pinned in `local-runtime-manifest.json`)
- License: Various open-source licenses (Alpine packages)
- https://alpinelinux.org/

## OpenCode

- Official musl binary downloaded at setup/update time (version pinned in manifest / GitHub Releases)
- License: See https://github.com/anomalyco/opencode
- OpenCode Android is an unofficial client and is not affiliated with the OpenCode project.

## Generating an SBOM

For a machine-readable inventory of Gradle dependencies:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath > build/sbom-gradle-deps.txt
```

Optional: use [Syft](https://github.com/anchore/syft) or the Gradle CycloneDX plugin in CI for SPDX/CycloneDX output.
