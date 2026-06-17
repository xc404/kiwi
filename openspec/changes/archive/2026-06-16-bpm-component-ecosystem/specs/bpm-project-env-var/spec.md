## ADDED Requirements

### Requirement: Project environment variables are stored per project

The system SHALL persist environment variables in collection `bpmProjectEnvVar` with fields `projectId`, `key`, `value`, `encrypted`, and optional `description`. The pair `(projectId, key)` MUST be unique.

#### Scenario: Create env var for a project

- **WHEN** an authorized user POSTs a new env var with `projectId`, `key`, and `value` for a project they own
- **THEN** the system saves the record and returns metadata without plaintext value when `encrypted` is true

### Requirement: Encrypted values are protected at rest and in API responses

When `encrypted` is true, the system SHALL encrypt `value` with AES using `app.password.secret` before persistence. GET list and GET by id MUST NOT return the decrypted value.

#### Scenario: Encrypted var update without changing secret

- **WHEN** user PUTs an update with empty `value` for an encrypted var
- **THEN** the stored ciphertext remains unchanged

### Requirement: Process start injects project environment variables

When starting a deployed process, the system SHALL load all env vars for `BpmProcess.projectId`, decrypt as needed, merge with user-supplied start variables (user keys override env keys), and pass them to the Operaton engine.

#### Scenario: Start with project env and user override

- **WHEN** project has `API_URL=https://api.example.com` and user starts with `variables: { "API_URL": "https://override" }`
- **THEN** the running instance uses `https://override` for `API_URL`

#### Scenario: Process without projectId

- **WHEN** `BpmProcess.projectId` is null or blank
- **THEN** start proceeds with only user-supplied variables (no env injection)

### Requirement: Users manage env vars in project UI

The admin UI SHALL provide CRUD for project environment variables under the BPM project workspace (tab or sub-page).

#### Scenario: List env vars in project settings

- **WHEN** user opens environment variables for a project
- **THEN** the UI lists keys, descriptions, and encrypted flag without showing secret values
