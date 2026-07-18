# OpenCode Android Initial MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android-only OpenCode client that connects to `opencode serve`, manages sessions, supports text/voice chat, can be selected as the Android digital assistant, and reuses offline Vosk wake-word detection.

**Architecture:** The app is a native Kotlin/Jetpack Compose client. All OpenCode interaction goes through an `OpenCodeBackend` interface backed by the official REST and SSE server API. Voice and wake-word components are adapted from the existing OpenClawAssistant project, while OpenClaw webhook code and branding are removed.

**Tech Stack:** Kotlin 1.9.22, Android Gradle Plugin 8.2.2, Jetpack Compose Material 3, Navigation Compose, OkHttp 4.12, OkHttp SSE, Gson, Kotlin Coroutines, EncryptedSharedPreferences, Vosk Android, JUnit 4, MockWebServer.

## Global Constraints

- App name is exactly `OpenCode Android`.
- Repository name is exactly `opencode-android`.
- Android-only; no iOS, Web, or desktop client code.
- Package and application ID are `com.opencode.android`.
- Minimum SDK is API 26; compile and target SDK are 34 for the first build.
- UI is always dark, using charcoal/navy surfaces and restrained blue/teal accents.
- OpenCode is the only agent engine; no Hermes or OpenClaw runtime integration.
- Remote OpenCode uses official `opencode serve` HTTP APIs.
- Secrets must use encrypted Android storage and must not appear in logs.
- The app must identify itself as an unofficial OpenCode client.
- Production behavior is developed test-first, except build configuration, generated resources, and mechanical project scaffolding.

---

## File Structure

```text
app/src/main/java/com/opencode/android/
├── OpenCodeApplication.kt                 dependency container
├── MainActivity.kt                        single-activity Compose host
├── api/
│   ├── OpenCodeApiClient.kt               REST and SSE transport
│   ├── OpenCodeApiModels.kt               API data models
│   ├── OpenCodeEventParser.kt             SSE JSON event parsing
│   └── OpenCodeUrl.kt                     endpoint validation/normalization
├── backend/
│   ├── OpenCodeBackend.kt                 backend contract
│   ├── RemoteOpenCodeBackend.kt           remote server implementation
│   └── LocalRuntimeStatus.kt              local-runtime capability/status model
├── data/
│   ├── ConnectionProfile.kt               saved connection model
│   ├── SecureSettingsRepository.kt        encrypted settings
│   └── AppRepository.kt                   selected connection/session state
├── speech/
│   ├── SpeechRecognizerManager.kt         Android speech-to-text
│   └── TTSManager.kt                      Android text-to-speech
├── assistant/
│   ├── OpenCodeVoiceInteractionService.kt system assistant service
│   ├── OpenCodeSessionService.kt          voice session host
│   ├── OpenCodeVoiceSession.kt            assistant UI and voice flow
│   └── OpenCodeRecognitionService.kt      recognition service declaration
├── hotword/
│   ├── HotwordService.kt                  Vosk foreground service
│   └── BootReceiver.kt                    optional restart after boot
├── ui/
│   ├── OpenCodeApp.kt                     NavHost and shared scaffold
│   ├── AppViewModel.kt                    app-level state
│   ├── components/                        shared cards and status chips
│   ├── home/HomeScreen.kt
│   ├── chat/ChatScreen.kt
│   ├── chat/ChatViewModel.kt
│   ├── connections/ConnectionsScreen.kt
│   ├── sessions/SessionsScreen.kt
│   ├── settings/SettingsScreen.kt
│   └── theme/                             dark theme tokens
└── util/AppError.kt                       user-facing error classification
```

---

### Task 1: Create isolated implementation branch and Android scaffold

**Files:**
- Create: Android Gradle project files under repository root
- Copy mechanically from: `/Users/yu-ga/AndroidStudioProjects/OpenClawAssistant`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `LICENSE`

**Interfaces:**
- Produces a buildable Compose Android project with package `com.opencode.android`.

- [ ] **Step 1: Create the isolated worktree and branch**

Run:

```bash
git worktree add .worktrees/initial-mvp -b feature/initial-mvp
```

Expected: a new worktree on `feature/initial-mvp`.

- [ ] **Step 2: Copy only project scaffold and reusable assets**

Copy Gradle wrapper/config, Vosk model assets, icon placeholders, voice interaction XML, and Android source tree from OpenClawAssistant. Exclude `.git`, `.gradle`, `.idea`, build outputs, `local.properties`, crash logs, and release artifacts.

- [ ] **Step 3: Rename project identity**

Set:

```kotlin
rootProject.name = "OpenCodeAndroid"
```

Set application values:

```kotlin
namespace = "com.opencode.android"
applicationId = "com.opencode.android"
minSdk = 26
targetSdk = 34
versionCode = 1
versionName = "0.1.0"
```

- [ ] **Step 4: Add API test dependencies**

Add:

```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
implementation("com.google.code.gson:gson:2.10.1")
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
```

- [ ] **Step 5: Verify scaffold build**

Run with Android Studio JBR:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug
```

Expected: exit code 0 before feature changes.

- [ ] **Step 6: Commit scaffold**

```bash
git add .
git commit -m "chore: scaffold OpenCode Android app"
```

---

### Task 2: Implement endpoint validation and secure connection storage

**Files:**
- Test: `app/src/test/java/com/opencode/android/api/OpenCodeUrlTest.kt`
- Test: `app/src/test/java/com/opencode/android/data/SecureSettingsRepositoryTest.kt`
- Create: `app/src/main/java/com/opencode/android/api/OpenCodeUrl.kt`
- Create: `app/src/main/java/com/opencode/android/data/ConnectionProfile.kt`
- Create: `app/src/main/java/com/opencode/android/data/SecureSettingsRepository.kt`

**Interfaces:**
- Produces `OpenCodeUrl.normalize(raw: String): Result<HttpUrl>`.
- Produces `ConnectionProfile(id, name, baseUrl, username, password, allowInsecureLan)`.
- Produces repository methods `connections()`, `upsertConnection()`, `deleteConnection()`, and `selectedConnectionId`.

- [ ] **Step 1: Write failing URL tests**

Tests must prove:

```kotlin
assertEquals("http://192.168.1.20:4096/", OpenCodeUrl.normalize("192.168.1.20:4096").getOrThrow().toString())
assertEquals("https://example.com/opencode/", OpenCodeUrl.normalize("https://example.com/opencode").getOrThrow().toString())
assertTrue(OpenCodeUrl.normalize("ftp://example.com").isFailure)
assertTrue(OpenCodeUrl.normalize("http://example.com").isFailure) // public cleartext rejected
assertTrue(OpenCodeUrl.normalize("http://100.64.0.10:4096").isSuccess) // Tailscale CGNAT accepted
```

- [ ] **Step 2: Run URL test and verify RED**

```bash
./gradlew testDebugUnitTest --tests '*OpenCodeUrlTest'
```

Expected: compilation failure because `OpenCodeUrl` does not exist.

- [ ] **Step 3: Implement minimal URL normalization**

Rules:

- Add `http://` when scheme is omitted.
- Add trailing slash.
- Allow HTTPS universally.
- Allow HTTP only for loopback, RFC1918 IPv4, `.local`, and `100.64.0.0/10` Tailscale addresses.
- Reject unsupported schemes and blank hosts.

- [ ] **Step 4: Verify URL tests GREEN**

Run the same test command; expected all URL tests pass.

- [ ] **Step 5: Write failing repository serialization test**

Use Robolectric-free pure serialization helper tests. Verify a connection list round-trips through JSON and passwords are excluded from `toString()`.

- [ ] **Step 6: Implement encrypted repository**

Use `MasterKey` and `EncryptedSharedPreferences`. Store one JSON array under `connections`, selection under `selected_connection`, and assistant settings under separate keys. Never log the stored JSON.

- [ ] **Step 7: Run all unit tests**

```bash
./gradlew testDebugUnitTest
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add secure OpenCode connections"
```

---

### Task 3: Implement OpenCode REST/SSE client

**Files:**
- Test: `app/src/test/java/com/opencode/android/api/OpenCodeApiClientTest.kt`
- Test: `app/src/test/java/com/opencode/android/api/OpenCodeEventParserTest.kt`
- Create: `app/src/main/java/com/opencode/android/api/OpenCodeApiModels.kt`
- Create: `app/src/main/java/com/opencode/android/api/OpenCodeEventParser.kt`
- Create: `app/src/main/java/com/opencode/android/api/OpenCodeApiClient.kt`

**Interfaces:**
- `suspend fun health(): OpenCodeHealth`
- `suspend fun sessions(): List<OpenCodeSession>`
- `suspend fun createSession(title: String?): OpenCodeSession`
- `suspend fun messages(sessionId: String): List<OpenCodeMessage>`
- `suspend fun providers(): ProviderCatalog`
- `suspend fun agents(): List<OpenCodeAgent>`
- `suspend fun promptAsync(sessionId: String, request: PromptRequest)`
- `suspend fun respondPermission(sessionId: String, permissionId: String, response: String, remember: Boolean): Boolean`
- `fun events(): Flow<OpenCodeEvent>`

- [ ] **Step 1: Write failing MockWebServer health/auth test**

Enqueue:

```json
{"healthy":true,"version":"1.2.3"}
```

Verify request path `/global/health` and Basic authentication header for username `opencode` and configured password.

- [ ] **Step 2: Run test and verify RED**

```bash
./gradlew testDebugUnitTest --tests '*OpenCodeApiClientTest'
```

- [ ] **Step 3: Implement health transport**

Use OkHttp calls on `Dispatchers.IO`, close every response body, redact auth data from exceptions, and map non-2xx codes to typed `OpenCodeApiException`.

- [ ] **Step 4: Add failing session/provider/message tests**

Cover the official endpoints and prompt body:

```json
{
  "model":{"providerID":"opencode","modelID":"deepseek-v4-flash-free"},
  "agent":"build",
  "parts":[{"type":"text","text":"hello"}]
}
```

- [ ] **Step 5: Implement models and API methods**

Use Gson data classes with nullable fields and tolerate unknown properties.

- [ ] **Step 6: Add failing SSE parser tests**

Test at least:

```json
{"type":"server.connected","properties":{}}
{"type":"message.part.updated","properties":{"part":{"id":"p1","sessionID":"s1","messageID":"m1","type":"text","text":"Hello"}}}
{"type":"permission.asked","properties":{"id":"perm1","sessionID":"s1","permission":"bash","patterns":["git status"]}}
{"type":"session.idle","properties":{"sessionID":"s1"}}
```

- [ ] **Step 7: Implement SSE flow**

Use `EventSources.createFactory(okHttpClient)`. Emit typed events, ignore unknown event types through `OpenCodeEvent.Unknown`, reconnect with bounded exponential delay while the Flow collector is active, and cancel EventSource in `awaitClose`.

- [ ] **Step 8: Verify all API tests**

```bash
./gradlew testDebugUnitTest --tests '*api*'
```

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/opencode/android/api app/src/test/java/com/opencode/android/api
git commit -m "feat: add OpenCode server client"
```

---

### Task 4: Add backend/repository state and chat behavior

**Files:**
- Test: `app/src/test/java/com/opencode/android/backend/RemoteOpenCodeBackendTest.kt`
- Test: `app/src/test/java/com/opencode/android/ui/chat/ChatViewModelTest.kt`
- Create: `app/src/main/java/com/opencode/android/backend/OpenCodeBackend.kt`
- Create: `app/src/main/java/com/opencode/android/backend/RemoteOpenCodeBackend.kt`
- Create: `app/src/main/java/com/opencode/android/data/AppRepository.kt`
- Create: `app/src/main/java/com/opencode/android/ui/chat/ChatViewModel.kt`

**Interfaces:**
- Backend contract defined in the design document.
- `ChatUiState` includes connection, session, messages, streamed text, permission requests, selected provider/model/agent, listening, speaking, and error.

- [ ] **Step 1: Write failing backend delegation test**

Verify `RemoteOpenCodeBackend` delegates health/session/prompt/permission calls to one client configured from a connection profile and exposes client events unchanged.

- [ ] **Step 2: Verify RED and implement backend**

Run backend test, implement minimal delegation, rerun to GREEN.

- [ ] **Step 3: Write failing ChatViewModel state tests**

Verify:

- Sending blank input does nothing.
- Sending text creates a session when none is selected.
- User message appears immediately.
- `message.part.updated` updates streamed assistant text.
- `session.idle` finalizes the assistant message.
- `permission.asked` adds an approval card.
- Responding removes the approval on success.
- Abort calls backend and clears running state.

- [ ] **Step 4: Implement minimal ViewModel state machine**

Do not persist duplicate conversation text locally; reload official messages when opening a session.

- [ ] **Step 5: Run backend/chat tests**

```bash
./gradlew testDebugUnitTest --tests '*RemoteOpenCodeBackendTest' --tests '*ChatViewModelTest'
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add OpenCode chat state"
```

---

### Task 5: Build dark Material 3 UI and navigation

**Files:**
- Create/replace files under `ui/theme`, `ui/components`, `ui/home`, `ui/chat`, `ui/connections`, `ui/sessions`, `ui/settings`
- Modify: `MainActivity.kt`
- Create: `OpenCodeApplication.kt`
- Create: `ui/OpenCodeApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`

**Interfaces:**
- UI consumes `AppRepository` and screen ViewModels only.
- Five bottom destinations: Home, Chat, Connections, Sessions, Settings.

- [ ] **Step 1: Add Compose semantics tests for critical components**

Create tests for connection form validation, send button disabled on blank input, and permission card action labels. Run to confirm RED because components do not exist.

- [ ] **Step 2: Implement theme tokens**

Use exact primary tokens:

```kotlin
Background = Color(0xFF0B1017)
Surface = Color(0xFF121A24)
SurfaceVariant = Color(0xFF1A2633)
Primary = Color(0xFF6EA8FE)
Secondary = Color(0xFF55C6C1)
Success = Color(0xFF62C58F)
Warning = Color(0xFFF2B766)
Error = Color(0xFFFF7A86)
```

- [ ] **Step 3: Implement connection flow**

Form fields: name, base URL, username defaulting to `opencode`, password, insecure-LAN acknowledgement. Test button calls `/global/health`; save is enabled only after valid URL.

- [ ] **Step 4: Implement home, sessions, chat, settings**

Use the approved calm dark visual language. Do not implement remote-desktop imagery or unsupported controls. Local runtime card must clearly say `Experimental / not installed` until Phase 3.

- [ ] **Step 5: Verify Compose/unit tests and debug build**

```bash
./gradlew testDebugUnitTest assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add OpenCode Android interface"
```

---

### Task 6: Port speech recognition, TTS, and system assistant

**Files:**
- Create files under `speech/` and `assistant/`
- Modify: `AndroidManifest.xml`
- Modify: `res/xml/voice_interaction_service.xml`
- Test: `app/src/test/java/com/opencode/android/assistant/AssistantProfileTest.kt`

**Interfaces:**
- `AssistantProfile` resolves backend/project/provider/model/agent and voice preferences.
- Voice session sends text through the same backend and session state machine.

- [ ] **Step 1: Write failing AssistantProfile resolution tests**

Verify missing model/agent falls back to server defaults, a deleted backend produces a configuration error, and continuous mode retains session ID.

- [ ] **Step 2: Implement profile model and resolver**

Store profile fields through `SecureSettingsRepository`.

- [ ] **Step 3: Port speech/TTS managers**

Rename packages and remove OpenClaw references. Keep Xiaomi-specific SpeechRecognizer cleanup. Replace forced speech rate 1.5 with configurable default 1.0.

- [ ] **Step 4: Port VoiceInteraction services**

Declare:

- `OpenCodeVoiceInteractionService`
- `OpenCodeSessionService`
- `OpenCodeVoiceSession`
- `OpenCodeRecognitionService`

The session pauses hotword listening, records speech, sends it to OpenCode, waits for final response, reads it with TTS, then either continues or closes.

- [ ] **Step 5: Run unit tests and manifest build**

```bash
./gradlew testDebugUnitTest processDebugMainManifest assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add Android OpenCode voice assistant"
```

---

### Task 7: Port Vosk wake word foreground service

**Files:**
- Create: `hotword/HotwordService.kt`
- Create: `hotword/BootReceiver.kt`
- Copy: `app/src/main/assets/model/**`
- Modify: `AndroidManifest.xml`
- Modify: string resources
- Test: `app/src/test/java/com/opencode/android/hotword/WakeWordConfigTest.kt`

**Interfaces:**
- Default phrase is `open code`.
- Settings provides normalized non-empty phrase list.
- Service actions are package-scoped `ACTION_PAUSE_HOTWORD` and `ACTION_RESUME_HOTWORD`.

- [ ] **Step 1: Write failing wake-word normalization tests**

Verify uppercase and extra whitespace normalize to lowercase single-spaced text, blank custom value falls back to `open code`, and preset values produce valid Vosk grammar JSON.

- [ ] **Step 2: Implement config and verify GREEN**

- [ ] **Step 3: Port service with OpenCode branding**

Foreground notification must state that the microphone is active and show the configured phrase. Detection opens the system assistant session rather than a webhook activity.

- [ ] **Step 4: Add boot behavior**

Only restart after boot when the user enabled wake word and granted required permissions. Handle Android 13 notification permission and Android 14 microphone foreground-service restrictions.

- [ ] **Step 5: Verify tests/build**

```bash
./gradlew testDebugUnitTest assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add offline OpenCode wake word"
```

---

### Task 8: Add local-runtime boundary, documentation, CI, and release verification

**Files:**
- Create: `backend/LocalRuntimeStatus.kt`
- Create: `backend/LocalOpenCodeBackend.kt`
- Create: `runtime/LocalRuntimeManager.kt`
- Test: `app/src/test/java/com/opencode/android/runtime/LocalRuntimeManagerTest.kt`
- Modify: `README.md`
- Create: `.github/workflows/android.yml`
- Create: `docs/LOCAL_RUNTIME.md`

**Interfaces:**
- `LocalRuntimeManager.status()` returns `NotInstalled`, `Installing`, `Ready(version, port)`, `Broken(reason)`, or `UnsupportedAbi`.
- Initial implementation does not claim to run OpenCode locally; it provides the stable boundary, state persistence, UI state, and a documented next implementation package.

- [ ] **Step 1: Write failing local runtime state tests**

Verify arm64 maps to `NotInstalled` without metadata, x86 maps to `UnsupportedAbi`, corrupt metadata maps to `Broken`, and a healthy metadata/port probe maps to `Ready`.

- [ ] **Step 2: Implement local runtime boundary**

Do not download or execute third-party binaries in this MVP. The UI exposes the capability as experimental and unavailable until installer assets are added in the dedicated local-runtime phase.

- [ ] **Step 3: Replace README**

Document:

- Unofficial client status
- PC command: `OPENCODE_SERVER_PASSWORD=... opencode serve --hostname 0.0.0.0 --port 4096`
- LAN/Tailscale security guidance
- App connection instructions
- Home assistant and wake-word setup
- Build instructions
- Current local-runtime limitation

- [ ] **Step 4: Add CI**

GitHub Actions uses JDK 17 and runs:

```bash
./gradlew testDebugUnitTest assembleDebug
```

- [ ] **Step 5: Full verification**

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew clean testDebugUnitTest assembleDebug
```

Then install to connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.opencode.android/.MainActivity
```

Capture logcat filtered to the package and verify no startup crash.

- [ ] **Step 6: Create GitHub repository and push branch**

After confirming GitHub CLI authentication:

```bash
gh repo create opencode-android --public --source=. --remote=origin --push
git push -u origin feature/initial-mvp
```

Do not expose secrets or `local.properties`.

- [ ] **Step 7: Commit final docs/CI/runtime boundary**

```bash
git add .
git commit -m "docs: complete OpenCode Android MVP"
```
