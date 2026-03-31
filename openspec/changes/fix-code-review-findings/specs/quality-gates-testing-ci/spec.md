## ADDED Requirements

### Requirement: Automated test proves API authentication enforcement

The system SHALL include at least one automated test that verifies an unauthenticated request to a previously under-protected business endpoint receives a non-success authentication outcome.

#### Scenario: Unauthenticated BPM request fails

- **WHEN** a test performs an HTTP request to a secured BPM endpoint without credentials
- **THEN** the response status or body SHALL match the project's convention for unauthenticated access (e.g. 401 or unified error code)

### Requirement: CI workflow runs backend tests

The repository SHALL include a continuous integration workflow that runs Maven tests for the admin backend module (with `-am` as needed for dependencies) on push or pull request to the main development branch.

#### Scenario: Workflow invokes Maven test

- **WHEN** a contributor opens a pull request
- **THEN** the CI workflow SHALL execute `mvn` test for the backend module and report failure if tests fail

### Requirement: Frontend lint is part of quality gate (advisory or required)

The system SHALL run the Angular project's lint script in CI, with policy documented whether failures block merge initially.

#### Scenario: Lint command runs in CI

- **WHEN** CI runs on a change that touches the front-end or global quality configuration
- **THEN** `npm run lint` (or project-equivalent) SHALL be executed in the front-end directory with Node version compatible with Angular 21
