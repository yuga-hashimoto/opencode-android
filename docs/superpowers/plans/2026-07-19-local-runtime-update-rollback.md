# Local Runtime Update and Rollback Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` task-by-task.

**Goal:** Add official OpenCode release checks, SHA-256 verified updates, free-space preflight, atomic binary/metadata rotation, automatic recovery, and one-level manual rollback.

**Architecture:** Keep the Alpine rootfs and user data. Update only the official musl OpenCode binary. Fetch official GitHub release metadata, select the exact ABI asset, require the API-provided digest, stage `opencode.candidate`, validate its version in PRoot, then rotate current/candidate/rollback files on the same filesystem. `LocalRuntimeManager` stops and restarts the server and restores the prior version when startup fails.

**Constraints**
- Release endpoint: `https://api.github.com/repos/anomalyco/opencode/releases/latest`.
- `arm64-v8a` uses `opencode-linux-arm64-musl.tar.gz`.
- `x86_64` uses `opencode-linux-x64-musl.tar.gz`.
- Require HTTPS and `sha256:` plus 64 lowercase hex characters.
- Preflight required space: `assetSize * 4 + 64 MiB`.
- Never replace the active binary before download, digest, extraction, executable bit, and `--version` validation succeed.
- Preserve one rollback version.
- Bind only to `127.0.0.1:4097`.
- Verify Unit Test, Lint, Debug/Release/R8, API 36 instrumentation, and emulator UI.

## Task 1: Release metadata

Files:
- Create `LocalRuntimeReleaseClient.kt`
- Create `LocalRuntimeReleaseClientTest.kt`

Interfaces:
- `LocalRuntimeRelease`
- `LocalRuntimeReleaseAsset`
- `LocalRuntimeUpdateCheck.UpToDate`
- `LocalRuntimeUpdateCheck.Available`
- `LocalRuntimeReleaseClient.check(currentVersion, abi)`
- `compareOpenCodeVersions(left, right)`

Steps:
- [x] Test semantic version comparison, exact asset selection, missing digest, non-HTTPS URL, unsupported ABI, and release-note truncation.
- [x] Confirm tests fail.
- [x] Implement OkHttp/Gson release parsing and validation.
- [x] Run targeted tests.
- [x] Commit `feat: add official OpenCode release checks`.

## Task 2: Atomic updater

Files:
- Create `LocalRuntimeUpdater.kt`
- Create `LocalRuntimeUpdaterTest.kt`
- Modify `LocalRuntimeCommandRunner.kt`
- Modify `LocalRuntimeInstaller.kt`

Interfaces:
- `PreparedRuntimeUpdate`
- `prepare(release, onProgress)`
- `activate(prepared)`
- `rollback()`
- `rollbackVersion()`
- `runShell(command, timeoutSeconds)`

Steps:
- [ ] Test insufficient space before download, SHA mismatch, candidate version mismatch, successful rotation, failure restoration, and manual rollback.
- [ ] Confirm tests fail.
- [ ] Download to `.partial`, verify, then rename.
- [ ] Extract to `opencode.candidate` and validate in PRoot.
- [ ] Rotate `opencode`, `opencode.rollback`, `metadata.json`, and `metadata.rollback.json` with swap files and restoration on failure.
- [ ] Run tests.
- [ ] Commit `feat: add atomic local runtime updater`.

## Task 3: Manager and service

Files:
- Modify `LocalRuntimeStatus.kt`
- Modify `LocalRuntimeManager.kt`
- Modify `LocalRuntimeService.kt`
- Modify `OpenCodeApplication.kt`
- Modify `LocalRuntimeTarget.kt`
- Extend manager/service tests.

Interfaces:
- `LocalRuntimeStatus.Updating`
- `LocalRuntimeOperationResult`
- `lastOperation: StateFlow`
- `checkForUpdate()`
- `updateToLatest()`
- `rollback()`
- Service actions `ACTION_UPDATE` and `ACTION_ROLLBACK`.

Steps:
- [ ] Test up-to-date no-op, update success, failed startup automatic rollback, manual rollback, and failed rollback restoration.
- [ ] Confirm tests fail.
- [ ] Add exhaustive status handling and notifications.
- [ ] Implement stop, prepare, activate, start, automatic rollback, and prior-version restart under the operation mutex.
- [ ] Keep URLs/digests out of intents.
- [ ] Run tests and compile.
- [ ] Commit `feat: orchestrate runtime updates and rollback`.

## Task 4: Management UI

Files:
- Modify `LocalRuntimeManagementViewModel.kt`
- Modify `LocalRuntimeManagementScreen.kt`
- Modify `OpenCodeApp.kt`
- Extend ViewModel tests.

Steps:
- [ ] Test available/up-to-date states, update action, operation messages, rollback action, and disabled controls while updating.
- [ ] Confirm tests fail.
- [ ] Show current/latest versions, bounded notes, verified digest, required free space, and update button.
- [ ] Show rollback version only when present and require confirmation.
- [ ] Refresh diagnostics and update state after operations.
- [ ] Run tests, compile, and Lint.
- [ ] Commit `feat: add runtime update management UI`.

## Task 5: Android verification and docs

Files:
- Create `LocalRuntimeUpdaterInstrumentedTest.kt`
- Modify `docs/LOCAL_RUNTIME.md`
- Modify `docs/COMPLETION_CHECKLIST.md`
- Update this plan.

Steps:
- [ ] Instrument activation and rollback in `targetContext.filesDir`.
- [ ] Run API 36 `connectedDebugAndroidTest`.
- [ ] Run official update check in the app and verify installed `1.18.3` is up to date.
- [ ] Run `testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest`.
- [ ] Update docs only for verified behavior.
- [ ] Independent review; fix Critical/Important findings.

- [ ] Commit `test: verify local runtime update rollback`.
