# Chat Composer, Echo, and Model Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match the second reference image's mobile chat composer, prevent assistant-side echoing of user input, and make provider models visible in the picker.

**Architecture:** Keep `ChatViewModel` as the owner of message state and preserve the Composer/attachment/variant work already present on `main`. Harden role filtering at the ViewModel/event boundary, normalize catalog entries in the existing picker, and make only the remaining visual/empty-state corrections in the chat screen. Preserve existing backend APIs and message cards.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android ViewModel, Kotlin coroutines/StateFlow, existing OpenCode API models and Compose test infrastructure.

## Global Constraints

- The dock must preserve attachment previews, voice input, send/stop behavior, model selection, variant/reasoning selection, agent selection, workspace selection, settings, and context usage.
- The optimistic local user message remains the sole user bubble for a sent prompt.
- Model picker entries with blank IDs are skipped safely and duplicate provider/model entries are removed.
- Missing model data must produce an explicit empty/unavailable state.
- Existing unit, instrumentation, lint, and release build checks remain required.

---

### Task 1: Reproduce and lock message-role behavior

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt:103-109,425-459,634-655`
- Test: `app/src/test/java/com/opencode/android/feature/chat/ChatViewModelTest.kt`

**Interfaces:**
- Consumes: `OpenCodeMessage.info.role`, `OpenCodePart.sessionId`, `OpenCodePart.messageId`, and current active session state.
- Produces: history and streaming transformations that never create assistant UI content from a user-role message/part.

- [ ] **Step 1: Write failing tests**

Extend the existing `ChatViewModelTest` fixture, which already tests normal streamed assistant text, with these cases:

```kotlin
@Test
fun streaming_user_part_is_ignored() = runTest {
    val backend = FakeBackend()
    val viewModel = ChatViewModel(backend)
    viewModel.sendMessage("眠い")
    advanceUntilIdle()
    backend.events.emit(OpenCodeEvent.MessagePartUpdated(OpenCodePart(
        id = "user-part", sessionId = "s1", messageId = "user-message",
        type = "text", text = "眠い", role = "user"
    )))
    advanceUntilIdle()
    assertEquals(1, viewModel.uiState.value.messages.count { it.text == "眠い" })
    assertTrue(viewModel.uiState.value.messages.single().isUser)
}
```

If `OpenCodePart` has no role field on the updated main branch, first add the server's role field using the existing Gson serialization convention, or maintain a set of known user message IDs populated from history/optimistic sends. Do not expose private production helpers solely for tests.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ChatViewModel*'`

Expected: the new role-isolation assertion fails before the guard is implemented.

- [ ] **Step 3: Implement the smallest role guard**

Track user message IDs and assistant message IDs in `ChatViewModel`; clear both on `newSession`/`openSession`. Before `updateStreamingMessage`, ignore a part whose message ID is known to be user-owned. If the API supplies a role on the part, reject `role == "user"`; otherwise keep the current optimistic user message and history role as the fallback. Do not change the optimistic user append in `sendMessage`.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ChatViewModel*'`

Expected: PASS, with no new failures in existing ViewModel tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt app/src/test
git commit -m "fix: prevent assistant echo of user messages"
```

### Task 2: Make model catalog rendering deterministic

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ModelPickerSheet.kt:33-117`
- Modify: `app/src/androidTest/java/com/opencode/android/feature/chat/OpenCodeChatScreenTest.kt` if picker behavior is covered there
- Test: `app/src/androidTest/java/com/opencode/android/feature/chat/OpenCodeChatScreenTest.kt`

**Interfaces:**
- Consumes: `List<OpenCodeProvider>`, each provider's model map, and recent model pairs already passed by `OpenCodeApp`.
- Produces: provider-grouped visible models with stable filtering, de-duplication, and explicit empty-state copy.

- [ ] **Step 1: Write failing catalog tests**

Test a pure helper or equivalent observable behavior for:

```kotlin
val providers = listOf(
    OpenCodeProvider("openai", "OpenAI", mapOf("gpt" to OpenCodeModel("gpt", name = "GPT"))),
    OpenCodeProvider("duplicate", "Duplicate", mapOf("gpt" to OpenCodeModel("gpt", name = "GPT"))),
    OpenCodeProvider("empty", "Empty", mapOf("" to OpenCodeModel("", name = "")))
)
// The visible list contains only valid IDs, stable provider grouping, and no duplicate
// entry for the same provider/model pair. The same model ID under another provider
// remains a separate selectable entry.
```

Add an instrumentation assertion that opens the model picker with no models and expects a visible explanatory message rather than a blank `LazyColumn`.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*OpenCodeChatScreenTest*'`

Expected: FAIL because blank IDs and empty catalog behavior are not currently normalized.

- [ ] **Step 3: Implement normalization and explicit state**

Build the displayed provider/model entries from active models only, discard blank provider/model IDs, de-duplicate by `provider.id + model.id`, and use stable keys. Keep provider headers. Add separate copy for no providers versus providers with no matching models, including the active search query when useful. Ensure the empty-state predicate uses the same filtered collection as the rendered rows. Do not alter provider loading/authentication code unless inspection proves the passed `providers` list is empty because `OpenCodeApp` is not collecting it.

- [ ] **Step 4: Run focused tests and verify success**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*OpenCodeChatScreenTest*'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opencode/android/feature/chat/ModelPickerSheet.kt app/src/test
git commit -m "fix: show normalized provider model catalog"
```

### Task 3: Finish the existing Composer dock's reference-layout gaps

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/OpenCodeChatScreen.kt:288-373,489-578`
- Modify: `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ja/strings.xml` if new labels are needed
- Test: existing or new Compose tests under `app/src/androidTest/java/com/opencode/android/feature/chat/`

**Interfaces:**
- Consumes: existing `ChatUiState`, provider/model/agent/workspace lists, and all existing callbacks.
- Produces: a single bottom dock with the reference visual hierarchy and unchanged callback semantics.

- [ ] **Step 1: Add failing Compose assertions**

Extend the existing `composer_shows_model_variant_agent_context_and_attachment` test rather than creating a second harness. Assert the attachment button and message field content descriptions, the rounded dock's controls, and that pending image attachments remain immediately above it. Assert that the send button becomes enabled after entering text or adding an attachment.

- [ ] **Step 2: Run the Compose test and verify failure**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*Chat*'`

Expected: any newly added reference-layout assertion fails against the current two-container composer.

- [ ] **Step 3: Implement the unified dock**

Keep the already-working callbacks and selectors, but wrap the current input row and `ComposerControls` in one unified surface dock containing:

```kotlin
Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // rounded input panel: attachment, mic, text field, send/stop
        // horizontally scrollable compact control row
        // context/agent status row
    }
}
```

Keep the conversation in the weighted area above it. Keep attachment cards visible immediately above the dock. Make the compact controls fit narrow screens through horizontal scrolling. Preserve callbacks exactly, including `onAttach`, `onMic`, `onSendMessage`, `onAbort`, model selection, variant selection, workspace selection, and additional settings. Do not reimplement attachment, variant, context, or provider auth behavior already present on `main`.

- [ ] **Step 4: Run the Compose test and verify success**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*Chat*'`

Expected: PASS with the dock visible and all controls actionable.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opencode/android/feature/chat/OpenCodeChatScreen.kt app/src/main/res
git commit -m "feat: match reference chat composer layout"
```

### Task 4: Full verification and regression review

**Files:**
- Modify: only files required by verification fixes
- Test: all existing app tests and build tasks

**Interfaces:**
- Consumes: Tasks 1-3 production changes and tests.
- Produces: verified build with no regressions to attachments, sessions, permissions, tools, diffs, voice, or navigation.

- [ ] **Step 1: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintDebug`

Expected: PASS or only pre-existing warnings documented in the final report.

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Inspect the final diff**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only intended source, resource, test, and plan/spec files are changed.

- [ ] **Step 5: Commit verification fixes if needed**

```bash
git add app/src docs/superpowers
git commit -m "test: verify chat composer and model catalog"
```
