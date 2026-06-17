## Context

`SlurmJobCompleteProcessor` currently contains two public entry-like methods for terminal completion flow: one oriented around `SlurmJob` and one oriented around parsed sacct payload (`SlurmResult`). The second entry performs adaptation and then delegates to the first, but the public API naming (`process*`) does not clearly communicate boundary responsibilities. `SlurmJobTracker` and tests call this adapter-style method directly.

Constraint: this change must be behavior-preserving for terminal handling (idempotency, Mongo optimistic lock, Camunda complete/failure reporting, retry semantics).

## Goals / Non-Goals

**Goals:**
- Provide exactly one clear external completion entry in `SlurmJobCompleteProcessor`.
- Rename completion methods to `complete*` naming with explicit internal/external intent.
- Keep runtime behavior unchanged while reducing API ambiguity.
- Update caller and tests to align with the new API and naming.

**Non-Goals:**
- No change to retry algorithm, lock policy, failure message composition, or Camunda payload format.
- No new persistence fields or schema updates in Mongo.
- No business-flow changes in `SlurmJobTracker` polling strategy.

## Decisions

1. Keep `SlurmJobCompleteProcessor` as terminal orchestration owner, but expose a single public method.
   - Rationale: terminal logic is already centralized here; moving ownership to tracker would increase cross-class coupling.
   - Alternative considered: move adapter logic fully into `SlurmJobTracker`; rejected because completion API would become duplicated across callers.

2. Convert current dual public surface into one public `complete(...)` entry and private/internal helper variants.
   - Rationale: callers should only see one stable completion API; adapter logic remains internal.
   - Alternative considered: keep two public methods and only rename; rejected because it does not satisfy entrypoint unification.

3. Use explicit naming conventions:
   - Public: `complete(...)` for external entry.
   - Internal helpers: `completeInternal...` / `completeWith...` style names to indicate non-public orchestration steps.
   - Rationale: improves readability and intent without changing behavior.

4. Update direct callers (`SlurmJobTracker`) and related tests in lockstep.
   - Rationale: preserve compile-time safety and test coverage after API consolidation.

## Risks / Trade-offs

- [Risk] Public method consolidation may accidentally alter guard ordering.  
  → Mitigation: preserve current null/idempotency checks and delegation order exactly; only restructure names/surface.

- [Risk] Caller migration may miss one invocation path.  
  → Mitigation: perform repository-wide reference search for old method signatures and update all occurrences.

- [Risk] Rename-heavy refactor can obscure review focus.  
  → Mitigation: keep commit/task scope narrow and avoid unrelated logic changes.

## Migration Plan

1. Introduce final public `complete(...)` entry in `SlurmJobCompleteProcessor` and make parsed-result adaptation internal/private.
2. Rename helper methods from `process*` to `complete*` semantics while keeping behavior.
3. Replace all call sites with new public `complete(...)` signature.
4. Update unit tests/mocks/verify clauses for new method names/signatures.
5. Validate via compile + lints in module scope.

Rollback strategy: revert this refactor set as one unit; no data migration required.

## Open Questions

- Whether to keep a second package-private adapter method for test readability, or fully internalize it as private helper and adapt at caller side. Recommended default: fully internal helper and one public `complete(...)`.
