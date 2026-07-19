# Chat, Navigation, and Provider Authentication

## Goal

Fix the top-level navigation and runtime connection status inconsistencies, and
redesign the chat composer around the requested mobile workflow. The same
change also makes provider authentication understandable and usable for both
API keys and ChatGPT Plus/Pro OAuth on a local OpenCode runtime.

## Scope

### In scope

- Reliable navigation from top-level tabs and nested screens back to Home.
- A single connection-state source for Workspace and Home screens.
- Chat composer controls at the bottom of the chat screen.
- Image/file attachment previews before sending and in user messages.
- Voice input using the existing speech recognizer flow.
- Model, agent mode, model variant/thinking effort, workspace, and additional
  settings from the composer.
- Context usage display when the OpenCode server provides token and context
  limit data.
- Provider authentication through API keys and OpenCode OAuth endpoints.
- Refreshing the provider/model catalog after authentication.

### Out of scope

- Replacing the OpenCode server or implementing an independent model gateway.
- Storing ChatGPT credentials in Android app storage when OpenCode can store
  them in its own `auth.json`.
- Pretending context usage is known when the server does not expose token data.
- Full editing of arbitrary `opencode.json` files from Android in this pass.

## Architecture

### Navigation

Top-level destinations remain Home, Chat, Activity, and Settings. A dedicated
top-level navigation helper will distinguish tab navigation from ordinary
screen navigation:

- Selecting a tab pops nested destinations to the Home graph root, then selects
  the requested tab with `launchSingleTop` and state restoration.
- Home remains reachable even when the current screen is Workspaces, Workspace
  Detail, Session Detail, or Local Runtime Management.
- The bottom navigation remains visible for Workspaces, Workspace Detail,
  Session Detail, and Local Runtime Management so Home is always one tap away.
  Only transient dialogs and external browser authentication are excluded from
  the persistent navigation surface.
- Existing pending-session behavior remains unchanged.

### Runtime connection state

`RuntimeTarget.state` is the authoritative live connection state. The catalog
repository will observe the selected target state and emit state updates so
screens react even when no catalog request completes. Catalog health remains
useful for the version and diagnostics, but Home's connected state will be true
when either a healthy health response or `RuntimeState.Connected` is available.

Partial failures from sessions, providers, agents, or workspaces will not erase
the successful connection state. The UI will show connection success and a
separate catalog warning when appropriate.

### Chat state and data flow

`ChatViewModel` remains the owner of composer state and message transformation.
The UI receives an immutable `ChatUiState` and callbacks. The state will gain:

- selected variant/thinking effort;
- attachment previews attached to user messages;
- optional context usage percentage and token details;
- model variant options derived from the selected model.

`PromptRequest` will carry the selected variant and the existing provider,
model, agent, and attachment fields. The backend/API layer will serialize only
non-empty optional values.

## Chat UI

The screen follows the supplied second screenshot without copying its exact
branding:

1. Compact session header with title, runtime, and connection status.
2. Scrollable conversation area with user/assistant bubbles and existing tool,
   command, reasoning, and permission cards.
3. Composer dock pinned to the bottom:
   - attachment button;
   - multiline text field;
   - microphone button with listening state;
   - send or stop button;
   - compact model chip;
   - variant/thinking chip;
   - Build/Plan agent chip;
   - workspace and additional-settings chip.
4. Context usage and current agent are shown below the chips. Unknown usage is
   displayed as unavailable rather than an invented percentage.

Pending image attachments use Android bitmap decoding for a lightweight
thumbnail. The same attachment metadata is retained on the outgoing user
message so the sent bubble can display its preview. Non-image files display a
file card with name and MIME type. Size and count limits remain enforced by the
view model.

Model and settings selectors use a bottom sheet or anchored menu so they do not
push the conversation off-screen. Existing provider and agent lists are reused.

## Provider Authentication

### API key

The existing encrypted `LocalProviderCredentialStore` remains the storage for
app-managed API keys. The UI will make the provider ID explicit, show saved
status, and refresh the catalog after saving. For local runtime startup, keys
continue to be synchronized as API entries in OpenCode's `auth.json`.

### OAuth

The app will query OpenCode's provider auth methods and expose OAuth when the
selected provider supports it. OAuth is delegated to the OpenCode server:

1. Request available methods from `/provider/auth`.
2. Request authorization from `/provider/{id}/oauth/authorize`.
3. Open the returned authorization URL in the Android browser.
4. Submit the returned callback data to
   `/provider/{id}/oauth/callback` when required by the response.
5. Refresh provider/auth/model data and show the authenticated state.

OAuth credentials are owned by OpenCode and preserved in its `auth.json`. If a
provider has an app-managed API key, switching to OAuth must not let the next
runtime startup overwrite the OAuth entry. The app will clear or disable the
managed API-key entry when the user explicitly switches that provider to OAuth.

The implementation must support both local and remote runtime targets through
the same backend interface. If a server does not expose OAuth, the UI shows the
available API-key path and a clear unsupported message.

## Model Variants and Context

The provider model response will retain variant names and context limits when
available. The selected variant is sent as `variant` in the prompt request.
Common OpenAI variants such as `none`, `minimal`, `low`, `medium`, `high`, and
`xhigh` are displayed only when advertised or supported by the model metadata.

Message token metadata will be parsed when supplied by the OpenCode server.
Context usage is calculated as:

`used input tokens / model context limit * 100`

If either value is missing, the UI displays an unavailable state. This avoids
misleading users with a fabricated percentage.

## Error Handling

- Navigation actions are idempotent and do not create duplicate top-level
  destinations.
- Runtime connection failures remain separate from catalog endpoint failures.
- OAuth cancellation, callback failure, and unsupported providers produce a
  user-visible message without clearing existing valid credentials.
- Attachment decode failures show a file card or a recoverable error instead of
  crashing composition.
- Unknown variant/context fields are ignored for compatibility with older
  OpenCode servers.

## Testing

- Unit tests for top-level navigation route selection and back-stack behavior.
- Unit tests proving `RuntimeState.Connected` makes Home connected even when
  catalog health is absent, and partial catalog failures preserve connection.
- Chat view model tests for variant propagation, attachment retention, and
  context percentage calculation.
- Compose tests for attachment preview, composer controls, variant/agent
  selectors, voice callback, and context unavailable state.
- API client tests for variant serialization and provider auth request/response
  handling.
- Existing unit tests, Compose instrumentation tests, lint, and release R8
  build remain required.

## Acceptance Criteria

- Home can be opened from every visible top-level or nested screen.
- Workspace and Home show the same connected/disconnected result.
- Chat attachments visibly show thumbnails or file cards.
- Voice input, model selection, Build/Plan selection, variant selection, and
  additional settings are usable from the bottom composer area.
- Context usage is shown when data exists and explicitly unavailable otherwise.
- Local OpenCode can authenticate OpenAI using an API key or ChatGPT Plus/Pro
  OAuth, then exposes the resulting models in the model picker.
