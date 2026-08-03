## ADDED Requirements

### Requirement: Index document schema

The remote market index MUST be a JSON document at `{base-url}{index-path}` with:

- `schemaVersion` (integer, currently 1)
- `generatedAt` (ISO-8601 string)
- `items` (array of market entries)

Each item MUST include: `type` (`template` or `plugin`), `slug`, `name`, `version`, `summary`, `downloadUrl`, `sha256`.

Each item SHOULD include: `category`, `tags`, `kiwiMinVersion`, `manifestUrl`, `signatureUrl`.

Template items SHOULD include: `kind`, `processCount`, `requiredComponentKeys`.

Plugin items SHOULD include: `componentKeys`, `mavenCoordinate` (`groupId`, `artifactId`, `version`).

#### Scenario: Valid index parsed

- **WHEN** index JSON conforms to schema version 1
- **THEN** the system SHALL deserialize all items for API responses

#### Scenario: Unsupported schema version

- **WHEN** `schemaVersion` is greater than supported
- **THEN** the system SHALL reject the index with a clear error on sync

### Requirement: Nexus directory layout

Published artifacts SHALL follow:

- Templates: `templates/{slug}/{version}/{slug}-{version}.kiwi-template-pack`
- Template manifest: `templates/{slug}/{version}/manifest.json`
- Plugins: `plugins/{groupIdPath}/{artifactId}/{version}/{artifactId}-{version}.jar`
- Plugin manifest: `plugins/{groupIdPath}/{artifactId}/{version}/manifest.json`
- Index: `market/index.json`

where `groupIdPath` is Maven `groupId` with `.` replaced by `/`.

#### Scenario: Relative URLs resolved

- **WHEN** an item's `downloadUrl` is relative to the source base URL
- **THEN** the system SHALL resolve it to an absolute URL before download
