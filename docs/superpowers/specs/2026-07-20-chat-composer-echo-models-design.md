# Chat Composer, Echo, and Model Catalog

## Goal

Match the supplied second screenshot's mobile chat composition area, stop user
input from appearing as an assistant response, and make the model picker show
the provider catalog reliably.

## Scope

- Replace the current split composer rows with one visually unified bottom dock.
- Keep attachment previews, voice input, send/stop behavior, model selection,
  variant/reasoning selection, agent selection, workspace selection, settings,
  and context usage available from the dock.
- Preserve existing message/tool/permission/diff rendering.
- Ensure persisted and streamed messages are rendered according to their server
  role and never duplicate a user message as assistant content.
- Normalize provider/model catalog data for display and show an explicit empty
  or unavailable state instead of an apparently broken picker.

## Design

### Composer dock

The dock uses a surface-colored container with a rounded outlined input panel.
The first row contains attachment, microphone, multiline input, and send/stop.
The second row contains compact model, variant/reasoning, agent, workspace, and
additional-settings controls. Context percentage and the active agent appear on
the bottom status row. Controls remain horizontally scrollable on narrow devices
and use bottom sheets or menus rather than expanding the conversation area.

Pending attachments stay above the dock and sent attachments remain attached to
the user message. Image thumbnails use the existing safe bitmap decode path;
non-images remain file cards.

### Message role isolation

`ChatViewModel.toUiMessages` only uses `OpenCodeMessageInfo.role` to determine
whether a message is a user or assistant item. Streaming updates are accepted
only for assistant parts and are ignored when their part/message identifies a
user message. The optimistic local user message remains the sole user bubble.

Retry continues to resend the last user message explicitly and is not inferred
from assistant content.

### Model catalog

The picker flattens valid active models from all providers while retaining
provider grouping. IDs and names are normalized for blank values, and duplicate
provider/model entries are removed. When no models are available, the picker
shows whether the catalog is empty or still unavailable instead of rendering a
blank list. Selecting a model updates the existing ViewModel configuration and
variant metadata flow.

## Error handling

- Missing model data produces an explicit empty-state message.
- Catalog entries with malformed or blank IDs are skipped safely.
- Attachment decode failures continue to render a file card.
- Unknown event roles/types are ignored without mutating the conversation.

## Testing

- ViewModel tests for user-role history and streamed user-part suppression.
- Model picker tests for provider grouping, empty state, duplicate removal, and
  selection callbacks.
- Compose tests for the unified dock, attachment preview, model controls, and
  unavailable model state.
- Existing unit, instrumentation, lint, and release build checks remain required.

## Acceptance criteria

- The bottom chat area visually follows the second reference: one large rounded
  composer panel with controls and model/agent settings below it.
- Sending a message creates one user bubble and assistant output does not repeat
  the exact user text unless the server genuinely returns it as assistant text.
- Opening the model picker shows available models grouped by provider, or a
  clear explanation when the catalog is unavailable/empty.
- Existing attachments, voice input, abort, session switching, permissions,
  tool cards, and diff summaries continue to work.
