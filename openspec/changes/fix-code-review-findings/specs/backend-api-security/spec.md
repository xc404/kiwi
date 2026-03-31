## ADDED Requirements

### Requirement: Non-whitelisted REST endpoints require authentication

The system SHALL reject requests without a valid login session or bearer token for every REST handler that is not explicitly listed in the anonymous whitelist configuration or annotated as publicly accessible.

#### Scenario: BPM process API is protected

- **WHEN** a client calls `GET /bpm/process` or other BPM process definition endpoints without a valid `Authorization` token
- **THEN** the response SHALL indicate not-logged-in or unauthorized per Sa-Token global error handling

#### Scenario: BPM component API is protected

- **WHEN** a client mutates BPM component resources (`POST`, `PUT`, `DELETE` under `bpm/component`) without authentication
- **THEN** the response SHALL NOT succeed with HTTP 2xx

### Requirement: BPM project endpoints have consistent protection

The system SHALL apply the same authentication requirement to all CRUD operations on BPM projects, including get-by-id, update, and delete, unless a documented anonymous read path exists.

#### Scenario: Project mutation requires login

- **WHEN** a client calls `PUT` or `DELETE` on `/bpm/project/{id}` without authentication
- **THEN** the response SHALL indicate not-logged-in or unauthorized

### Requirement: Anonymous whitelist is explicit and minimal

The system SHALL document and implement an explicit list of paths that do not require login (e.g. sign-in, sign-out, public documentation if enabled).

#### Scenario: Sign-in remains accessible

- **WHEN** a client posts valid credentials to the sign-in endpoint
- **THEN** the request SHALL be processed without a prior session token
