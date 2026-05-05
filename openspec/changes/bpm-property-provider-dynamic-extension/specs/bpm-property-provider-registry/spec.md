## ADDED Requirements

### Requirement: Property providers are composed from registered contributors

The BPM designer properties panel SHALL obtain merged property tabs by composing one or more implementations of `PropertyProvider` registered as contributors, rather than relying on a fixed hardcoded list of provider classes inside the composite implementation.

#### Scenario: Multiple contributors merge by tab name

- **WHEN** two contributors both return a tab with the same `name` (including empty/undefined handled per implementation contract)
- **THEN** the composite result SHALL contain one tab with that name whose `groups` array contains the groups from the first contributor's tab followed by the groups from the second contributor's tab (preserving existing merge semantics)

### Requirement: Contributors are registered via Angular dependency injection

The system SHALL allow additional `PropertyProvider` implementations to be attached without modifying the composite property provider source file, by registering contributors through Angular DI (for example a multi-provided injection token for property provider contributors).

#### Scenario: Feature module registers a contributor

- **WHEN** an application or lazy route adds a DI provider that registers a new `PropertyProvider` as a contributor
- **THEN** that contributor SHALL participate in tab composition when the properties panel resolves the composite property provider

### Requirement: Default contributors preserve baseline tabs

The default application configuration SHALL register contributors equivalent to the existing base common properties and component binding properties so that, without extra registration, the properties panel behavior for existing BPMN elements remains available.

#### Scenario: Service task shows input and output tabs

- **WHEN** a user selects a `bpmn:ServiceTask` that uses the existing component binding flow
- **THEN** the merged tabs SHALL still include the baseline "基础信息" (or equivalent) tab from the base contributor and the "输入" / "输出" tabs from the component contributor as before

### Requirement: Contributor merge order is deterministic

The composite SHALL merge contributor outputs in a deterministic order defined by contributor registration order (documented as implementation contract).

#### Scenario: Ordering affects stacking of groups for same-named tabs

- **WHEN** contributor A is registered before contributor B and both contribute to the same tab name
- **THEN** groups from A SHALL appear before groups from B in the merged tab's `groups` array
