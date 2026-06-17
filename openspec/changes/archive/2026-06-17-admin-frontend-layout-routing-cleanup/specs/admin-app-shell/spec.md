## ADDED Requirements

### Requirement: Authenticated routes SHALL NOT use a `default` URL segment

The kiwi-admin frontend SHALL expose primary application routes without a leading `default` path segment after the user is authenticated. The main shell SHALL remain a single layout (formerly the default layout).

#### Scenario: Root redirect after load

- **WHEN** an unauthenticated user opens the application root URL appropriate for the app entry
- **THEN** the application SHALL follow existing authentication and entry flows unchanged except that post-login primary URLs SHALL NOT contain `/default` as a routing prefix

#### Scenario: Deep link to a business page

- **WHEN** a user navigates to a primary business route such as dashboard or system administration under the authenticated shell
- **THEN** the browser URL SHALL use paths of the form `/<primary-segment>/...` (for example `/dashboard/...` or `/system/...`) and SHALL NOT require `/default` before `<primary-segment>`

### Requirement: Tab refresh placeholder route SHALL align with the shell routing

The system SHALL provide an internal route used for tab refresh or empty placeholder behavior that is consistent with the shell route tree and SHALL NOT depend on a `/default/refresh-empty` path.

#### Scenario: Tab service navigates to refresh placeholder

- **WHEN** the tab or routing service performs navigation to the refresh-empty placeholder route
- **THEN** the navigation SHALL target the path defined for the new shell routing (without a `default` segment) and the nav layer SHALL treat that URL consistently when filtering or matching navigation events

### Requirement: In-app navigation and links SHALL use the new path prefix

All in-app programmatic navigation, `routerLink` bindings, and similar constructs that previously targeted `/default/...` or `default/...` for shell children SHALL be updated to the new paths so that navigation remains correct.

#### Scenario: Login success redirect

- **WHEN** a user successfully completes login through the standard login flow
- **THEN** the application SHALL navigate to the configured default landing route using a URL without a `default` segment (for example the dashboard analysis route under the new prefix scheme)

#### Scenario: Header or notice links to personal pages

- **WHEN** a user activates a link from shell-related UI to a personal area route (such as notifications)
- **THEN** the link SHALL resolve to the correct route under the updated URL scheme without a `default` segment
