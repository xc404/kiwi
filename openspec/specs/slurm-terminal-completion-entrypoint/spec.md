# slurm-terminal-completion-entrypoint Specification

## Purpose
TBD - created by archiving change slurm-complete-entrypoint-unification. Update Purpose after archive.
## Requirements
### Requirement: Unified external completion entrypoint
The Slurm terminal completion processor SHALL expose exactly one public completion entrypoint for external callers. Additional completion steps needed for parsed sacct input or orchestration MUST be internal (private or package-private non-entry helpers) and MUST NOT introduce a second external entrypoint.

#### Scenario: Tracker completes via single public API
- **WHEN** `SlurmJobTracker` reports a parsed sacct terminal result
- **THEN** it invokes the processor's single public `complete` entrypoint
- **AND** no additional public processor entrypoint is required for parsed sacct adaptation

### Requirement: Completion naming clarity
Completion-related methods in the processor SHALL use `complete*` naming. Public external entrypoint methods MUST be named as `complete(...)`, and internal non-entry orchestration methods MUST use differentiated `complete*` helper naming that indicates internal usage.

#### Scenario: Public and internal methods are distinguishable by name
- **WHEN** developers read `SlurmJobCompleteProcessor`
- **THEN** they can identify the external entrypoint by `complete(...)`
- **AND** internal helper methods are recognizable as non-entry methods by `complete*` helper names

### Requirement: Behavior-preserving refactor
The entrypoint and naming refactor MUST preserve existing terminal completion semantics, including idempotency handling, optimistic locking, and Camunda completion/failure routing with retry behavior.

#### Scenario: Terminal result semantics remain unchanged
- **WHEN** a terminal result is processed through the unified `complete(...)` entrypoint
- **THEN** success path still reports Camunda `complete`
- **AND** failure path still reports Camunda `handleFailure` using existing retry planning behavior
- **AND** idempotency and optimistic lock safeguards remain in effect

