# Question UI, Voice Feedback, Modes, Models, and GitHub Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make live questions answerable in chat, make voice input visibly active, expose execution modes and a provider-oriented model picker from the composer, and add separate Copilot and Git-operation GitHub authentication paths.

**Architecture:** Extend the existing OpenCode event/backend abstractions for questions and answers, keep question and voice state in `ChatViewModel`, and render the new controls in the existing Compose chat screen. Keep OpenCode provider auth unchanged as the Copilot path, while adding a separate encrypted GitHub Device Flow repository and local-runtime credential-helper synchronization for git.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android SpeechRecognizer, Kotlin coroutines/Flow, existing Gson HTTP client, Android encrypted settings/runtime credential infrastructure, JUnit and Compose instrumentation tests.

## Global Constraints

- Preserve all existing uncommitted user changes; never reset or overwrite unrelated work.
- Do not store ChatGPT, Copilot, or GitHub tokens in plaintext.
- Do not send local GitHub git tokens to remote runtimes without an explicit remote credential contract.
- Use the existing `RmsChanged` speech callback; do not add a second recorder or persist microphone data.
- Unknown or incomplete question/model fields must fail visibly and safely, not crash or render a blank surface.
- Existing unit, Compose instrumentation, lint, debug assembly, and release/R8 checks remain required.

## File Map

- Modify `app/src/main/java/com/opencode/android/core/api/OpenCodeApiModels.kt`: question request/answer event and backend-facing data types.
- Modify `app/src/main/java/com/opencode/android/core/api/OpenCodeEventParser.kt`: parse question events safely.
- Modify `app/src/main/java/com/opencode/android/core/api/OpenCodeApiClient.kt`: question answer endpoint and GitHub Device Flow HTTP calls where the client pattern is reused.
- Modify `app/src/main/java/com/opencode/android/runtime/OpenCodeBackend.kt` and local/remote implementations: expose question answers and local credential synchronization capability.
- Modify `app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt`: pending questions, answer lifecycle, voice level, and mode state.
- Modify `app/src/main/java/com/opencode/android/feature/chat/ChatHomeScreen.kt`: question cards, waveform feedback, and bottom mode controls.
- Modify `app/src/main/java/com/opencode/android/feature/chat/ModelAndRuntimePickerSheet.kt`: favorites/provider hierarchy, search, and explicit states.
- Add `app/src/main/java/com/opencode/android/feature/chat/QuestionCard.kt`: focused question rendering and answer callbacks.
- Add `app/src/main/java/com/opencode/android/feature/settings/GitHubAuthRepository.kt`: Device Flow and encrypted credential lifecycle.
- Modify `app/src/main/java/com/opencode/android/feature/settings/SettingsViewModel.kt`: GitHub git auth state and actions, separate from provider auth.
- Modify `app/src/main/java/com/opencode/android/feature/settings/ProviderSettingsScreen.kt` and settings host: separate Copilot/provider and Git operations cards.
- Modify local runtime credential files (`app/src/main/java/com/opencode/android/runtime/local/*`): create/remove git credential helper without logging secrets.
- Add/update tests beside API, chat, model picker, settings, and runtime credential implementations.

---

### Task 1: Add Question Event and Answer Contract

**Files:**
- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeApiModels.kt`
- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeEventParser.kt`
- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeApiClient.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/OpenCodeBackend.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalOpenCodeBackend.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/remote/RemoteOpenCodeBackend.kt`
- Test: `app/src/test/java/com/opencode/android/core/api/OpenCodeEventParserTest.kt`
- Test: `app/src/test/java/com/opencode/android/core/api/OpenCodeApiClientTest.kt`

**Interfaces:**
- Produce `data class QuestionRequest(id: String, sessionId: String, questions: List<QuestionPrompt>, multiple: Boolean = false)`.
- Produce `data class QuestionPrompt(question: String, header: String? = null, options: List<QuestionOption> = emptyList(), placeholder: String? = null)`.
- Produce `data class QuestionOption(label: String, description: String? = null)`.
- Produce `OpenCodeEvent.QuestionAsked(val request: QuestionRequest)`.
- Produce `suspend fun answerQuestion(sessionId: String, requestId: String, answers: List<List<String>>): Boolean` on `OpenCodeBackend` and its implementations.

- [ ] **Step 1: Write parser tests for valid and malformed question events.**

```kotlin
@Test
fun `parses question asked with options`() {
    val event = parser.parse(validQuestionJson)
    val request = requireIs<OpenCodeEvent.QuestionAsked>(event).request
    assertEquals("q-1", request.id)
    assertEquals("Pick a folder", request.questions.single().question)
    assertEquals(listOf("src", "docs"), request.questions.single().options.map { it.label })
}

@Test
fun `malformed question event becomes unknown instead of throwing`() {
    assertIs<OpenCodeEvent.Unknown>(parser.parse("{\"type\":\"question.asked\",\"properties\":{}}"))
}
```

- [ ] **Step 2: Run parser tests and confirm failure.**

Run: `./gradlew testDebugUnitTest --tests '*OpenCodeEventParserTest'`

Expected: FAIL because the event type and parser branch do not exist.

- [ ] **Step 3: Implement typed models, parser branch, API method, and backend delegation.**

Parse `properties.id`, `properties.sessionID`, and `properties.questions`; accept both object-shaped prompts and primitive string prompts. Return `Unknown` when the ID or session ID is missing. POST answers using the existing JSON request helper and return the existing success boolean convention.

- [ ] **Step 4: Run focused API tests.**

Run: `./gradlew testDebugUnitTest --tests '*OpenCodeEventParserTest' --tests '*OpenCodeApiClientTest'`

Expected: PASS.

- [ ] **Step 5: Commit the contract.**

```bash
git add app/src/main/java/com/opencode/android/core/api app/src/main/java/com/opencode/android/runtime app/src/test/java/com/opencode/android/core/api
git commit -m "feat: add question event and answer contract"
```

### Task 2: Add Chat Question State and Answer Lifecycle

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt`
- Add: `app/src/main/java/com/opencode/android/feature/chat/QuestionCard.kt`
- Test: `app/src/test/java/com/opencode/android/feature/chat/ChatViewModelQuestionTest.kt`

**Interfaces:**
- `ChatUiState.pendingQuestions: List<PendingQuestionUi>`.
- `PendingQuestionUi` contains the request plus `selectedAnswers: List<List<String>>`, `isSubmitting: Boolean`, and `error: String?`.
- `ChatViewModel.selectQuestionAnswer(questionId: String, questionIndex: Int, answer: String)`.
- `ChatViewModel.submitQuestion(questionId: String)`.

- [ ] **Step 1: Write failing tests for session filtering, answer selection, success, and failure.**

```kotlin
@Test
fun `question event is shown only for active session`() {
    viewModel.openSession("session-1")
    emit(OpenCodeEvent.QuestionAsked(request("q-1", "session-2")))
    assertTrue(viewModel.uiState.value.pendingQuestions.isEmpty())
}

@Test
fun `successful answer removes pending question`() = runTest {
    emit(OpenCodeEvent.QuestionAsked(request("q-1", "session-1")))
    viewModel.selectQuestionAnswer("q-1", 0, "src")
    viewModel.submitQuestion("q-1")
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.pendingQuestions.isEmpty())
}
```

- [ ] **Step 2: Run the focused test and confirm failure.**

Run: `./gradlew testDebugUnitTest --tests '*ChatViewModelQuestionTest'`

Expected: FAIL because pending question state and handlers do not exist.

- [ ] **Step 3: Implement state and event handling.**

Add `QuestionAsked` handling next to permission handling. Clear pending questions on new session/opened session. For single-select prompts replace the answer; for multi-select prompts toggle the answer. Keep failed submissions in state with `error` and clear `isSubmitting`.

- [ ] **Step 4: Add `QuestionCard` with accessible controls.**

Render question text, header, radio/checkbox options, text input when needed, submit button, progress state, and error text. Use stable test tags for question ID and submit action. Do not render a blank card when a prompt has no options; show its text and a text input.

- [ ] **Step 5: Run focused tests.**

Run: `./gradlew testDebugUnitTest --tests '*ChatViewModelQuestionTest'`

Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/opencode/android/feature/chat app/src/test/java/com/opencode/android/feature/chat/ChatViewModelQuestionTest.kt
git commit -m "feat: render and answer chat questions"
```

### Task 3: Add Voice Level State and Waveform Feedback

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatHomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Test: `app/src/test/java/com/opencode/android/feature/chat/ChatViewModelVoiceTest.kt`
- Test: `app/src/androidTest/java/com/opencode/android/feature/chat/ChatVoiceInstrumentedTest.kt`

**Interfaces:**
- `ChatUiState.voiceLevel: Float` normalized to `0f..1f`.
- Existing `startListening`, `updateSpeechPartial`, `reportSpeechError`, and `stopListening` reset or preserve the level consistently.

- [ ] **Step 1: Write failing RMS normalization tests.**

```kotlin
@Test
fun `rms values become bounded voice levels`() {
    viewModel.updateSpeechRms(-20f)
    assertEquals(0.5f, viewModel.uiState.value.voiceLevel, 0.01f)
    viewModel.updateSpeechRms(20f)
    assertEquals(1f, viewModel.uiState.value.voiceLevel)
}
```

- [ ] **Step 2: Run and confirm failure.**

Run: `./gradlew testDebugUnitTest --tests '*ChatViewModelVoiceTest'`

Expected: FAIL because `voiceLevel` and `updateSpeechRms` do not exist.

- [ ] **Step 3: Implement bounded RMS state.**

Map a practical speech range of `-60dB..0dB` to `0f..1f`, clamp all values, and reset to zero on stop/error/new session. Update the voice-session collector to call `updateSpeechRms(result.rmsdB)`.

- [ ] **Step 4: Implement composer visual feedback.**

When listening, tint the mic button with primary color, show the localized listening label, and draw 7 vertical bars. Use deterministic bar multipliers based on `voiceLevel` plus a small infinite transition so the UI moves even during quiet speech. During processing show a progress indicator; on error return to the normal mic state.

- [ ] **Step 5: Run tests and compile.**

Run: `./gradlew testDebugUnitTest --tests '*ChatViewModelVoiceTest' :app:compileDebugKotlin`

Expected: PASS and successful Kotlin compilation.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/opencode/android/feature/chat app/src/main/res/values app/src/main/res/values-ja app/src/test app/src/androidTest
git commit -m "feat: show live voice input feedback"
```

### Task 4: Add Bottom Composer Execution Modes

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatHomeScreen.kt`
- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeApiModels.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Test: `app/src/test/java/com/opencode/android/feature/chat/ChatViewModelModeTest.kt`

**Interfaces:**
- `enum class ChatExecutionMode { BUILD, PLAN, AUTO_ACCEPT }`.
- `ChatUiState.executionMode: ChatExecutionMode`.
- `ChatViewModel.selectExecutionMode(mode: ChatExecutionMode)`.
- `PromptRequest.executionMode: String?` serialized only when non-null.

- [ ] **Step 1: Write failing selection and serialization tests.**

```kotlin
@Test
fun `selected plan mode is sent in prompt`() = runTest {
    viewModel.selectExecutionMode(ChatExecutionMode.PLAN)
    viewModel.sendMessage("inspect project")
    assertEquals("plan", backend.lastPrompt.executionMode)
}
```

- [ ] **Step 2: Run focused tests and confirm failure.**

Run: `./gradlew testDebugUnitTest --tests '*ChatViewModelModeTest'`

Expected: FAIL because execution mode is absent from state and request.

- [ ] **Step 3: Implement state, request field, and compatibility mapping.**

Map Build to `build`, Plan to `plan`, and Auto accept to `auto-accept`. Preserve the existing selected agent independently. If the backend rejects the optional field, display the existing chat error without changing the selected mode.

- [ ] **Step 4: Add bottom chip UI and Compose tests.**

Place mode chips in the existing horizontal control row. Use a menu or compact row on narrow screens. Add test tags for each mode and assert selected styling/state.

- [ ] **Step 5: Run tests.**

Run: `./gradlew testDebugUnitTest --tests '*ChatViewModelModeTest' connectedDebugAndroidTest`

Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/opencode/android/feature/chat app/src/main/java/com/opencode/android/core/api app/src/main/res/values app/src/main/res/values-ja app/src/test app/src/androidTest
git commit -m "feat: add chat execution mode controls"
```

### Task 5: Redesign Provider Model Picker

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ModelAndRuntimePickerSheet.kt`
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatHomeScreen.kt`
- Modify: `app/src/main/java/com/opencode/android/data/repository/RuntimeCatalogRepository.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Test: `app/src/test/java/com/opencode/android/feature/chat/ModelPickerTest.kt`
- Test: `app/src/androidTest/java/com/opencode/android/feature/chat/ModelPickerInstrumentedTest.kt`

**Interfaces:**
- Internal `ModelPickerState { Loading, Unavailable(message), Empty, Ready }`.
- Internal provider/model normalization function returning deduplicated provider groups.

- [ ] **Step 1: Write normalization tests.**

```kotlin
@Test
fun `normalization removes blank inactive and duplicate models`() {
    val groups = normalizeProviders(inputProviders)
    assertEquals(listOf("model-a"), groups.single { it.provider.id == "openai" }.models.map { it.id })
}
```

- [ ] **Step 2: Run and confirm failure.**

Run: `./gradlew testDebugUnitTest --tests '*ModelPickerTest'`

Expected: FAIL because normalization and hierarchical sheet state do not exist.

- [ ] **Step 3: Implement model normalization and picker state.**

Filter active models, reject blank IDs, deduplicate by `provider.id/model.id`, and retain provider grouping. Distinguish catalog unavailable from a connected runtime with zero models.

- [ ] **Step 4: Implement the image-inspired sheet hierarchy.**

Show favorites and provider rows initially. Selecting a provider transitions to a detail list with back navigation, search, model description/ID, favorite toggle, and selected check. Keep runtime selection available from the initial sheet entry without crowding the model list.

- [ ] **Step 5: Add UI state tests.**

Assert favorites render first, provider counts render, provider navigation works, selected model has a check, and unavailable/empty states contain explanatory text rather than an empty surface.

- [ ] **Step 6: Run tests and commit.**

Run: `./gradlew testDebugUnitTest --tests '*ModelPickerTest' connectedDebugAndroidTest`

```bash
git add app/src/main/java/com/opencode/android/feature/chat app/src/main/java/com/opencode/android/data/repository app/src/main/res/values app/src/main/res/values-ja app/src/test app/src/androidTest
git commit -m "feat: redesign provider model picker"
```

### Task 6: Implement GitHub OAuth Device Flow Repository

**Files:**
- Add: `app/src/main/java/com/opencode/android/feature/settings/GitHubAuthRepository.kt`
- Add: `app/src/main/java/com/opencode/android/feature/settings/GitHubAuthModels.kt`
- Modify: `app/src/main/java/com/opencode/android/data/connection/SecureSettingsRepository.kt`
- Modify: `app/src/main/java/com/opencode/android/feature/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/opencode/android/feature/settings/GitHubAuthRepositoryTest.kt`
- Test: `app/src/test/java/com/opencode/android/feature/settings/SettingsViewModelGitHubTest.kt`

**Interfaces:**
- `GitHubAuthRepository.beginDeviceFlow(): DeviceAuthorization`.
- `GitHubAuthRepository.pollDeviceFlow(deviceCode: String, intervalSeconds: Long, expiresInSeconds: Long): GitHubAccount`.
- `GitHubAuthRepository.clear()`.
- `GitHubAuthUiState { signedOut, awaitingUser, polling, signedIn, error }`.

- [ ] **Step 1: Write fake-client tests for device code, authorization pending, success, expiry, and cancellation.**

```kotlin
@Test
fun `polling continues on authorization pending then saves account`() = runTest {
    fakeGithub.enqueuePendingThenToken()
    repository.beginAndPoll()
    assertEquals("octocat", repository.state.value.account?.login)
    assertTrue(secureSettings.githubTokenExists())
}
```

- [ ] **Step 2: Run and confirm failure.**

Run: `./gradlew testDebugUnitTest --tests '*GitHubAuthRepositoryTest'`

Expected: FAIL because the repository and encrypted GitHub fields do not exist.

- [ ] **Step 3: Implement Device Flow HTTP and encrypted persistence.**

Use the fixed `BuildConfig.GITHUB_CLIENT_ID`, request `read:user` and `repo` scopes, honor GitHub `interval` and `slow_down`, stop at expiry, and never include tokens in exceptions/log output. Store token, login, and timestamp through `SecureSettingsRepository`.

- [ ] **Step 4: Wire ViewModel actions.**

Expose start, open verification URL, cancel, and disconnect actions. Keep GitHub git state separate from `providerAuthDialog` and provider connected IDs.

- [ ] **Step 5: Run focused tests.**

Run: `./gradlew testDebugUnitTest --tests '*GitHubAuthRepositoryTest' --tests '*SettingsViewModelGitHubTest'`

Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/opencode/android/feature/settings app/src/main/java/com/opencode/android/data/connection app/src/test/java/com/opencode/android/feature/settings
git commit -m "feat: add github device authentication"
```

### Task 7: Add Git Credential Helper Synchronization

**Files:**
- Modify: `app/src/main/java/com/opencode/android/runtime/OpenCodeBackend.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeTarget.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeEnvironmentActivation.kt`
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalRuntimeService.kt`
- Add or modify: `app/src/main/java/com/opencode/android/runtime/local/GitCredentialHelper.kt`
- Test: `app/src/test/java/com/opencode/android/runtime/local/GitCredentialHelperTest.kt`

**Interfaces:**
- `GitCredentialHelper.install(token: String): Result<Unit>`.
- `GitCredentialHelper.remove(): Result<Unit>`.
- The helper returns `username=x-access-token` and the token only to git credential queries; it must not log command arguments or token content.

- [ ] **Step 1: Write fake runtime tests.**

```kotlin
@Test
fun `install writes helper without exposing token in command log`() {
    helper.install("secret")
    assertTrue(fakeRuntime.files.any { it.path.endsWith("git-credential-opencode") })
    assertFalse(fakeRuntime.logs.any { "secret" in it })
}
```

- [ ] **Step 2: Run and confirm failure.**

Run: `./gradlew testDebugUnitTest --tests '*GitCredentialHelperTest'`

Expected: FAIL because the helper and runtime synchronization hooks do not exist.

- [ ] **Step 3: Implement helper lifecycle.**

Create a private executable helper in the local runtime prefix, pass the token through a protected runtime file or environment mechanism, set git credential configuration to use the helper, and delete both helper and token file on disconnect. Refuse installation when the target is remote.

- [ ] **Step 4: Trigger synchronization on auth changes and startup.**

Call install after successful Device Flow and during local runtime activation when encrypted credentials exist. Call remove on disconnect. Keep startup failures visible as a settings warning without preventing the app from opening.

- [ ] **Step 5: Run tests and commit.**

Run: `./gradlew testDebugUnitTest --tests '*GitCredentialHelperTest' :app:compileDebugKotlin`

```bash
git add app/src/main/java/com/opencode/android/runtime app/src/test/java/com/opencode/android/runtime/local
git commit -m "feat: sync github credentials to local git"
```

### Task 8: Separate GitHub Settings UI and Final Verification

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/settings/ProviderSettingsScreen.kt`
- Modify: `app/src/main/java/com/opencode/android/feature/settings/SettingsScreenV2.kt`
- Modify: `app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Test: `app/src/androidTest/java/com/opencode/android/ui/UiScreenshotInstrumentedTest.kt`

- [ ] **Step 1: Add separate settings cards.**

Keep the current provider list and OAuth dialog under a `GitHub Copilot / モデル接続` section. Add a `GitHub / Git操作` section with signed-out, device-code, polling, signed-in, disconnect, unsupported-remote, and missing-Client-ID states. Never display the token.

- [ ] **Step 2: Wire browser launch and lifecycle cancellation.**

Use the existing browser-launch pattern for verification URLs. Cancel polling when the dialog leaves composition or the selected runtime changes.

- [ ] **Step 3: Add Compose/screenshot assertions.**

Assert that both sections are visible, question cards are not rendered as blank space, microphone listening state has a label/waveform, and model picker provider rows show counts.

- [ ] **Step 4: Run the complete verification suite.**

Run:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

Expected: all commands pass. If an emulator or signing environment is unavailable, record the exact command and failure in the final report rather than claiming completion.

- [ ] **Step 5: Review the diff and commit the final UI integration.**

```bash
git status --short
git commit -m "feat: complete chat interaction and github settings UI"
```

## Self-Review Checklist

- Question parsing, state, rendering, answer API, and tests are covered by Tasks 1-2.
- Voice state derives from the existing RMS callback and has unit/UI coverage in Task 3.
- Composer modes are explicitly modeled, serialized, and rendered in Task 4.
- Provider/favorite model hierarchy and explicit catalog states are covered in Task 5.
- Copilot provider auth remains separate from GitHub git Device Flow in Tasks 6 and 8.
- Encrypted persistence, local helper installation/removal, and secret-safe logs are covered in Tasks 6-7.
- No task relies on an undefined method or placeholder, and each task ends with a focused test/commit cycle.
