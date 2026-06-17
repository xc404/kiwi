## ADDED Requirements

### Requirement: Slurm log paths stay under work directory

The system SHALL resolve Slurm stdout/stderr paths used in sbatch (`--output`, `--error`) so that the effective path, after normalization, lies under the configured Slurm work directory. Relative segments such as `..` SHALL NOT escape that directory.

#### Scenario: Relative path stays inside work directory

- **WHEN** a process variable provides a relative `slurm_output_file` or `slurm_error_file` without directory traversal
- **THEN** the generated sbatch contains `--output`/`--error` paths that resolve under the configured work directory

#### Scenario: Traversal is rejected or neutralized

- **WHEN** a process variable provides a relative path containing `..` or equivalent that would escape the work directory
- **THEN** the system SHALL reject the submission or normalize to a path still under the work directory without escaping (exact policy per implementation; rejection is acceptable)

### Requirement: Failure-time stderr read is restricted

The system SHALL read stderr content for failure handling only from paths that resolve under the same configured Slurm work directory as used for submission. Attempts to read outside that boundary SHALL NOT return file contents to failure resolvers.

#### Scenario: In-bounds error file is read

- **WHEN** the persisted error file path resolves under the work directory
- **THEN** the failure resolver MAY read that file when building the exception message

#### Scenario: Out-of-bounds error path is not read

- **WHEN** the error file path is an absolute path outside the work directory or escapes via traversal
- **THEN** the system SHALL not read that path for failure details (and MAY treat as missing content)

### Requirement: Sbatch script resists newline injection from variables

The system SHALL ensure that values written into `#SBATCH` lines and the user command section cannot introduce additional logical lines via embedded newline or carriage return characters from process variables (minimum: strip, reject, or equivalent).

#### Scenario: Variable with embedded newline does not add extra script lines

- **WHEN** a process variable used in `#SBATCH` or command contains `\n` or `\r`
- **THEN** the generated `.sbatch` file SHALL not gain unintended extra lines that execute as separate commands or directives beyond the chosen mitigation strategy
