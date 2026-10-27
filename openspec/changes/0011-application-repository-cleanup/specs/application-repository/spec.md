## ADDED Requirements

### Requirement: AR-001 Readiness matches the complete minimal batch
The repository SHALL require fresh T0011-001 readiness for both cleanup tickets
before any implementation or source deletion.

#### Scenario: The approved smaller scope is evaluated
- **WHEN** readiness is refreshed for T0011-001 and T0011-002
- **THEN** target/base, all five backup refs, known local changes, exact deletion
  paths, retained product identities and pinned OpenSpec/analyzer tools are checked
- **AND** a blocked or unchecked required component stops deletion
- **AND** the previous backend/deployment prerequisites are recorded as superseded
  by the explicitly changed deliverable rather than falsely reported healthy

### Requirement: AR-002 The product specification is preserved
The scaffold SHALL preserve all 85 original product requirement IDs and 194
scenarios in eight canonical OpenSpec capability specifications.

#### Scenario: Product specifications are promoted
- **WHEN** the old application change wrappers and task registry are removed
- **THEN** product capability bodies stay identical apart from R002's obsolete
  execution/tasks.json clause and canonical OpenSpec section headings
- **AND** product-baseline, approved data, AsyncAPI, visual and qualification
  materials remain unchanged and available
- **AND** technical requirements are changed explicitly for a later language
  experiment rather than silently removed or treated as delivered functionality

### Requirement: AR-003 Keep only the lightweight scaffold
The working tree SHALL contain product specification material, OpenSpec,
standalone static-analysis tools/configuration/fixtures and concise project rules.

#### Scenario: Implementation and runtime are removed
- **WHEN** the reviewed deletion manifest is applied
- **THEN** Java/Spring and React implementations, application build manifests,
  Docker/deployment assets, Sonar, Playwright, runtime gates, agent execution,
  model routing/accounting, role skills and obsolete execution packets are absent
- **AND** no dummy integration, provider hook or replacement execution engine is added

#### Scenario: Unowned data and history are preserved
- **WHEN** this chat's prepared services and generated files are cleaned up
- **THEN** only owned services are stopped, no application volume or sibling
  resource is deleted, symlinks are not followed, and Git/IDE state is retained

### Requirement: AR-004 Real lightweight checks remain usable
The scaffold SHALL provide pinned OpenSpec, Checkstyle, PMD and ESLint commands
without requiring an application, Docker daemon, database, model or Sonar server.

#### Scenario: Static analysis is invoked directly
- **WHEN** setup and the java or lint command is given an explicit source path
- **THEN** declared checksummed analyzers and existing rules process the source,
  rule/tool errors return a nonzero exit, and an empty source set cannot pass
- **AND** no task packet, approval file, provider key, handoff or runtime service is needed

#### Scenario: Existing analyzer regression fixtures are checked
- **WHEN** the fixture command runs against the retained Java and React fixtures
- **THEN** positive fixtures pass, negative fixtures fail for the expected rules,
  every named PMD case is observed, and tool failures cannot count as expected violations

### Requirement: AR-005 Project guidance is truthful and minimal
The scaffold SHALL document its exact setup, retained specification and check
commands without claiming a running application or completed business acceptance.

#### Scenario: The scaffold is verified
- **WHEN** cleanup verification completes
- **THEN** strict OpenSpec and selected real analyzer fixtures pass, preserved
  content comparisons succeed, and documented paths/commands resolve
- **AND** the report states that application implementation, deployment, Sonar
  and Warden integration are deferred and no commit or push was made
