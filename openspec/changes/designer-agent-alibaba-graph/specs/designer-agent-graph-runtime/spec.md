## ADDED Requirements

### Requirement: Graph orchestrates designer agent runs
The Designer Agent SHALL execute each run as a compiled Alibaba Graph whose nodes cover ingest, generate, apply, validate, repair, explain, and persist-preview intent. Stage transitions SHALL be expressed as graph edges, not ad-hoc continuation lambdas.

#### Scenario: Complex edit waits at plan gate
- **WHEN** a user starts a run whose EditPlan is not skipped
- **THEN** the graph interrupts at the plan review point and the client receives `plan_ready` and `await_human` SSE events with stage `await_plan`

#### Scenario: Simple edit skips plan gate
- **WHEN** a run generates an EditPlan that the skip evaluator allows
- **THEN** the graph proceeds to apply and validate without emitting `plan_ready`

#### Scenario: Read-only explanation completes
- **WHEN** the user scenario is classified as read-only explanation
- **THEN** the graph reaches done and emits a `done` event without applying an EditPlan

### Requirement: Human interrupt and resume
The system SHALL pause the graph at plan, preview, ask, and install gates using Graph interrupt/resume. Confirm-plan, confirm-preview, and answer SHALL resume via Graph `updateState` (or equivalent) rather than in-memory continuation runnables.

#### Scenario: Confirm plan applies patch
- **WHEN** the run is interrupted at `await_plan` and the user confirms the plan
- **THEN** the graph resumes, applies the EditPlan, validates, and emits `preview_ready` (or ask/install) without requiring a stored `Runnable`

#### Scenario: Reject plan regenerates
- **WHEN** the user rejects the plan at `await_plan`
- **THEN** the graph returns to generate and produces a new plan turn

#### Scenario: Answer resumes after ask
- **WHEN** the run is interrupted at `await_ask` and the user submits an answer
- **THEN** the graph resumes using the updated scenario text

### Requirement: Checkpoint is the run source of truth
The system SHALL persist graph checkpoints with `threadId` equal to `runId`. Storage SHALL be selectable as `memory` or `mongodb` via `kiwi.bpm.designer-agent.checkpoint`. Status and SSE resume SHALL reconstruct run state from checkpoint plus an optional in-process SSE sink map.

#### Scenario: Status after interrupt
- **WHEN** a run is interrupted at a human gate
- **THEN** `GET /bpm/designer-agent/runs/{runId}` returns the current stage and plan/preview fields from checkpointed state

#### Scenario: Mongo restart continuity
- **WHEN** checkpoint mode is mongodb and the process restarts while a run is awaiting plan
- **THEN** confirm-plan and stream resume still operate against the same `runId`

### Requirement: HTTP and SSE compatibility
Existing Designer Agent HTTP paths and `AgentStreamEvent` type names SHALL remain unchanged. The frontend MUST NOT be required to change for this capability.

#### Scenario: Start stream still emits run_started first
- **WHEN** a client posts `/bpm/designer-agent/runs/stream`
- **THEN** the server sends `run_started` before asynchronous graph execution can complete the emitter with `error` or `done`
