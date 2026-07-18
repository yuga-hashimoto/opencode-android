# Local Runtime Diagnostics and Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a production-ready local runtime management screen that reports storage, memory, uptime, logs, and required Linux tools, and can completely remove the managed runtime after explicit confirmation.

**Architecture:** Keep diagnostics collection in the local-runtime layer and expose immutable diagnostics models to a dedicated ViewModel. Guest tool checks run through the same PRoot environment as OpenCode, while host metrics are collected from the app-owned runtime directory and child process. Complete deletion is serialized by `LocalRuntimeManager`, stops the child process first, deletes the entire runtime directory, then publishes `NotInstalled`.

**Tech Stack:** Kotlin, Android Foreground Service, Jetpack Compose Material 3, coroutines/StateFlow, JUnit4.

## Global Constraints

- Keep the OpenCode server bound to `127.0.0.1`.
- Never expose or log API keys, passwords, or Authorization headers.
- Do not delete runtime files without explicit user confirmation.
- Diagnostics must work for installed/stopped, installed/running, broken, and not-installed states.
- Required tool checks: OpenCode, Git, Bash, curl, SSH, ripgrep, CA certificates.
- Full verification: Unit Test, Lint, Debug APK, Release APK/R8.

---

### Task 1: Runtime Diagnostics Collector

**Files:**
- Create: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeDiagnostics.kt`
- Create: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeCommandRunner.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeProcessLauncher.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeInstaller.kt`
- Test: `app/src/test/java/com/opencode/android/runtime/local/LocalRuntimeDiagnosticsTest.kt`

**Interfaces:**
- Produces `LocalRuntimeDiagnosticsCollector.collect(): LocalRuntimeDiagnostics`.
- Produces process metrics from `LocalRuntimeProcessLauncher.metrics()`.
- Produces guest command results through `LocalRuntimeCommandRunner.run(command)`.

- [x] Write tests proving byte totals, free space, uptime, RSS, log truncation, and tool pass/fail mapping.
- [x] Run the targeted test and confirm it fails because diagnostics types are absent.
- [x] Implement the immutable diagnostics models and collector.
- [x] Implement PRoot command execution with timeout and bounded output.
- [x] Add `openssh-client` to fresh/repaired runtime setup.
- [x] Run targeted tests and compile.

### Task 2: Complete Runtime Deletion

**Files:**
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeManager.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeService.kt`
- Test: `app/src/test/java/com/opencode/android/runtime/local/LocalRuntimeManagerTest.kt`

**Interfaces:**
- Produces `LocalRuntimeManager.deleteRuntime(): Result<LocalRuntimeStatus.NotInstalled>`.
- Produces `LocalRuntimeServiceController.delete()` and `ACTION_DELETE`.

- [x] Write a failing test with environment, cache, logs, metadata, and workspace content.
- [x] Confirm deletion test fails because the manager method is absent.
- [x] Stop the launcher, recursively delete `files/runtime`, recreate no managed content, and publish `NotInstalled`.
- [x] Add Foreground Service delete action that disables watchdog and stops the service after deletion.
- [x] Run manager/watchdog tests.

### Task 3: Diagnostics and Management UI

**Files:**
- Create: `app/src/main/java/com/opencode/android/feature/workspace/LocalRuntimeManagementViewModel.kt`
- Create: `app/src/main/java/com/opencode/android/feature/workspace/LocalRuntimeManagementScreen.kt`
- Modify: `app/src/main/java/com/opencode/android/feature/workspace/WorkspaceViewModel.kt`
- Modify: `app/src/main/java/com/opencode/android/feature/workspace/WorkspacesScreen.kt`
- Modify: `app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt`
- Modify: `app/src/main/java/com/opencode/android/OpenCodeApplication.kt`
- Test: `app/src/test/java/com/opencode/android/feature/workspace/LocalRuntimeManagementViewModelTest.kt`

**Interfaces:**
- Management screen consumes `LocalRuntimeManagementUiState` and refresh/repair/delete callbacks.
- Workspaces screen produces `onOpenLocalManagement`.

- [x] Write ViewModel tests for initial refresh, successful diagnostics, failure, and delete request state.
- [x] Confirm tests fail because the ViewModel is absent.
- [x] Wire the collector into `OpenCodeApplication` and the ViewModel factory.
- [x] Build the management route with summary metrics, tool checks, log tail, refresh, repair, and confirmed delete.
- [x] Verify deletion returns to Workspaces and shows `未インストール`.
- [x] Run all tests, lint, Debug/Release builds, and API 36 emulator checks.
- [x] Update `docs/LOCAL_RUNTIME.md` and `docs/COMPLETION_CHECKLIST.md` only for verified items.
- [ ] Commit the verified feature.
