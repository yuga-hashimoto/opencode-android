# Chat, Navigation, and Provider Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Home reachable from every screen, unify runtime connection status, rebuild the chat composer around the requested mobile workflow, and support both API-key and ChatGPT Plus/Pro OAuth authentication for OpenAI through OpenCode.

**Architecture:** Keep `OpenCodeApplication`, `RuntimeCatalogRepository`, and the existing feature ViewModels as the integration points. Add small pure helpers for navigation and context calculation, extend the existing backend/API models instead of introducing a second model system, and keep provider credentials owned by the local OpenCode runtime whenever OAuth is used.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose Navigation, Android Activity Result APIs, Gson, OkHttp, Kotlin coroutines/StateFlow, JUnit 4, Compose UI tests, Gradle Android release/R8 build.

## Global Constraints

- `RuntimeTarget.state` is the authoritative live connection state.
- OpenCode remains the model gateway; Android must not implement an independent inference path.
- API keys remain encrypted in `SecureSettingsRepository`.
- OAuth credentials remain in OpenCode's `auth.json` and must not be overwritten by app-managed API-key synchronization.
- Existing attachment size/count limits, voice recognition flow, permission cards, and streaming events must continue to work.
- Context usage must be displayed as unavailable when token or context-limit data is absent.
- Only transient dialogs and external browser authentication are excluded from persistent bottom navigation.
- Existing unit tests, Compose instrumentation tests, lint, and the R8 release build are required before completion.

---

## File Map

### Navigation and runtime state

- Create: `app/src/main/java/com/opencode/android/ui/TopLevelNavigation.kt` for pure route mapping and the idempotent top-level navigation helper.
- Modify: `app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt` to use the helper and keep Home navigation available from nested routes.
- Modify: `app/src/main/java/com/opencode/android/data/repository/RuntimeCatalogRepository.kt` to emit selected-target state changes without discarding catalog data.
- Modify: `app/src/main/java/com/opencode/android/feature/home/HomeViewModel.kt` to derive connected state from live runtime state and health.
- Create: `app/src/test/java/com/opencode/android/ui/TopLevelNavigationTest.kt`.
- Create: `app/src/test/java/com/opencode/android/feature/home/HomeViewModelTest.kt`.
- Modify: `app/src/test/java/com/opencode/android/data/repository/RuntimeCatalogRepositoryTest.kt`.

### API and chat state

- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeApiModels.kt` for model variants, limits, message token metadata, file parts, and prompt variant.
- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeApiClient.kt` for variant serialization and provider-auth endpoints.
- Modify: `app/src/main/java/com/opencode/android/runtime/OpenCodeBackend.kt` for provider-auth operations.
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalOpenCodeBackend.kt` and `app/src/main/java/com/opencode/android/runtime/remote/RemoteOpenCodeBackend.kt` to delegate the new operations.
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt` for variant state, attachment retention, and context usage calculation.
- Create: `app/src/main/java/com/opencode/android/feature/chat/ContextUsage.kt` for a pure context percentage calculation.
- Modify: `app/src/test/java/com/opencode/android/core/api/OpenCodeApiClientTest.kt`.
- Modify: `app/src/test/java/com/opencode/android/feature/chat/ChatViewModelTest.kt`.
- Create: `app/src/test/java/com/opencode/android/feature/chat/ContextUsageTest.kt`.

### Chat UI

- Replace internals of: `app/src/main/java/com/opencode/android/feature/chat/OpenCodeChatScreen.kt` with a compact header, conversation list, bottom composer, settings sheet, attachment thumbnails, and context indicator.
- Modify: `app/src/androidTest/java/com/opencode/android/feature/chat/OpenCodeChatScreenTest.kt` for composer and attachment assertions.

### Provider authentication and local setup

- Create: `app/src/main/java/com/opencode/android/core/api/OpenCodeProviderAuthModels.kt` for auth methods and OAuth response transport types.
- Modify: `app/src/main/java/com/opencode/android/feature/settings/SettingsViewModel.kt` for auth-method loading, API-key refresh, and OAuth orchestration.
- Modify: `app/src/main/java/com/opencode/android/feature/settings/SettingsScreen.kt` for provider method selection, OAuth launch, and status/error presentation.
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalProviderCredentialStore.kt` to remove an app-managed provider ID when OAuth takes ownership.
- Modify: `app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt` to pass browser/OAuth callbacks into Settings.
- Modify: `app/src/main/AndroidManifest.xml` only if the OpenCode OAuth response requires an app deep-link callback; otherwise use the server callback URL without adding a custom scheme.
- Modify: `app/src/test/java/com/opencode/android/runtime/local/LocalProviderCredentialStoreTest.kt`.
- Create or modify: `app/src/test/java/com/opencode/android/feature/settings/SettingsViewModelTest.kt`.
- Modify: `docs/LOCAL_RUNTIME.md` with API-key and ChatGPT Plus/Pro OAuth setup instructions.

---

## Task 1: Lock Down Top-Level Navigation

**Files:**
- Create: `app/src/main/java/com/opencode/android/ui/TopLevelNavigation.kt`.
- Modify: `app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt`.
- Test: `app/src/test/java/com/opencode/android/ui/TopLevelNavigationTest.kt`.

**Interfaces:**
- Produces `internal fun topLevelRouteFor(route: String?, topLevelRoutes: Set<String>): String?`.
- Produces `internal fun NavHostController.navigateTopLevel(route: String, homeRoute: String)`.

- [ ] **Step 1: Write failing route-mapping tests**

```kotlin
@Test
fun `nested route maps to its top level parent`() {
    assertEquals("activity", topLevelRouteFor("session-detail", setOf("home", "chat", "activity", "settings")))
}

@Test
fun `unknown route has no selected tab`() {
    assertNull(topLevelRouteFor("workspaces", setOf("home", "chat", "activity", "settings")))
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.ui.TopLevelNavigationTest`

Expected: FAIL because `TopLevelNavigation.kt` and `topLevelRouteFor` do not exist.

- [ ] **Step 3: Implement route mapping and idempotent navigation**

Implement the route mapping with explicit nested-route ownership:

```kotlin
internal fun topLevelRouteFor(route: String?, topLevelRoutes: Set<String>): String? = when (route) {
    "workspace-detail", "local-runtime-management" -> "home"
    "session-detail" -> "activity"
    else -> route?.takeIf { it in topLevelRoutes }
}

internal fun NavHostController.navigateTopLevel(route: String, homeRoute: String) {
    navigate(route) {
        popUpTo(homeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

Update `OpenCodeApp.kt` so tab clicks call `navigateTopLevel`, `showBottomBar` is true for every application route except transient dialogs, and the selected tab uses `topLevelRouteFor(backStackEntry?.destination?.route, topLevelRoutes)`.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.ui.TopLevelNavigationTest`

Expected: PASS.

- [ ] **Step 5: Commit the navigation change**

```bash
git add app/src/main/java/com/opencode/android/ui/TopLevelNavigation.kt app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt app/src/test/java/com/opencode/android/ui/TopLevelNavigationTest.kt
git commit -m "fix: keep home navigation available from nested screens"
```

## Task 2: Unify Runtime Connection State

**Files:**
- Modify: `app/src/main/java/com/opencode/android/data/repository/RuntimeCatalogRepository.kt`.
- Modify: `app/src/main/java/com/opencode/android/feature/home/HomeViewModel.kt`.
- Test: `app/src/test/java/com/opencode/android/data/repository/RuntimeCatalogRepositoryTest.kt`.
- Create: `app/src/test/java/com/opencode/android/feature/home/HomeViewModelTest.kt`.

**Interfaces:**
- Add `val runtimeState: RuntimeState` to `RuntimeCatalogState`.
- `HomeUiState.connected` returns true for `RuntimeState.Connected` or a healthy catalog health response.

- [ ] **Step 1: Add failing Home connection-state tests**

```kotlin
@Test
fun `home is connected when target state is connected without catalog health`() {
    val state = HomeUiState(runtimeState = RuntimeState.Connected("1.2.3"))
    assertTrue(state.connected)
}

@Test
fun `catalog failure does not erase successful connection state`() = runTest {
    // Use the existing fake RuntimeTarget and repository fixture.
    // Make connect return a healthy response, then make listProviders fail.
    // Assert repository.state.value.runtimeState is Connected and error is non-null.
}
```

- [ ] **Step 2: Run the focused tests and verify the new behavior fails**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.feature.home.HomeViewModelTest --tests com.opencode.android.data.repository.RuntimeCatalogRepositoryTest`

Expected: the new assertions fail because Home currently checks only `health.version` and the repository does not emit target state changes.

- [ ] **Step 3: Observe selected target state in the repository**

When `registry.selected` changes, launch a `collectLatest` on `target.state`. For every state emission, update only `runtime` and `runtimeState` in `RuntimeCatalogState`; do not clear sessions, providers, agents, workspaces, or the last catalog error. When `load(target)` succeeds, write both `health` and `runtimeState = target.state.value`. When connection fails, write the failure state and preserve a diagnostic error.

Use `RuntimeState` in the imports and initialize the new property from `registry.selected.value?.state?.value ?: RuntimeState.Disconnected`.

- [ ] **Step 4: Update Home derivation**

Replace `val connected: Boolean get() = version != null` with:

```kotlin
val connected: Boolean
    get() = runtimeState is RuntimeState.Connected || version != null
```

Use the connected-state version when `health?.version` is absent so the Home subtitle remains useful.

- [ ] **Step 5: Run focused tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.feature.home.HomeViewModelTest --tests com.opencode.android.data.repository.RuntimeCatalogRepositoryTest`

Expected: PASS.

- [ ] **Step 6: Commit the state unification**

```bash
git add app/src/main/java/com/opencode/android/data/repository/RuntimeCatalogRepository.kt app/src/main/java/com/opencode/android/feature/home/HomeViewModel.kt app/src/test/java/com/opencode/android/data/repository/RuntimeCatalogRepositoryTest.kt app/src/test/java/com/opencode/android/feature/home/HomeViewModelTest.kt
git commit -m "fix: unify home and workspace connection state"
```

## Task 3: Extend API Models for Variants, Attachments, and Context

**Files:**
- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeApiModels.kt`.
- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeApiClient.kt`.
- Create: `app/src/main/java/com/opencode/android/feature/chat/ContextUsage.kt`.
- Test: `app/src/test/java/com/opencode/android/core/api/OpenCodeApiClientTest.kt`.
- Create: `app/src/test/java/com/opencode/android/feature/chat/ContextUsageTest.kt`.

**Interfaces:**
- `data class OpenCodeModelLimit(val context: Long? = null, val output: Long? = null)`.
- `data class OpenCodeModelVariant(val name: String? = null, val reasoningEffort: String? = null)`.
- `OpenCodeModel` gains `val limit: OpenCodeModelLimit?` and `val variants: Map<String, OpenCodeModelVariant>`.
- `PromptRequest` gains `val variant: String? = null`.
- `OpenCodeMessageInfo` gains optional `tokens` data with input/output/reasoning/cache values.
- `OpenCodePart` gains optional `fileName`, `mimeType`, and `url` fields using the server's `filename`, `mime`, and `url` names.
- `fun contextUsagePercent(inputTokens: Long?, contextLimit: Long?): Int?` returns a clamped rounded percentage or null.

- [ ] **Step 1: Write failing serialization and calculation tests**

```kotlin
@Test
fun `prompt variant is serialized`() = runTest {
    client.promptAsync("s1", PromptRequest(text = "hi", variant = "high"))
    val body = server.lastRequestBody()
    assertEquals("high", body["variant"].asString)
}

@Test
fun `context percentage is rounded and clamped`() {
    assertEquals(25, contextUsagePercent(250L, 1000L))
    assertEquals(100, contextUsagePercent(1200L, 1000L))
    assertNull(contextUsagePercent(null, 1000L))
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.core.api.OpenCodeApiClientTest --tests com.opencode.android.feature.chat.ContextUsageTest`

Expected: FAIL because variant and context fields are not implemented.

- [ ] **Step 3: Add model fields and serialize optional variant**

Add Gson-compatible nullable fields with defaults. In `OpenCodeApiClient.promptAsync`, add:

```kotlin
request.variant?.takeIf { it.isNotBlank() }?.let { addProperty("variant", it) }
```

Do not emit the property for null or blank values so older OpenCode servers continue to accept requests.

- [ ] **Step 4: Implement the pure context calculation**

```kotlin
internal fun contextUsagePercent(inputTokens: Long?, contextLimit: Long?): Int? {
    if (inputTokens == null || contextLimit == null || inputTokens < 0 || contextLimit <= 0) return null
    return ((inputTokens.toDouble() / contextLimit.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}
```

- [ ] **Step 5: Run focused tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.core.api.OpenCodeApiClientTest --tests com.opencode.android.feature.chat.ContextUsageTest`

Expected: PASS.

- [ ] **Step 6: Commit the API model change**

```bash
git add app/src/main/java/com/opencode/android/core/api/OpenCodeApiModels.kt app/src/main/java/com/opencode/android/core/api/OpenCodeApiClient.kt app/src/main/java/com/opencode/android/feature/chat/ContextUsage.kt app/src/test/java/com/opencode/android/core/api/OpenCodeApiClientTest.kt app/src/test/java/com/opencode/android/feature/chat/ContextUsageTest.kt
git commit -m "feat: support model variants and context metadata"
```

## Task 4: Extend Chat ViewModel State and Behavior

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt`.
- Modify: `app/src/test/java/com/opencode/android/feature/chat/ChatViewModelTest.kt`.

**Interfaces:**
- `ChatUiState` gains `selectedVariant: String?`, `availableVariants: List<String>`, and `contextUsagePercent: Int?`.
- `ChatMessage` gains `attachments: List<PendingAttachment>`.
- Add `fun selectVariant(variant: String?)`.

- [ ] **Step 1: Add failing ViewModel tests**

```kotlin
@Test
fun `selected variant is included in sent prompt`() = runTest(dispatcher) {
    viewModel.selectVariant("high")
    viewModel.sendMessage("review this")
    advanceUntilIdle()
    assertEquals("high", backend.lastPrompt?.variant)
}

@Test
fun `sent user message retains attachment preview data`() = runTest(dispatcher) {
    viewModel.addAttachment("photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
    viewModel.sendMessage("look")
    advanceUntilIdle()
    assertEquals("photo.jpg", viewModel.uiState.value.messages.first().attachments.single().fileName)
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.feature.chat.ChatViewModelTest`

Expected: FAIL because variant and message attachment retention are absent.

- [ ] **Step 3: Add variant and attachment state**

On `selectVariant`, update only the selected value. When model configuration is selected, derive `availableVariants` from the selected `OpenCodeModel`, defaulting to null when none are advertised. In `sendMessage`, copy `attachments` into the new user `ChatMessage` before clearing `pendingAttachments`. Include `variant = _uiState.value.selectedVariant` in `PromptRequest`.

For historical file parts, convert `filename`, `mime`, and data URL metadata into file-card attachment metadata when bytes are not locally available. Never attempt to decode an absent or malformed URL.

- [ ] **Step 4: Update context usage after history and message events**

Read the latest message token metadata and selected model context limit. Set `contextUsagePercent = contextUsagePercent(tokens.input, model.limit.context)`; set it to null when either input is missing. Keep the calculation in the pure helper from Task 3.

- [ ] **Step 5: Run focused tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.feature.chat.ChatViewModelTest`

Expected: PASS.

- [ ] **Step 6: Commit the ViewModel change**

```bash
git add app/src/main/java/com/opencode/android/feature/chat/ChatViewModel.kt app/src/test/java/com/opencode/android/feature/chat/ChatViewModelTest.kt
git commit -m "feat: retain chat attachments and variant state"
```

## Task 5: Rebuild the Bottom Chat Composer

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/chat/OpenCodeChatScreen.kt`.
- Test: `app/src/androidTest/java/com/opencode/android/feature/chat/OpenCodeChatScreenTest.kt`.

**Interfaces:**
- Add `onSelectVariant: (String?) -> Unit` and `onOpenAdditionalSettings: () -> Unit` to the screen signature; keep `onSelectWorkspace` for the workspace chip and existing callbacks unchanged.
- Keep existing callbacks for `onMic`, `onAttach`, `onRemoveAttachment`, `onSendMessage`, `onAbort`, and permissions.

- [ ] **Step 1: Add failing Compose assertions**

```kotlin
@Test
fun composer_shows_model_variant_agent_and_context_controls() {
    composeRule.setContent { testChat(state = ChatUiState(selectedModelId = "gpt-5", selectedVariant = "high", selectedAgentId = "build", contextUsagePercent = 6)) }
    composeRule.onNodeWithText("gpt-5").assertIsDisplayed()
    composeRule.onNodeWithText("high").assertIsDisplayed()
    composeRule.onNodeWithText("Build").assertIsDisplayed()
    composeRule.onNodeWithText("6%").assertIsDisplayed()
}

@Test
fun composer_shows_image_attachment_preview() {
    val attachment = PendingAttachment(fileName = "photo.jpg", mimeType = "image/jpeg", bytes = validTinyJpeg)
    composeRule.setContent { testChat(state = ChatUiState(pendingAttachments = listOf(attachment))) }
    composeRule.onNodeWithContentDescription("photo.jpg").assertIsDisplayed()
}
```

- [ ] **Step 2: Run the instrumentation tests and verify the new assertions fail**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.opencode.android.feature.chat.OpenCodeChatScreenTest`

Expected: FAIL because controls are currently in the header and attachments are filename-only chips.

- [ ] **Step 3: Implement the compact header and bottom composer**

Keep the conversation `LazyColumn` weighted between a compact session header and a non-scrolling composer dock. Move workspace/model/agent selection out of the large header. Add chips/buttons below the text field for model, variant, Build/Plan agent, workspace, and additional settings.

Use `ModalBottomSheet` for the additional settings menu. Each selector calls the existing ViewModel callbacks and dismisses itself. The agent selector must show primary agents including `build` and `plan` when returned by the server.

- [ ] **Step 4: Render attachments**

For `image/*`, decode bytes with `BitmapFactory.decodeByteArray` inside `remember(attachment.id)` and render an `Image` thumbnail with a content description containing the file name. For other files render a card containing filename and MIME type. Keep remove buttons on pending attachments.

User messages render attachment previews before the message text. Assistant/tool/reasoning cards keep their existing behavior.

- [ ] **Step 5: Render voice and context states**

Use the existing `onMic` callback and show a selected/listening state when `state.isListening` is true. Show `state.contextUsagePercent` as `"N%"` and a neutral unavailable label when null. The send button is disabled only when there is no text and no pending attachment; the stop button remains available while running.

- [ ] **Step 6: Run the instrumentation tests and verify they pass**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.opencode.android.feature.chat.OpenCodeChatScreenTest`

Expected: PASS.

- [ ] **Step 7: Commit the composer redesign**

```bash
git add app/src/main/java/com/opencode/android/feature/chat/OpenCodeChatScreen.kt app/src/androidTest/java/com/opencode/android/feature/chat/OpenCodeChatScreenTest.kt
git commit -m "feat: redesign chat composer for mobile controls"
```

## Task 6: Add Provider Auth API and OAuth Transport

**Files:**
- Create: `app/src/main/java/com/opencode/android/core/api/OpenCodeProviderAuthModels.kt`.
- Modify: `app/src/main/java/com/opencode/android/core/api/OpenCodeApiClient.kt`.
- Modify: `app/src/main/java/com/opencode/android/runtime/OpenCodeBackend.kt`.
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalOpenCodeBackend.kt`.
- Modify: `app/src/main/java/com/opencode/android/runtime/remote/RemoteOpenCodeBackend.kt`.
- Test: `app/src/test/java/com/opencode/android/core/api/OpenCodeApiClientTest.kt`.

**Interfaces:**
- `data class ProviderAuthMethod(val id: String, val label: String, val type: String)`.
- `data class ProviderAuthAuthorization(val url: String, val method: String? = null, val state: String? = null, val callbackUrl: String? = null)`.
- `suspend fun providerAuthMethods(): Map<String, List<ProviderAuthMethod>>`.
- `suspend fun authorizeProvider(providerId: String, methodId: String): ProviderAuthAuthorization`.
- `suspend fun completeProviderOAuth(providerId: String, methodId: String, callback: Map<String, String>): Boolean`.

- [ ] **Step 1: Inspect the running server schema before coding response adapters**

Use the documented OpenCode endpoint or a local runtime endpoint:

```bash
curl -fsS http://127.0.0.1:<port>/doc > /tmp/opencode-doc.html
```

Confirm the exact JSON names for `/provider/auth`, `/provider/{id}/oauth/authorize`, and `/provider/{id}/oauth/callback`. Map those names with Gson `@SerializedName` rather than assuming Kotlin property names.

- [ ] **Step 2: Add failing API client tests**

Add MockWebServer tests asserting:

```kotlin
assertEquals("ChatGPT Plus/Pro", client.providerAuthMethods()["openai"]!!.first().label)
assertEquals("https://auth.example", client.authorizeProvider("openai", "oauth").url)
assertTrue(client.completeProviderOAuth("openai", "oauth", mapOf("code" to "abc", "state" to "s1")))
```

- [ ] **Step 3: Implement the API client and backend delegation**

Use `GET provider/auth`, `POST provider/{id}/oauth/authorize`, and `POST provider/{id}/oauth/callback`. Send only non-empty callback fields. Treat 404/405 as unsupported OAuth and expose that as a typed failure for the UI. Delegate all methods through both local and remote backends.

- [ ] **Step 4: Run focused API tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.core.api.OpenCodeApiClientTest`

Expected: PASS.

- [ ] **Step 5: Commit the provider API layer**

```bash
git add app/src/main/java/com/opencode/android/core/api/OpenCodeProviderAuthModels.kt app/src/main/java/com/opencode/android/core/api/OpenCodeApiClient.kt app/src/main/java/com/opencode/android/runtime/OpenCodeBackend.kt app/src/main/java/com/opencode/android/runtime/local/LocalOpenCodeBackend.kt app/src/main/java/com/opencode/android/runtime/remote/RemoteOpenCodeBackend.kt app/src/test/java/com/opencode/android/core/api/OpenCodeApiClientTest.kt
git commit -m "feat: expose OpenCode provider authentication APIs"
```

## Task 7: Add API-Key and OAuth Controls to Settings

**Files:**
- Modify: `app/src/main/java/com/opencode/android/feature/settings/SettingsViewModel.kt`.
- Modify: `app/src/main/java/com/opencode/android/feature/settings/SettingsScreen.kt`.
- Modify: `app/src/main/java/com/opencode/android/runtime/local/LocalProviderCredentialStore.kt`.
- Modify: `app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt`.
- Test: `app/src/test/java/com/opencode/android/runtime/local/LocalProviderCredentialStoreTest.kt`.
- Create or modify: `app/src/test/java/com/opencode/android/feature/settings/SettingsViewModelTest.kt`.

**Interfaces:**
- `SettingsUiState` gains `providerAuthMethods`, `oauthProviderId`, `oauthError`, and `oauthMessage`.
- `SettingsViewModel.refreshProviderAuth()` loads methods from the selected runtime.
- `SettingsViewModel.beginOAuth(providerId: String, methodId: String): ProviderAuthAuthorization?`.
- `SettingsViewModel.completeOAuth(providerId: String, methodId: String, callback: Map<String, String>): Boolean`.
- `LocalProviderCredentialStore.unmanageProvider(providerId: String)` removes the ID from managed synchronization without deleting unrelated credentials.

- [ ] **Step 1: Add failing credential ownership tests**

```kotlin
@Test
fun `unmanaged provider is not overwritten on runtime sync`() {
    val memory = memoryStore(mapOf("openai" to "api-key"), setOf("openai"))
    memory.store.unmanageProvider("openai")
    val authFile = memory.store.syncToRuntime(memory.rootfs)
    assertTrue(authFile.readText().contains("openai"))
    assertTrue("openai" !in memory.managedIds)
}
```

- [ ] **Step 2: Run focused credential tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.runtime.local.LocalProviderCredentialStoreTest`

Expected: FAIL because `unmanageProvider` does not exist.

- [ ] **Step 3: Implement credential ownership and refresh behavior**

When API-key save succeeds, keep the current encrypted storage and call `catalog.refresh()` so the provider/model picker updates. When OAuth begins, call `unmanageProvider(providerId)` before opening the browser. On successful OAuth completion, refresh the catalog and preserve existing OAuth data. Never report OAuth success until the callback endpoint returns true.

- [ ] **Step 4: Implement Settings UI and browser launch callback**

Add a provider authentication section that:

- lists providers and saved/API/OAuth status;
- offers API key input for manual methods;
- offers a ChatGPT Plus/Pro OAuth button for `openai` when advertised;
- opens `Intent.ACTION_VIEW` with the authorization URL;
- displays cancellation, unsupported, callback, and success messages;
- refreshes the model catalog after success.

Pass a browser-launch callback from `OpenCodeApp`. If the server response includes a local app callback URI, register only that exact URI in the manifest and route its query parameters to `completeOAuth`; otherwise rely on the server-owned callback and refresh after the browser returns.

- [ ] **Step 5: Run focused tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.opencode.android.runtime.local.LocalProviderCredentialStoreTest --tests com.opencode.android.feature.settings.SettingsViewModelTest`

Expected: PASS.

- [ ] **Step 6: Commit the settings/auth UI**

```bash
git add app/src/main/java/com/opencode/android/feature/settings/SettingsViewModel.kt app/src/main/java/com/opencode/android/feature/settings/SettingsScreen.kt app/src/main/java/com/opencode/android/runtime/local/LocalProviderCredentialStore.kt app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt app/src/main/AndroidManifest.xml app/src/test/java/com/opencode/android/runtime/local/LocalProviderCredentialStoreTest.kt app/src/test/java/com/opencode/android/feature/settings/SettingsViewModelTest.kt
git commit -m "feat: support API key and OAuth provider setup"
```

## Task 8: Connect Chat Controls and Update Documentation

**Files:**
- Modify: `app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt`.
- Modify: `app/src/main/java/com/opencode/android/feature/chat/OpenCodeChatScreen.kt`.
- Modify: `docs/LOCAL_RUNTIME.md`.
- Modify: `app/src/main/res/values/strings.xml`.
- Modify: `app/src/main/res/values-ja/strings.xml`.

- [ ] **Step 1: Pass all new state and callbacks from `OpenCodeApp`**

Wire `selectedVariant`, `availableVariants`, `contextUsagePercent`, and `chatViewModel::selectVariant` into `OpenCodeChatScreen`. Keep `settingsViewModel::selectModel`, `settingsViewModel::selectAgent`, `chatViewModel::selectWorkspace`, voice input, attachment launch, permission response, send, and abort callbacks connected.

- [ ] **Step 2: Add Japanese and English strings**

Add resource-backed labels for model variant, thinking effort, Build, Plan, context usage, context unavailable, OAuth login, API key, provider connected, OAuth cancelled, and OAuth unsupported. Remove new hard-coded user-facing strings from the chat/settings composables.

- [ ] **Step 3: Document local OpenCode setup**

Document both supported paths:

```text
API key: Settings -> Provider credentials -> provider id `openai` -> API key -> Save -> refresh models.
ChatGPT Plus/Pro: Settings -> Provider credentials -> OpenAI -> ChatGPT Plus/Pro -> browser login -> return -> refresh models.
```

State that the model picker is populated from the connected OpenCode runtime and that arbitrary config-file-only models are not edited from Android in this release.

- [ ] **Step 4: Run Compose instrumentation tests**

Run: `./gradlew connectedDebugAndroidTest`

Expected: PASS, including attachment, composer, permission, and existing wake-word tests.

- [ ] **Step 5: Commit integration and docs**

```bash
git add app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt app/src/main/java/com/opencode/android/feature/chat/OpenCodeChatScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-ja/strings.xml docs/LOCAL_RUNTIME.md
git commit -m "feat: connect chat controls and document provider setup"
```

## Task 9: Full Verification and Release Candidate

**Files:**
- No source changes unless verification exposes a failure.

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 2: Run lint**

Run: `./gradlew lintDebug`

Expected: `BUILD SUCCESSFUL` with no new errors.

- [ ] **Step 3: Run instrumentation tests on API matrix**

Run: `./gradlew connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL` on the connected emulator. CI must continue to cover API 26 and API 34.

- [ ] **Step 4: Build the R8 release APK**

Run: `./gradlew assembleRelease`

Expected: `BUILD SUCCESSFUL`, including `minifyReleaseWithR8` and `lintVitalRelease`.

- [ ] **Step 5: Perform manual acceptance checks**

Install the release APK and verify:

1. Home opens from Chat, Activity, Settings, Workspaces, Workspace Detail, Session Detail, and Local Runtime Management.
2. Workspace and Home both show the same connected state.
3. Selecting an image shows a thumbnail and the sent user bubble retains it.
4. Voice input starts/stops and inserts the recognized message.
5. Model, variant, Build/Plan, workspace, and additional settings open from the bottom composer.
6. Context usage shows a percentage when server metadata exists and an unavailable label otherwise.
7. OpenAI API-key login populates models.
8. ChatGPT Plus/Pro OAuth completes without the next local runtime startup overwriting OAuth credentials.

- [ ] **Step 6: Review the final diff and commit any verification fix**

Run: `git status`, `git diff --check`, `git diff --stat`, and `git log --oneline -10`.

Only after all checks pass, create the release commit/tag according to `docs/RELEASE.md`.
