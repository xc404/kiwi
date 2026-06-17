## 1. Path resolution (`SlurmService` / builder)

- [x] 1.1 Implement work-directory root normalization helper (handle missing dir / `toRealPath` fallback per design).
- [x] 1.2 Harden `resolvePathUnderShellDir` so resolved paths cannot escape the work directory; reject or fail-fast on traversal/out-of-root absolute paths.
- [x] 1.3 Ensure `SlurmSbatchConfigBuilder` output/error paths only pass through the hardened resolver.

## 2. Failure-time reads (`SlurmJobCompleteProcessor`)

- [x] 2.1 Before `readErrorFileContent`, validate `errorFilePath` resolves under the same work directory policy as submission; skip read when invalid.
- [x] 2.2 Align error handling with spec (no cross-boundary reads); log at appropriate level when skipping.

## 3. Sbatch generation (`SbatchConfig` / `SlurmService#createSbatchFile`)

- [x] 3.1 Sanitize or reject `#SBATCH` field values and command-related inputs so `\n`/`\r` cannot inject extra script lines (policy per design).
- [x] 3.2 Keep behavior documented if any field is rejected vs stripped.

## 4. Verification

- [x] 4.1 Unit tests: traversal, absolute outside root, valid relative paths, newline in variable.
- [x] 4.2 Run module tests / compile `kiwi-bpmn-component`.

## 5. Ops / docs

- [x] 5.1 Note BREAKING for stderr/stdout outside work directory in change README or release notes if repo expects it (optional single paragraph in proposal already).
