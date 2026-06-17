## ADDED Requirements

### Requirement: Client remembers the last BPM project workspace

The front-end application SHALL persist the identifier of the **last opened BPM project** (workspace) in browser storage so that users can return to the same project context without re-selecting from the list every time.

#### Scenario: Opening a project workspace updates memory

- **WHEN** the user navigates to the BPM project workspace route that includes a project identifier (e.g. `/default/bpm/project/:id`)
- **THEN** the application SHALL store that project identifier as the last workspace selection

### Requirement: Project list offers returning to the last workspace

When a last workspace identifier exists, the project list page SHALL provide a clear control that navigates the user to that project workspace route.

#### Scenario: Continue last workspace is available

- **WHEN** the user opens the BPM project list page and a last workspace identifier is present in storage
- **THEN** the page SHALL show an affordance (e.g. link or button) that navigates to `/default/bpm/project/{lastId}`

#### Scenario: No memory does not block the list

- **WHEN** no last workspace identifier is stored
- **THEN** the project list page SHALL behave as before without showing a misleading empty workspace banner
