## ADDED Requirements

### Requirement: Cross-origin OPTIONS preflight bypasses session authentication

The system SHALL NOT require a valid login session or token for **HTTP OPTIONS** requests used as CORS preflight, so that browsers can obtain CORS headers before sending the actual API request with credentials or custom headers.

#### Scenario: OPTIONS without Authorization succeeds for CORS

- **WHEN** a browser sends an `OPTIONS` request to an API path that would otherwise require authentication for non-OPTIONS methods
- **THEN** the request SHALL NOT fail solely due to missing or invalid `Authorization` (or equivalent) for the purpose of Sa-Token login enforcement on that path

### Requirement: CORS mapping supports common preflight headers and caching

The system SHALL expose a Spring CORS configuration that includes **`allowedHeaders`** (including headers required for `Authorization` and typical API usage), **`exposedHeaders`** as needed, and a non-zero **`maxAge`** for preflight result caching, consistent with configured `app.cors.allowed-origins`.

#### Scenario: Preflight lists required request headers

- **WHEN** the browser sends a CORS preflight that declares `Access-Control-Request-Headers` including `authorization` (or other configured custom headers)
- **THEN** the response SHALL allow those headers via `Access-Control-Allow-Headers` according to the CORS mapping

#### Scenario: Allowed origins remain explicit

- **WHEN** the browser sends a cross-origin request from an origin listed in application CORS configuration
- **THEN** the response SHALL include `Access-Control-Allow-Origin` for that origin (and related CORS rules SHALL apply as configured)
