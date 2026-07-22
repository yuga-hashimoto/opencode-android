# Question UI, Voice Feedback, Modes, Models, and GitHub Authentication

## Goal

Make the chat workflow visibly interactive and complete the settings-based
authentication experience. The change covers missing question-tool rendering,
voice-input feedback, bottom composer mode controls, a provider-oriented model
picker, GitHub Copilot authentication, and GitHub git authentication.

## Scope

### In scope

- Render `question.asked` requests in the active chat and submit answers.
- Show listening, processing, error, and live audio-level feedback when the
  microphone is used.
- Expose Build, Plan, and Auto accept-style execution modes from the bottom
  composer without leaving the chat.
- Redesign the model picker around favorites, providers, provider model lists,
  search, selected state, and explicit loading/empty/error states.
- Keep GitHub Copilot provider authentication separate from GitHub git
  authentication.
- Authenticate git through GitHub OAuth Device Flow using a fixed application
  Client ID, encrypted token storage, and a local-runtime credential helper.

### Out of scope

- Replacing the OpenCode server or implementing a model gateway.
- Storing ChatGPT, Copilot, or GitHub tokens in plaintext.
- Sending local GitHub git tokens to remote runtimes without an explicit remote
  credential contract.
- Supporting SSH-key management in this change.

## Architecture

### Question events

Add a typed question event to the API event model and parser. A question request
contains its ID, session ID, one or more questions, answer options, and whether
multiple answers are allowed. The backend exposes an answer operation that sends
the selected or entered answers to the active runtime.

`ChatViewModel` owns pending questions in `ChatUiState`, filtered to the active
session. A question card is rendered independently from ordinary messages and
permissions so it remains visible even when the conversation has no assistant
message. Successful answers remove the card; failed answers keep it visible and
show an actionable error. Unknown or incomplete fields fall back to readable
text rather than dropping the request.

### Voice feedback

The existing speech recognizer `RmsChanged` callback remains the audio-level
source. `ChatViewModel` stores a normalized `voiceLevel` alongside
`isListening`, `partialText`, and the existing error state. The composer renders
an active microphone treatment, an `音声入力中` status label, and animated bars
whose height is driven by the current level. Ready, processing, and error states
remain visually distinct. The implementation does not add a second recorder or
persist microphone data.

### Composer modes

The composer keeps model, agent, workspace, and mode controls in a horizontally
scrollable bottom row. Build and Plan map to the existing agent configuration;
Auto accept is an explicit execution setting and is sent only when supported by
the backend contract. Selected chips use filled styling and a check indicator.
Unsupported modes are disabled with a short explanation instead of silently
changing behavior.

### Model picker

The picker is a modal sheet with the following navigation:

1. Favorites appear at the top with provider name and model metadata.
2. Provider rows show provider icon treatment, name, model count, and a chevron.
3. Selecting a provider opens its searchable model list.

Models with blank IDs, inactive status, or duplicate provider/model keys are
excluded. The picker distinguishes loading, unavailable, empty, and populated
states. Selecting a model updates the existing preferences and closes the sheet.

### Authentication

Provider authentication remains backed by the current OpenCode provider OAuth
and API-key endpoints. It is presented as the Copilot/model connection path.

GitHub git authentication is a separate repository and state flow:

1. Request a device code from GitHub using the fixed OAuth Client ID.
2. Show the user code and verification URL, opening the browser when requested.
3. Poll for the access token with cancellation and expiry handling.
4. Store the token and basic account metadata in encrypted app storage.
5. On local runtime startup or credential refresh, generate/update a temporary
   git credential helper inside the runtime environment.
6. On disconnect, remove encrypted credentials and the runtime helper.

The settings screen shows GitHub Copilot and Git operations as separate cards.
Remote runtimes show the git-auth capability as unsupported unless a future
remote credential interface is available. A missing Client ID disables the
Device Flow action and explains the configuration issue.

## Error Handling

- Malformed question events are ignored only when no stable ID/session exists;
  otherwise they render a safe fallback card.
- Answer failures preserve the question and show the failure message.
- Voice recognition errors stop the active animation and preserve any partial
  text already received.
- Missing model data produces an explicit unavailable or empty state.
- Device Flow cancellation, expiry, denial, network errors, and disconnect
  failures are user-visible and do not erase valid credentials prematurely.
- Git credential helper failures do not expose the token in logs or UI text.

## Testing

- Parser tests for valid, incomplete, and unknown question events.
- ViewModel tests for question lifecycle, session filtering, answer failures,
  and RMS normalization.
- Compose tests for question cards, answer controls, voice waveform/status,
  bottom mode chips, and model-picker navigation states.
- GitHub Device Flow tests for code exchange, polling, cancellation, expiry,
  encrypted persistence, and disconnect cleanup using a fake HTTP client and
  fake credential runtime.
- Existing unit tests, Compose instrumentation tests, lint, debug assembly,
  and release/R8 checks remain required.

## Acceptance Criteria

- A live question tool request is visible and answerable in the active chat.
- Pressing the microphone provides unmistakable listening feedback and a live
  waveform-like animation driven by speech level.
- Build, Plan, and Auto accept-style modes are selectable from the chat bottom.
- The model picker resembles the supplied provider/favorites hierarchy and no
  longer appears blank when data is unavailable.
- Settings can authenticate GitHub Copilot separately from Git operations.
- GitHub Device Flow credentials are encrypted, usable by local git operations,
  removable, and never written to logs.
