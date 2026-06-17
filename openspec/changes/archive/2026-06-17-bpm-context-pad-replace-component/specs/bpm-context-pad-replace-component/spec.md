## ADDED Requirements

### Requirement: ServiceTask context pad exposes replace component action

The BPM designer context pad SHALL show a **替换组件** action for `bpmn:ServiceTask` elements that are already bound to a business component (`componentId` present). The action SHALL open the same style of popup menu as **追加业务组件** (component library groups and recent usages). The action SHALL NOT appear for elements without a bound component, for `bpmn:CallActivity`, or for non–ServiceTask flow nodes.

#### Scenario: Bound ServiceTask shows replace entry

- **WHEN** the user opens the context pad on a ServiceTask with a configured `componentId`
- **THEN** a replace-component entry is visible alongside other model actions

#### Scenario: Unbound or non-ServiceTask hides replace entry

- **WHEN** the user opens the context pad on a ServiceTask without `componentId`, or on a CallActivity / gateway / end event
- **THEN** the replace-component entry is not shown

### Requirement: Replace updates component binding in place

When the user selects a target component from the replace menu, the designer SHALL update the existing diagram element in place: the same shape id, position, and sequence flows remain. The element SHALL be bound to the new component id and delegate (or equivalent) per existing `ComponentService.initElement` conventions.

#### Scenario: Successful replace keeps topology

- **WHEN** the user chooses component B to replace component A on a connected ServiceTask
- **THEN** the node remains at the same location with the same incoming and outgoing flows, and `componentId` reflects B

### Requirement: Custom outputs are preserved on replace

User-defined output parameters that are **not** part of the old component's catalog `outputParameters` SHALL remain on the element after replace with their configured values unchanged.

#### Scenario: Custom output survives replace

- **WHEN** the element has a custom output `extraFlag` not listed in component A's catalog outputs, and the user replaces A with component B
- **THEN** `extraFlag` and its value remain in the model after replace

### Requirement: Overlapping inputs retain configured values

For each input parameter key defined on the **new** component, if the element already had a configured value for that key before replace, that value SHALL be kept. Input keys that exist only on the old component and not on the new component SHALL be removed from the element.

#### Scenario: Shared input key keeps value

- **WHEN** both component A and B define input `datasetId`, the element has `datasetId = "ds-1"`, and the user replaces A with B
- **THEN** after replace `datasetId` remains `"ds-1"`

#### Scenario: Old-only input is removed

- **WHEN** component A defines input `legacyMode` configured on the element, component B does not define `legacyMode`, and the user replaces A with B
- **THEN** `legacyMode` is no longer present on the element

### Requirement: New component inputs and catalog outputs initialize from defaults

Input keys newly introduced by the target component that were not configured on the element SHALL receive the target component's `defaultValue` (or empty string if none). Catalog output parameters from the **old** component that are not in the **new** component's catalog SHALL be removed; catalog outputs defined only on the new component SHALL appear per default component metadata (no stale old-catalog output rows).

#### Scenario: New input gets default

- **WHEN** component B defines input `retryCount` with default `3`, the element had no `retryCount`, and the user replaces A with B
- **THEN** `retryCount` is set to `3` on the element

#### Scenario: Old catalog output removed

- **WHEN** component A catalog includes output `resultPath`, component B does not, and the user replaces A with B
- **THEN** the catalog output `resultPath` is removed from the element (custom outputs with the same name that are user-defined remain per custom-output rule)

### Requirement: Replace is undoable

The replace operation SHALL be executed as one or more bpmn-js modeling commands so the user can undo and redo via the editor command stack.

#### Scenario: Undo restores prior component

- **WHEN** the user replaces a component and then triggers undo
- **THEN** the element returns to the previous component binding and prior parameter snapshot
