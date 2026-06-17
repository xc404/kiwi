## Why

`SlurmJobCompleteProcessor` currently exposes more than one public completion entry and mixes scene-specific adaptation with terminal reporting logic. This increases cognitive load, makes naming ambiguous, and raises maintenance risk when terminal flow evolves.

## What Changes

- Unify public completion flow to a single external entry in `SlurmJobCompleteProcessor`.
- Rename completion-related methods around `processTerminal` / `processParsedSlurmTerminal` to `complete*` naming that clearly separates external API and internal helpers.
- Keep existing terminal semantics unchanged: idempotency guard, optimistic lock coordination, Camunda complete/handleFailure routing, and retry behavior.
- Update tracker-side call sites and tests to align with the new API naming and visibility.

## Capabilities

### New Capabilities
- `slurm-terminal-completion-entrypoint`: Provide one clear public completion entrypoint for Slurm terminal reporting, with consistent naming for external and internal completion methods.

### Modified Capabilities
- None.

## Impact

- Affected code:
  - `kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmJobCompleteProcessor.java`
  - `kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmJobTracker.java`
  - `kiwi-bpmn/kiwi-bpmn-component/src/test/java/com/kiwi/bpmn/component/slurm/SlurmJobTrackerTest.java`
- API impact: internal Java API rename for Slurm completion entrypoints.
- Runtime behavior impact: expected no functional change, only entrypoint and method naming consolidation.
