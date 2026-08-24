## ADDED Requirements

### Requirement: Remote market source configuration

The system SHALL read remote market sources from `kiwi.bpm.remote-market.sources` when `enabled` is true.

Each source MUST include `id`, `name`, `base-url`, and `index-path`. Optional `username` and `password` SHALL be used for HTTP Basic authentication.

#### Scenario: Disabled by default

- **WHEN** `kiwi.bpm.remote-market.enabled` is false or omitted
- **THEN** remote market API endpoints SHALL return an empty list or indicate remote market is disabled

#### Scenario: Multiple sources merged

- **WHEN** multiple sources are configured
- **THEN** the system SHALL fetch each source's index and merge items for listing

### Requirement: Index cache and sync

The system SHALL cache fetched `market/index.json` per source in memory for `cache-ttl-seconds` (default 300).

#### Scenario: Manual sync

- **WHEN** client calls `POST /bpm/remote-market/sync`
- **THEN** the system SHALL bypass cache TTL and re-fetch all configured source indexes

#### Scenario: Cache hit

- **WHEN** a list request occurs within TTL after a successful fetch
- **THEN** the system SHALL serve items from cache without HTTP request

### Requirement: Source fetch failure

When a source index cannot be fetched, the system SHALL log the error and continue with other sources if configured.

#### Scenario: Partial source failure

- **WHEN** one of two sources returns HTTP 500
- **THEN** items from the healthy source SHALL still appear in the list
