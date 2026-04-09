# skill-cli-java.md

## ADDED Requirements

### Requirement: Java-native shared spec-driven CLI

- MUST provide a Java CLI entrypoint for repositories that use a `.spec-driven/` workflow directory
- MUST replace the single shared `spec-driven.ts` workflow script used across multiple skills, rather than introducing separate per-skill CLIs
- MUST support running the workflow without requiring Node.js or TypeScript tooling to be installed
- MUST print a usage summary when invoked with no command or with an unsupported command
- MUST exit with a non-zero status for missing required arguments or unsupported commands

#### Scenario: Unsupported invocation returns usage

- GIVEN a developer invokes the CLI without a valid command
- WHEN argument parsing completes
- THEN the CLI returns a non-zero exit status
- AND the CLI prints usage guidance that lists the supported command surface

### Requirement: Spec-driven command parity

- MUST support these commands: `propose`, `modify`, `apply`, `verify`, `verify-roadmap`, `roadmap-status`, `archive`, `cancel`, `init`, `run-maintenance`, `migrate`, and `list`
- Commands that require a change name MUST reject a missing `<change-name>` argument with usage guidance and a non-zero exit status
- `propose <change-name>` MUST create `.spec-driven/changes/<change-name>/` containing `proposal.md`, `design.md`, `tasks.md`, `questions.md`, and a `specs/` directory
- `init` MUST create the baseline `.spec-driven/` scaffold including `changes/`, `specs/`, `roadmap/`, `config.yaml`, `specs/INDEX.md`, `specs/README.md`, and `roadmap/INDEX.md`
- `list` MUST report active changes separately from archived changes using the repository's `.spec-driven/changes/` layout

#### Scenario: Propose scaffolds a new change

- GIVEN a repository with an initialized `.spec-driven/` directory
- WHEN the developer runs `propose skill-cli-java`
- THEN `.spec-driven/changes/skill-cli-java/` is created
- AND the directory contains `proposal.md`, `design.md`, `tasks.md`, `questions.md`, and `specs/`

#### Scenario: List distinguishes active and archived changes

- GIVEN a repository with at least one active change and one archived change
- WHEN the developer runs `list`
- THEN the output includes an `Active:` section
- AND the output includes an `Archived:` section
- AND active changes are not reported as archived

### Requirement: Roadmap reporting compatibility

- `verify-roadmap` MUST emit a machine-readable JSON report describing roadmap validity, warnings, errors, allowed declared statuses, and milestone summaries
- `roadmap-status` MUST emit a machine-readable JSON report containing each milestone's declared status, derived status, and planned-change status summaries
- Invalid roadmap structure or unsupported declared statuses MUST cause the command to exit non-zero

#### Scenario: Roadmap status is machine-readable

- GIVEN a repository with a valid roadmap
- WHEN the developer runs `roadmap-status`
- THEN the command exits successfully
- AND stdout contains valid JSON
- AND the JSON includes milestone-level declared and derived status information

### Requirement: Change validation compatibility

- `verify <change-name>` MUST validate the required proposal artifacts for the named change
- `verify <change-name>` MUST fail when required markdown artifacts or expected delta specs are missing or malformed
- `verify <change-name>` MUST exit successfully when the change artifacts satisfy the repository's spec-driven format rules

#### Scenario: Verify rejects incomplete proposal artifacts

- GIVEN a scaffolded change with missing or malformed required proposal files
- WHEN the developer runs `verify <change-name>`
- THEN the command exits with a non-zero status
- AND the output identifies the missing or invalid artifact
