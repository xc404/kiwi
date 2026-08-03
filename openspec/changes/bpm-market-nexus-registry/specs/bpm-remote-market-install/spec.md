## ADDED Requirements

### Requirement: Download with SHA-256 verification

Before installation, the system MUST download the artifact from `downloadUrl` and verify the content SHA-256 hex digest matches `sha256` on the index item.

#### Scenario: Checksum mismatch

- **WHEN** downloaded bytes SHA-256 does not match index `sha256`
- **THEN** installation SHALL be aborted with HTTP 400 and a checksum error message

### Requirement: Kiwi version compatibility

Before installation, if `kiwiMinVersion` is present, the system MUST compare it against configured `kiwi.bpm.remote-market.kiwi-version`.

#### Scenario: Incompatible Kiwi version

- **WHEN** instance Kiwi version is lower than `kiwiMinVersion`
- **THEN** installation SHALL be aborted with HTTP 409 and version details

### Requirement: Template remote install

`POST /bpm/remote-market/templates/{slug}/versions/{version}/install` SHALL download the template pack, verify checksum and version, check `requiredComponentKeys` against deployed components, then install as a new project using existing template pack import logic.

#### Scenario: Missing required components

- **WHEN** template lists `requiredComponentKeys` not present in the component library
- **THEN** the system SHALL return HTTP 409 with the list of missing keys

#### Scenario: Successful template install

- **WHEN** all checks pass
- **THEN** the system SHALL return project id and name like local `import-and-install`

### Requirement: Plugin remote install

`POST /bpm/remote-market/plugins/{slug}/versions/{version}/install` SHALL download the JAR, verify checksum and version, validate JAR metadata via existing plugin preview logic, copy to plugins directory, and reload.

#### Scenario: Successful plugin install

- **WHEN** JAR is valid and checksum matches
- **THEN** the plugin SHALL appear in installed plugins list after reload
