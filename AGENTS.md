# AGENTS.md — Kiwi Codebase Guide

## Architecture Overview

Maven multi-module monorepo: Java 25 + Spring Boot 4.0 backend (Operaton BPM engine embedded) + Angular 21 frontend.

```
kiwi-common/              # Shared entities, Mongo/MyBatis base classes
kiwi-bpmn/
  kiwi-bpmn-core/         # @ComponentDescription / @ComponentParameter annotations, variable mapping, job retry
  kiwi-bpmn-component/    # Official delegates as plugin JARs (Shell, HTTP, MongoDB, JDBC, …); Slurm stays classpath via kiwi-bpmn-component-slurm
  kiwi-bpmn-external-task/ # External Task abstraction
kiwi-admin/
  backend/                # com.kiwi.framework.* (infra) + com.kiwi.project.{system,bpm,ai,tools,monitor,notification}
  frontend/               # src/app/{core,layout,pages,shared,config,utils}; BPMN editor at pages/bpm
openspec/                 # Spec-driven change proposals (see OpenSpec Workflow below)
```

## Developer Workflows

**Backend (local dev):**
```bash
# From repo root — compiles all upstream modules
mvn -pl kiwi-admin/backend -am compile -DskipTests
# Run Application with profiles: local,dev  (port 8000, H2 engine DB, MyBatis stdout)
# Working directory MUST be kiwi-admin/backend — official plugin JARs are committed under plugins/
```
Official BPM component plugin JARs live in `kiwi-admin/backend/plugins/` (committed). They are **slim shaded JARs** (~30–50 MB total): platform libs (`kiwi-bpmn-core`, Spring, Operaton) are `provided` at build time and excluded from shade. **Only when changing `kiwi-bpmn-component*` modules**, rebuild and commit:
```bash
mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests
```
Copy `application.example.yml` → `application-local.yml`; fill MongoDB URI and `kiwi.mongodb.init.admin-password`.

**Frontend:**
```bash
cd kiwi-admin/frontend && npm install && npm start   # http://localhost:4201
```
`environment.ts` `api.baseUrl` must match the running backend port (`8000` for `dev` profile, `8080` otherwise).

**Full stack (Docker):**
```bash
docker compose -f docker/docker-compose.yml up -d --build   # http://localhost:8080/kiwi-admin/
```

**Package backend:** `mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests`

**Frontend scripts:** `npm run build` | `npm run lint:fix` | `npm run prettier` | `npm test`

## Java Conventions

- **Naming**: enums and `static final` constants use **PascalCase** (`Pending`, `DefaultTimeout`), not `SCREAMING_SNAKE_CASE`.
- **No static methods** in service/component/domain classes. Use instance methods + Spring injection. Pure utils go in dedicated `*Utils`/`*Helper` classes.
- **OpenAPI annotations required** on every public REST endpoint — `@Operation(operationId, summary)` is mandatory. `operationId` format: `{controllerPrefix}_{action}` (e.g., `bpmComp_page`, `menu_get`). Missing `@Operation` means the endpoint is invisible to MCP tools (`KiwiOpenApiSyncMcpToolsConfiguration` scans `@Operation` to register MCP tools).
- Controllers extend `BaseCtl` (provides `getCurrentUser()`), are annotated `@SaCheckLogin` at class level, and use `@Tag(name = "…")`.

## BPM Component Pattern

New process delegates implement `JavaDelegate` and are annotated with `@ComponentDescription` + `@ComponentParameter`:
```java
@ComponentDescription(name = "命令行",
    inputs  = { @ComponentParameter(key = "command", description = "…") },
    outputs = { @ComponentParameter(key = "result",  description = "…") })
@Component("shell")
public class ShellActivityBehavior implements JavaDelegate { … }
```
- **`@ComponentParameter` key**: no dots — use `_` (e.g., `mdoc_id` not `mdoc.id`).
- **`htmlType`**: omit unless overriding default (use `"CheckBox"` for booleans, `"spel-expression"` for SpEL, etc.).
- Read variables via `ExecutionUtils.getStringInputVariable(execution, "key")` (returns `Optional`), not path-style APIs.
- Default `required=true` input maps to `${key}` in the BPMN property panel by convention.

## API / Frontend Integration

- **Unified response**: `ResponseAdvice` wraps all responses in `R<T>`. Collections become `R<CollectionResult<T>>` — front end reads `.data.content`, not a bare array.
- **Errors**: `ExceptionHandler` (`@ControllerAdvice`) returns `R.fail(…)`. Front end handles errors from `code`/`msg` fields in the HTTP interceptor, not raw exceptions.
- **Frontend async**: Use RxJS `Observable` throughout (`HttpClient`, `pipe`/`switchMap`). Avoid mixing `async/await` with Observables in business logic; use `lastValueFrom` only at boundaries.

## MongoDB Migrations

Two mechanisms (both run on startup when `kiwi.mongodb.migration.enabled=true`):

| Type | Dir | Naming | Runs |
|------|-----|--------|------|
| Mongock `@ChangeUnit` | `com.kiwi.framework.mongo.migration.primary` | class-level `@ChangeUnit(id, order)` | Once |
| JSON reference data | `mongo/migration/versioned/` | `V{date}_{seq}__{EntityName}.json` | Once |
| JSON repeatable | `mongo/migration/repeatable/` | `R__{EntityName}.json` | On checksum change |

JSON files contain arrays of objects with non-null `id`; entity class resolved from `EntitySimpleName` against `com.kiwi.project.system.entity`.

## OpenSpec Workflow

For non-trivial features, create a spec **before** writing code:
```bash
openspec new change "<kebab-name>"                              # scaffold
openspec instructions apply --change "<name>" --json           # get context files + tasks
# implement; tick tasks.md: - [x]
openspec archive --change "<name>"                             # move to openspec/changes/archive/
```
Small obvious fixes can skip spec creation.

**Plan files:** Any implementation plan the agent produces must be **written to disk in the same turn** at `.cursor/plans/<feature>_<hash>.plan.md` (see `.cursor/rules/plans-in-workspace.mdc`). Do not leave plans only in chat.

## Key Config & Integration Points

- **Operaton**: embedded engine, API prefix `operaton.bpm.*`. `/engine-rest` HTTP is **off** by default (`kiwi.bpm.engine-rest-http-enabled=false`); enable only for debugging.
- **Auth**: Sa-Token with MongoDB session store by default (`kiwi.sa-token.storage=mongodb`); switch to Redis with `redis` profile.
- **AI**: Spring AI + DeepSeek (`DEEPSEEK_API_KEY`). MCP server enabled by default; `KIWI_AI_ENABLED=false` disables the AI subsystem. MCP tools are auto-registered from `@Operation`-annotated controllers.
- **Profiles**: always run with `local,dev` locally. `dev` activates H2 engine DB and port 8000; `local` loads `application-local.yml` (gitignored).
- **CORS**: `APP_CORS_ALLOWED_ORIGINS` (default includes `http://localhost:4201`).

