## ADDED Requirements

### Requirement: Call Activity custom inputs on input tab

For `bpmn:CallActivity`, the property panel「输入」tab SHALL include a「自定义输入」section that allows users to add, edit, and remove rows, persisting each row as a `camunda:inputParameter` under `camunda:InputOutput` (namespace `inputParameter`), with the same interaction model as the existing「自定义输出」section.

#### Scenario: Add custom input row

- **WHEN** the user adds a row in「自定义输入」and sets a non-empty parameter name and value
- **THEN** the system SHALL create or update the corresponding `inputParameter` on the element
- **AND** the value SHALL be readable on reload via `ElementModel.getValue` with namespace `inputParameter`

#### Scenario: Remove custom input row

- **WHEN** the user removes a custom input row
- **THEN** the system SHALL remove that `inputParameter` from the element if it is not a catalog-declared input key

### Requirement: Custom inputs exclude catalog keys when component-bound

When the Call Activity has a component binding with declared `inputParameters`, custom input rows SHALL NOT duplicate those catalog keys; catalog inputs SHALL continue to appear only in component-driven「输入」groups.

#### Scenario: Filter catalog keys

- **WHEN** the Call Activity has a non-empty `componentId` and the component declares an input with key `foo`
- **THEN**「自定义输入」SHALL NOT list or persist a row named `foo` as a custom input
- **AND** `foo` SHALL remain editable only through the catalog input UI if present

#### Scenario: No component binding

- **WHEN** the Call Activity has no `componentId` (process-pick path)
- **THEN** all `inputParameter` entries on the element SHALL be managed through「自定义输入」
- **AND** the system SHALL NOT show catalog input groups from `as-component`

### Requirement: Call Activity defaults propagate all variables

When a Call Activity is created from the palette (without going through the component library initializer that already sets bindings), the system SHALL ensure Camunda propagate-all semantics by creating `camunda:In` and `camunda:Out` with `variables="all"` if not already present.

#### Scenario: New palette Call Activity

- **WHEN** the user drops a Call Activity from the「子流程」palette onto the canvas
- **THEN** the element SHALL have propagate all variables enabled (both In and Out with `variables="all"`)
- **AND** this SHALL occur without requiring the user to toggle a property

#### Scenario: Component library Call Activity unchanged

- **WHEN** a Call Activity is created from the component library with `componentId`
- **THEN** existing `setComponentId` behavior (including propagate all if already invoked there) SHALL remain valid
