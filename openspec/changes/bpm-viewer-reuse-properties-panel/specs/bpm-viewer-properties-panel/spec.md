## ADDED Requirements

### Requirement: Instance viewer layout matches editor properties region

The BPM process instance viewer page SHALL use a layout structure aligned with the BPM editor: a main canvas area and a right-hand sidebar dedicated to properties, using the same structural pattern as the editor (e.g. `nz-layout` with an `nz-sider` for the properties column and a scroll container for the properties content).

#### Scenario: User opens instance viewer

- **WHEN** the user navigates to the process instance viewer route
- **THEN** the diagram canvas occupies the primary content area and the properties UI is placed in a right sidebar whose width and scroll behavior are consistent with the BPM editor properties sidebar

### Requirement: Properties sidebar uses bpm-properties-panel

The process instance viewer SHALL render the `bpm-properties-panel` component as the primary right-hand properties UI instead of the standalone `bpm-instance-properties` component, while preserving read-only semantics for the diagram.

#### Scenario: Properties panel shows selection context

- **WHEN** the user selects an element on the read-only diagram (or the process root)
- **THEN** the properties panel reflects the current selection (including header consistent with the design-time panel) and does not expose modeling actions that mutate the deployed definition in a way that only the Modeler supports

### Requirement: Runtime variables remain available for the selected scope

The system SHALL continue to expose process-instance runtime variables for the current selection scope (process-level when root is selected, activity-scoped when an activity is selected) at least at the same level of detail as the previous `bpm-instance-properties` implementation.

#### Scenario: Filtered variables match scope

- **WHEN** the user selects the process root
- **THEN** variables shown for that scope are limited to process-scoped variables (no activity instance id), consistent with prior filtering rules

#### Scenario: Activity-scoped variables

- **WHEN** the user selects an activity that has runtime variable rows tied to activity instances
- **THEN** the variables displayed are filtered to those rows for the selected activity, consistent with prior filtering rules
