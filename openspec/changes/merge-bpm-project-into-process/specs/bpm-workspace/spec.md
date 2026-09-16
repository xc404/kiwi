## ADDED Requirements

### Requirement: Project management is the sole BPM design entry

The admin application SHALL expose **项目管理** at `/bpm/project` as the only sidebar entry for designing processes. That page SHALL show the current project and the process list belonging to it. The application SHALL NOT show a separate **流程管理** sidebar item for process definitions.

#### Scenario: Sidebar shows project management only

- **WHEN** a logged-in user opens the 工作流 menu
- **THEN** the application SHALL show 项目管理 linking to `/bpm/project`
- **AND** the application SHALL NOT show 流程管理 as a visible menu item for process definitions

#### Scenario: Project page lists processes for the current project

- **WHEN** the user opens `/bpm/project` with a selected project
- **THEN** the page SHALL list processes whose `projectId` is that project
- **AND** the user SHALL be able to create, open (design), deploy, and clone processes in that context

### Requirement: Legacy process-definition path lands on project management

Requests to `/bpm/process-definition` SHALL navigate to `/bpm/project`. If the request includes `projectId`, that query parameter SHALL be preserved.

#### Scenario: Bookmark with projectId

- **WHEN** the user opens `/bpm/process-definition?projectId={id}`
- **THEN** the application SHALL show project management at `/bpm/project` with that project selected

#### Scenario: Bookmark without projectId

- **WHEN** the user opens `/bpm/process-definition` with no `projectId`
- **THEN** the application SHALL show project management at `/bpm/project`
- **AND** SHALL select the last remembered project if present, otherwise the first available project

### Requirement: Client remembers the last BPM project

The front-end application SHALL persist the identifier of the last selected BPM project in browser storage. Opening project management SHALL restore that selection when the URL has no `projectId`.

#### Scenario: Selecting a project updates memory

- **WHEN** the user selects a project on `/bpm/project`
- **THEN** the application SHALL store that project identifier as the last selection

#### Scenario: Entering project management restores memory

- **WHEN** the user opens `/bpm/project` with no `projectId` query parameter and a last project identifier is stored
- **THEN** the application SHALL select that project as the current project

#### Scenario: No memory and no projects

- **WHEN** the user opens `/bpm/project` and there are no projects
- **THEN** the page SHALL prompt the user to create a project
- **AND** SHALL NOT show a misleading empty process table as the only affordance

### Requirement: Project lifecycle is available on the project page

On `/bpm/project` the user SHALL be able to create a project, rename the current project, manage that project's environment variables, and import or export a template pack for that project.

#### Scenario: Create project

- **WHEN** the user creates a project from the project management page
- **THEN** the system SHALL persist a `BpmProject`
- **AND** the page SHALL switch to the new project

#### Scenario: Rename current project

- **WHEN** the user renames the current project
- **THEN** the system SHALL update that `BpmProject` name
- **AND** the page SHALL show the new name

#### Scenario: Environment variables

- **WHEN** the user opens environment variables for the current project
- **THEN** the application SHALL allow editing `BpmProjectEnvVar` rows for that `projectId`

### Requirement: Delete project refuses when processes exist

Deleting a BPM project SHALL fail if any `BpmProcess` still references that `projectId`. When no processes remain, the system SHALL delete the project and all `BpmProjectEnvVar` rows for that `projectId`.

#### Scenario: Delete blocked by processes

- **WHEN** the user deletes a project that still has one or more processes
- **THEN** the system SHALL NOT delete the project
- **AND** SHALL return an error stating that processes must be removed first

#### Scenario: Delete empty project clears env

- **WHEN** the user deletes a project that has no processes
- **THEN** the system SHALL delete the project
- **AND** SHALL delete all environment variables for that `projectId`

#### Scenario: Client recovers after current project is deleted

- **WHEN** the current project is deleted successfully
- **THEN** the front-end SHALL clear last-project memory if it pointed at that id
- **AND** SHALL select another remaining project or show the empty-project prompt
