## ADDED Requirements

### Requirement: Scenario authoring process orchestration

The system SHALL orchestrate AI workflow authoring for a user application scenario via an internal Kiwi/Operaton process (process definition key configurable, default `kiwi_ai_workflow_authoring`) whose steps include keyword extraction, catalog retrieval, LLM plan/generation, validation, optional repair loops, optional plugin-install confirmation, preview confirmation, and save of the target process definition.

#### Scenario: Start from designer scenario

- **WHEN** a logged-in user submits an application scenario in the BPM designer AI assistant for a target process
- **THEN** the system starts (or correlates to) the authoring process instance with at least `scenario` and `targetProcessId` variables and does not overwrite the target process definition until preview is confirmed

#### Scenario: Meta-process isolation

- **WHEN** the authoring process runs
- **THEN** its process definition and instance MUST remain distinct from the user's `targetProcessId` business process being edited

### Requirement: Catalog retrieval and injection without RAG as primary path

The system SHALL build a catalog for each authoring run by extracting search keywords and/or tags from the scenario (rules and/or a bounded LLM extraction call), querying installed components and template-pack summaries (and installable-but-not-installed plugin/component candidates), truncating to a configured Top-N, and injecting that catalog into the LLM plan/generation step. Vector RAG SHALL NOT be required for v1.

#### Scenario: Catalog marks installability

- **WHEN** the catalog includes a component or plugin that is not installed but available from the market
- **THEN** the catalog entry SHALL be marked as available to install (not as already usable at runtime)

#### Scenario: Plan ids constrained to catalog

- **WHEN** the LLM produces a plan that references component ids
- **THEN** those ids MUST appear in the current run's catalog; ids marked installable MUST be flagged as requiring install

#### Scenario: MCP is auxiliary

- **WHEN** the primary scenario-authoring path runs
- **THEN** the system MUST NOT rely solely on the model spontaneously calling MCP search tools to discover the catalog; server-side retrieval and injection is the primary discovery mechanism

### Requirement: Multi-layer BPMN validation and issue dispatch

The system SHALL validate candidate BPMN XML before preview/save and collect all applicable issues in one pass where feasible, including at least: well-formed XML with `definitions` root; structural checks (sequence flow endpoints / orphan nodes as defined by the implementation); resolution of `componentId` references; detection of missing plugins when a referenced component requires an uninstalled plugin JAR; and missing required component parameters when metadata is available. Issues SHALL be aggregated into a dispatch outcome for process gateways (`PASS`, `REPAIR`, `INSTALL`, or `ASK`).

#### Scenario: Invalid XML does not save target

- **WHEN** validation yields blocking issues
- **THEN** the system MUST NOT persist the candidate XML to the target process definition

#### Scenario: Repair loop bound

- **WHEN** dispatch is `REPAIR`
- **THEN** the process MAY regenerate or patch and re-validate up to a configured maximum repair round count (default 3), after which it SHALL route to ask-user rather than unbounded looping

#### Scenario: Missing plugin routes to confirmation

- **WHEN** validation detects a referenced component that requires a plugin not installed but available to install
- **THEN** dispatch SHALL be `INSTALL` (or equivalent) and the process SHALL wait for user confirmation before invoking install APIs

### Requirement: Human confirmation for preview and plugin install

The system SHALL require explicit user confirmation before saving the candidate BPMN to the target process and before installing plugins discovered as required by the candidate. After successful confirmation and save, the designer canvas MAY show the saved definition; after plugin install, validation SHALL run again.

#### Scenario: Preview then save

- **WHEN** validation passes
- **THEN** the user is offered a preview of the candidate BPMN on the designer and the target process is saved only after the user confirms

#### Scenario: User declines preview

- **WHEN** the user declines to save the previewed candidate
- **THEN** the target process definition remains unchanged and the authoring process MAY continue with adjustment (e.g., return to plan/generate) without having persisted the declined XML

#### Scenario: User confirms plugin install

- **WHEN** the user confirms installation of a required plugin
- **THEN** the system installs via existing plugin/market install mechanisms and re-enters validation

### Requirement: Designer and assistant bridge

The system SHALL expose a bridge so the BPM designer AI chat can start the authoring process, present stage status (e.g., cataloging, validating, awaiting preview, awaiting plugin install), complete human tasks (preview/install/ask), and apply preview import without treating unvalidated XML as final save.

#### Scenario: Stage visible in chat

- **WHEN** an authoring process instance is active for the current designer process
- **THEN** the assistant UI SHALL be able to show the current authoring stage to the user

#### Scenario: Feature flag

- **WHEN** AI workflow authoring via the internal process is disabled by configuration
- **THEN** the system SHALL fall back to the previous assistant behavior or hide the scenario-authoring entry without breaking non-authoring assistant use
