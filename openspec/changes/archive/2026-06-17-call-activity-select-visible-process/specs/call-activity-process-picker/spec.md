## ADDED Requirements

### Requirement: Call Activity branches on component binding

For a `bpmn:CallActivity` in the BPM design property panel, the system SHALL use the component-library path when `componentId` is non-empty (after trim), and the process-pick path when `componentId` is absent or blank.

#### Scenario: Component library Call Activity

- **WHEN** the selected Call Activity has a non-empty `componentId` in element extension properties
- **THEN** the「流程」group SHALL show a read-only component selector bound to `componentId`
- **AND** the system SHALL NOT show an editable「选择流程」control for `processId`

#### Scenario: Palette Call Activity without component

- **WHEN** the selected Call Activity has no `componentId` (missing or whitespace only)
- **THEN** the「流程」group SHALL show an editable「选择流程」control bound to `processId`
- **AND** the system SHALL NOT require `componentId` to configure the callee reference

### Requirement: Process picker lists user-visible processes

The process-pick path SHALL populate the selector from the authenticated process-definition list API (`GET /bpm/process` with paginated `content`), respecting server-side visibility for the current user.

#### Scenario: Options loaded for selector

- **WHEN** the user opens the「选择流程」control on a process-pick Call Activity
- **THEN** the system SHALL request the visible process list and display each option with human-readable name and process id as value

#### Scenario: Exclude current process from options

- **WHEN** the editor has a loaded current process definition id
- **THEN** the selector options SHALL NOT include that same id

### Requirement: Process selection persists processId only

When the user selects a process in the process-pick path, the system SHALL persist `processId` in extension properties through `ElementModel` and SHALL NOT load subprocess IO via `as-component` or set `calledElement` as part of this change.

#### Scenario: User selects a process

- **WHEN** the user chooses a process id in「选择流程」
- **THEN** the element SHALL have `processId` set to that id in extension properties
- **AND** the system SHALL NOT call `GET /bpm/process/{id}/as-component` to populate property tabs
- **AND** the system SHALL NOT update BPMN `calledElement` in this change

#### Scenario: User clears selection

- **WHEN** the user clears「选择流程」
- **THEN** `processId` SHALL be removed from extension properties

### Requirement: Mutual exclusivity of componentId and processId

The system SHALL NOT keep both `componentId` and `processId` on the same Call Activity when assigning either binding.

#### Scenario: Switch from process to component

- **WHEN** a component id is assigned (e.g. via component library drag)
- **THEN** `processId` SHALL be cleared and component-path UI SHALL apply
