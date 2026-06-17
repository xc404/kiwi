## ADDED Requirements

### Requirement: Default committed configuration contains no real secrets

The default Spring configuration committed to the repository SHALL NOT contain production database passwords, Redis passwords, MongoDB credentials, password hashing secrets, or Camunda admin passwords as real values.

#### Scenario: Clone without local overrides does not expose real credentials

- **WHEN** a developer clones the repository and opens the default `application.yml` (or primary template)
- **THEN** sensitive fields SHALL use placeholders, environment variable references, or empty values with documentation pointing to local override files

### Requirement: Local secrets are gitignored

The system SHALL provide a documented pattern (e.g. `application-local.yml`) for developer-specific secrets and SHALL list that pattern in `.gitignore` so it is not committed by default.

#### Scenario: Local override file is ignored

- **WHEN** a developer creates the local secrets file per README instructions
- **THEN** `git status` SHALL NOT list that file unless force-added

### Requirement: Production-safe SQL logging defaults

The default configuration for production-oriented profiles SHALL NOT enable MyBatis SQL logging to standard output in a way that leaks statements in typical deployments.

#### Scenario: Production profile does not use StdOut SQL logger

- **WHEN** the application runs with the production profile
- **THEN** MyBatis SHALL NOT use `StdOutImpl` as the log implementation for SQL mapping
