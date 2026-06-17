## ADDED Requirements

### Requirement: Authenticated users can list their inbox messages

The system SHALL expose a **GET** API that returns the current login user's inbox messages, ordered from newest to oldest by creation time.

The API SHALL require an authenticated session (same rules as other Sa-Token protected APIs).

#### Scenario: Successful list

- **WHEN** an authenticated client calls `GET /notifications`
- **THEN** the response SHALL include only messages belonging to the current user and SHALL be ordered by creation time descending

#### Scenario: Unauthenticated access is rejected

- **WHEN** a client calls `GET /notifications` without a valid login session
- **THEN** the request SHALL NOT return the inbox payload as success (same behavior as other protected endpoints)

### Requirement: Messages support channel classification and optional tag metadata

Each message SHALL include a **channel** value among `notice`, `message`, and `todo`, and MAY include a **tag** with display text and color for UI badges.

#### Scenario: Channel is present

- **WHEN** the client receives a message payload
- **THEN** the payload SHALL include a `channel` field with one of the supported values

### Requirement: Admin shell header shows a channel-aware preview

The header bell dropdown SHALL load inbox data from the **GET /notifications** API and SHALL group preview items by **channel** (`notice` / `message` / `todo`).

#### Scenario: Preview is limited

- **WHEN** the user opens the bell dropdown
- **THEN** each channel tab SHALL show at most a small fixed number of items (preview), and the UI SHALL provide a path to open the full message center page
