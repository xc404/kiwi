# Kiwi

[中文](README.zh-CN.md)

[![License: MIT](https://img.shields.io/github/license/xc404/kiwi)](LICENSE)
[![CI](https://github.com/xc404/kiwi/actions/workflows/ci.yml/badge.svg)](https://github.com/xc404/kiwi/actions/workflows/ci.yml)

**Kiwi** is a BPMN-based workflow orchestration and management platform built on [Operaton](https://www.operaton.org/) (the community continuation of Camunda 7): visual BPMN designer, pluggable process delegates, admin console, and AI assistant. This repository is a Maven multi-module monorepo with a separate admin frontend and backend.

> **Camunda 7 baseline**: Git tag and branch **`camunda`** preserve the pre-migration Camunda 7.24 + Spring Boot 3.5 state for rollback and comparison (`git checkout camunda`).

**Live demo:** [https://www.kiwi-admin.cn](https://www.kiwi-admin.cn)

![AI-assisted workflow design demo](docs/screenshots/kiwi-ai-process-design.gif)

## Features

- **BPMN design**: Angular + BPMN.js; property panel synced with backend component metadata
- **Operaton engine**: process definitions/instances, `/engine-rest`, External Tasks, async jobs with configurable retries
- **Pluggable delegates**: Shell, HTTP, file I/O, variable assignment, MongoDB, Slurm, and more
- **Admin console**: users, roles, menus, departments, dictionaries, Sa-Token, Personal Access Tokens
- **Low-code tools**: code generation, JDBC and schema browser
- **AI assistant**: Spring AI (DashScope), built-in MCP; page navigation and BPMN designer orchestration
- **Data migrations**: Mongock + JSON reference data (Flyway-like convention)

## Tech stack

| Layer | Stack |
|-------|-------|
| Backend | Java **25**, Spring Boot **4.0**, Operaton **2.1**, MongoDB, MyBatis, Sa-Token |
| Frontend | Angular **21**, ng-zorro-antd, BPMN.js, ECharts, @antv/x6 |
| Build | Maven (multi-module), npm |
| Specs | [OpenSpec](openspec/) (`spec-driven`) |

## Repository layout

```
kiwi/
├── kiwi-common/                 # Shared entities, Mongo/MyBatis utilities
├── kiwi-bpmn/
│   ├── kiwi-bpmn-core/          # Component annotations, variable mapping, job retry
│   ├── kiwi-bpmn-component/     # Built-in delegates (Shell, HTTP, Slurm, …)
│   └── kiwi-bpmn-external-task/
├── kiwi-admin/
│   ├── backend/                 # Spring Boot main app (see sub README)
│   └── frontend/                # Angular admin UI (see sub README)
├── docs/                        # Screenshots, Maven snippets, etc.
├── openspec/                    # Change specs and tasks
└── LICENSE
```

### Modules and key paths

| Path | Role |
|------|------|
| [kiwi-common/](kiwi-common/) | Cross-module entities and data-access base classes |
| [kiwi-bpmn/kiwi-bpmn-core/](kiwi-bpmn/kiwi-bpmn-core/) | `@ComponentDescription` / `@ComponentParameter`, variable mapping, JUEL tolerance, job retry |
| [kiwi-bpmn/kiwi-bpmn-component/](kiwi-bpmn/kiwi-bpmn-component/) | Built-in process components (Shell, HTTP, MongoDB, Slurm, etc.); Slurm ops: [slurm-workdir-cleanup.md](kiwi-bpmn/kiwi-bpmn-component/docs/slurm-workdir-cleanup.md) |
| [kiwi-bpmn/kiwi-bpmn-external-task/](kiwi-bpmn/kiwi-bpmn-external-task/) | External Task abstraction and retry |
| [kiwi-admin/backend/](kiwi-admin/backend/) | `com.kiwi.framework` (boot, security, Mongo migrations, exception handling) + `com.kiwi.project.{system,bpm,ai,tools,monitor,notification}` |
| [kiwi-admin/frontend/](kiwi-admin/frontend/) | `src/app/{core,layout,pages,shared,config,utils}`; BPMN editor under `pages/bpm` |
| [docs/screenshots/](docs/screenshots/) | Admin UI screenshots and demo GIFs |
| [docs/maven/settings-dev-snippet.xml](docs/maven/settings-dev-snippet.xml) | Maven settings snippet for SNAPSHOT dev |

## Screenshots

![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110413.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110437.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110447.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110512.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110602.png)

More screenshots: [docs/screenshots/](docs/screenshots/).

## Quick start

### Docker (full stack — recommended)

Requires [Docker](https://docs.docker.com/get-docker/) and Docker Compose. Starts **MongoDB**, **backend**, and **frontend** (Nginx serving the Angular app and proxying API to the backend). Compose and Dockerfiles live under [`docker/`](docker/).

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

Or from the `docker/` directory: `docker compose up -d --build`

- **Admin UI:** http://localhost:8080/kiwi-admin/
- **Default admin:** `admin` / `kiwi-demo` (override with `KIWI_INIT_ADMIN_PASSWORD`)
- **Notes:** `docker` profile uses embedded H2 for the Operaton engine DB; MongoDB is provided by compose; AI is disabled by default; first build may take several minutes (Maven + npm)

Services: `mongodb` · `backend` (internal `:8080`) · `frontend` (Nginx, host `:8080` → container `:80`).

### Local development (summary)

**Prerequisites:** JDK 25, Maven 3.x, MongoDB; MySQL for the Operaton engine DB unless using the `dev` profile (embedded H2); Node.js compatible with Angular 21. Operaton artifacts resolve from Maven Central (see backend README).

1. **Backend:** Copy [application.example.yml](kiwi-admin/backend/src/main/resources/application.example.yml) → `application-local.yml`, fill in connections and `kiwi.mongodb.init.admin-password`; from repo root run `mvn -pl kiwi-admin/backend -am compile -DskipTests`; run `com.kiwi.framework.springboot.Application` in your IDE with profiles `local,dev` (port **8000**). See [kiwi-admin/backend/README.md](kiwi-admin/backend/README.md).

2. **Frontend:** `cd kiwi-admin/frontend && npm install && npm start` → **http://localhost:4201**; match `api.baseUrl` in `environment.ts` to your backend port. See [kiwi-admin/frontend/README.md](kiwi-admin/frontend/README.md).

## Documentation

| Doc | Contents |
|-----|----------|
| [docs/bpm-component.zh-CN.md](docs/bpm-component.zh-CN.md) | BPM component architecture, add/configure/use (Chinese) |
| [kiwi-admin/backend/README.md](kiwi-admin/backend/README.md) | Backend architecture, config, local run, Mongo migrations |
| [kiwi-admin/frontend/README.md](kiwi-admin/frontend/README.md) | Frontend architecture, env, scripts, AI integration |
| [kiwi-admin/backend/deploy/README.md](kiwi-admin/backend/deploy/README.md) | Backend remote deploy |
| [kiwi-admin/frontend/deploy/README.md](kiwi-admin/frontend/deploy/README.md) | Frontend remote deploy |

For non-trivial features, use **OpenSpec** (`openspec list`); Java/process-component and frontend async conventions are in `.cursor/rules/`.

## Contributing

Contributions welcome — BPMN components, admin features, docs, and bug fixes.

1. **Local setup:** Follow [Quick start](#quick-start) and [Documentation](#documentation) above.
2. **Specs & tasks:** For non-trivial work, start with [OpenSpec](openspec/) (`openspec new change "<name>"`), then implement from `tasks.md`.
3. **Conventions:** Java delegates, frontend integration, `@ComponentParameter` rules — see `.cursor/rules/`.
4. **Submit:** Fork, branch, open a Pull Request; questions welcome in [Issues](https://github.com/xc404/kiwi/issues).

## License

[MIT License](LICENSE)
