## ADDED Requirements

### Requirement: Example module demonstrates third-party component pattern

The repository SHALL include Maven module `kiwi-bpmn-component-example` with a `DemoGreetingActivity` implementing `JavaDelegate`, annotated with `@ComponentDescription` and `@Component("demoGreeting")`.

#### Scenario: Demo component metadata

- **WHEN** the backend starts with `kiwi-bpmn-component-example` on the classpath
- **THEN** Mongo receives component metadata with key `demoGreeting` and group「示例」

### Requirement: Demo component runtime behavior

`DemoGreetingActivity` SHALL read input variable `name` and set output variable `greeting` to `"Hello, " + name`.

#### Scenario: Execute with name Kiwi

- **WHEN** the component runs with `name` = `Kiwi`
- **THEN** `greeting` equals `Hello, Kiwi`

### Requirement: Example module includes developer documentation

The module SHALL ship `README.md` describing Maven dependencies, how to add the module to `kiwi-admin/backend`, and how to verify the component appears in the designer palette.

#### Scenario: Developer follows README

- **WHEN** developer adds the example dependency and restarts the backend
- **THEN**「Demo 问候」appears in the designer component palette under group「示例」
