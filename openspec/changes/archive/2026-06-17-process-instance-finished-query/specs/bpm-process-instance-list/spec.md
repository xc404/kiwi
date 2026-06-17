## ADDED Requirements

### Requirement: Process instance list supports lifecycle state filter

The system SHALL allow filtering the BPM process instance list by **instance lifecycle state**: running only, completed only, or all historic instances.

The default filter SHALL be **running** (unfinished process instances only).

#### Scenario: Default list shows running instances

- **WHEN** the client calls the process instance list API without state parameters (or with the default state for running)
- **THEN** the response SHALL include only process instances that have not ended (Camunda: unfinished)

#### Scenario: User selects completed instances

- **WHEN** the client requests the list with completed state selected
- **THEN** the response SHALL include only process instances that have ended (Camunda: completed)

#### Scenario: User selects all instances

- **WHEN** the client requests the list with “all” state selected
- **THEN** the response SHALL not apply unfinished-only or completed-only filters (full historic query; performance may vary)

#### Scenario: Mutually exclusive Camunda query semantics

- **WHEN** the server builds the historic process instance query
- **THEN** it SHALL NOT combine `unfinished()` and `completed()` on the same query

### Requirement: Admin UI exposes instance state in search

The process instance list page (crud-page) SHALL expose a search control for instance state (running / completed / all) with **running** as the default selection.

#### Scenario: Search form includes state

- **WHEN** the user opens the process instance list page
- **THEN** the search area SHALL include an instance-state control defaulting to running

### Requirement: View action opens instance viewer in a new browser tab

The list row action to view a process instance SHALL open the instance viewer route in a **new browser tab** (not same-tab navigation), preserving the SPA hash URL for the current deployment.

#### Scenario: User opens view from the list

- **WHEN** the user triggers the view action on a process instance row
- **THEN** the application SHALL open `/bpm/process-instance/:processInstanceId` in a new tab (e.g. `window.open` with `noopener` / `noreferrer` where appropriate)
