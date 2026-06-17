## ADDED Requirements

### Requirement: Assistant client actions are dispatched through a pluggable handler chain

The Angular admin app SHALL dispatch `AiAssistantResponse.actions` through an orchestration layer that composes, in order: (1) handlers from the chat component `actionHandlers` input, (2) handlers registered via multi-provider `ASSISTANT_ACTION_HANDLERS`, (3) built-in handlers including `navigate`. For each action, the first handler whose `supports(action)` is true SHALL run `handle` at most once.

#### Scenario: Navigate remains the default behavior

- **WHEN** the assistant response includes an action with `type` `navigate` and a non-empty `path`, and no higher-priority handler claims that action
- **THEN** the app SHALL invoke `Router.navigate` with segments derived from `path` and `queryParams` as today, and SHALL surface a user-visible warning if navigation fails

#### Scenario: Page-specific handler runs before built-ins

- **WHEN** a parent passes a custom handler in `actionHandlers` that returns true from `supports` for a given action
- **THEN** that handler SHALL be invoked instead of a later handler in the chain for that action

### Requirement: Multiple navigates preserve legacy truncation semantics

When processing the `actions` array in order, if a `navigate` action is successfully handled by the built-in navigate handler (valid path and navigation invoked), the orchestrator SHALL NOT process any further actions in the same response.

#### Scenario: Actions after the first successful navigate are ignored

- **WHEN** `actions` is `[{ type: navigate, path: /a }, { type: navigate, path: /b }]`
- **THEN** the built-in handler processes the first navigate and the second action SHALL NOT be processed by the orchestrator in the same dispatch

### Requirement: Unknown action types do not break the chat UI

- **WHEN** an action has a `type` that no handler in the chain supports
- **THEN** the chat UI SHALL still display assistant `content` and SHALL NOT throw uncaught errors; the orchestrator MAY emit a non-throwing diagnostic (e.g. development console warning)

### Requirement: Embedding sites can attach handlers without backend changes

- **WHEN** a template uses `<app-chat [actionHandlers]="handlers" />` (or equivalent binding) with an array of `AssistantActionHandler` implementations
- **THEN** those handlers SHALL participate in the same dispatch chain as documented in the first requirement without requiring changes to `POST /ai/assistant`
