## ADDED Requirements

### Requirement: CORS allows explicit origins per environment

The system SHALL configure CORS allowed origins from configuration (properties or environment) such that production deployments can restrict origins to known front-end URLs instead of a universal wildcard alone.

#### Scenario: Configurable origin list

- **WHEN** operators set allowed origins for the deployment environment
- **THEN** the application SHALL reject browser cross-origin requests from non-listed origins according to Spring CORS behavior

### Requirement: Development convenience without weakening production defaults

The system SHALL separate development and production CORS defaults so that local development (e.g. `http://localhost:4201`) remains usable while production defaults are strict unless overridden.

#### Scenario: Local frontend can call local API

- **WHEN** developers run the front-end on the documented local port and the back-end with the local profile
- **THEN** browser requests from that origin SHALL be permitted for API calls as configured
