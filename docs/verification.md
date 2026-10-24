# Preparation verification

## Offline-qualified model routing and budget extension

`0009-agent-model-routing` was initially authored without execution; the follow-up request explicitly authorized offline wrapper tests, fixes and PR publication on 2026-10-24. T0009-001 readiness covers T0009-006, T0009-008 and T0009-009: Python 3.14.8, Node 24.21.0 and npm 11.19.0 match pins; no application service, credential or live model is required for this batch. No coding agent, credential helper or model/API probe was launched. See [model-routing documentation](execution/agent-models.md) for configuration and limitations.

Current offline verification:

| Check | Result |
|---|---|
| Model selection, accounting and budgets | 39 tests passed with fake inference processes and denied network connections |
| Existing coordinator regression | 29 tests passed; temporary Git lifecycle and controlled child-process fixtures, no real agents |
| Task graph and traceability | 48 tasks, 85 requirements, 36 acceptance scenarios; application DAG unchanged |
| Strict OpenSpec validation | 9 changes passed, 0 failures |
| Budget admission | Zero defaults block before catalog/probe calls; 8 concurrent reservation contenders cannot oversubscribe 2 available allowances |
| Budget stopping | Exact threshold, late overrun, unknown usage, orphaned reservations, policy edits, lowered limits on restart, silent sibling cancellation and merge/DONE prevention covered |

Fixes include closing read-only SQLite connections, rejecting malformed policy field types with controlled blockers and rejecting impossible reasoning-token subsets. The new suites explicitly replace subprocess creation and deny socket connections; their only model event streams are local fixtures. Historical analyzer/browser checks below were not rerun because application/static/runtime gate code did not change. Live model IDs, provider catalog schema, effort/image support, actual account access and billing remain **NOT_VERIFIED** under pending T0009-007. Offline success does not assert real-provider compatibility or application acceptance.

Budgets measure observed input plus output tokens, not money. In-flight turns can exceed an allowance before telemetry arrives; the full observed overrun is retained. No budget limit was activated and no private accounting data or secret was published. GitHub CI for the new PR runs the same offline package checks; PR checks are the publication evidence.

## Initial preparation baseline

Status: **execution package prepared and locally verified**. Application implementation, application acceptance, real-model qualification and application Sonar analysis are **NOT_RUN**. All 48 implementation/preparation tickets remain PLANNED; creating this repository did not execute them.

| Check | Observed result |
|---|---|
| OpenSpec strict validation | 8 changes passed; 0 failures |
| Task graph / scope / traceability | 48 tasks; 84 imported requirements plus AI001; all 36 acceptance scenarios mapped; no dependency cycle |
| Coordinator and boundary tests | 29 passed, including actual temporary Git worktree/commit/integration lifecycle with stubbed agents, concurrent resource claims, stale/missing evidence, path/scope protection, process interruption and actual-test manifest validation |
| Real static analyzers | 13 tests passed using pinned Checkstyle 14.3.0, PMD 7.28.0 and ESLint; positive and intentional negative fixtures, tampered/missing reports and suppression cases |
| Runtime/browser tooling fixtures | 20 tests passed with real Chromium, including dependency loss, failed browser assertion, stale evidence and prevention of fixture-to-application completion |
| Repository skills | 7 SKILL.md files passed the skill-creator validator |
| AsyncAPI | YAML syntax and both channels/three messages checked; semantic publisher/consumer tests belong to T0005-004 |
| Toolchain read-only check | Node 24.21.0, npm 11.19.0, Python 3.14.8 and selected JDK 23.0.2 observed |
| Codex launcher | Installed CLI 0.163.0-alpha.11.1; required exec flags inspected; authentication present; no implementation agents launched |
| Original sources | 716 imported source files rehashed; zero changes from import hashes |
| Publication scan | No private-key, GitHub-token or AWS-key patterns found in publishable text sources; caches/private runtime data excluded |

These 62 focused tests verify the preparation tools, not Stone Shelter's 36 product acceptance scenarios. Browser fixture reviews are synthetic test attestations, never claimed as human or application approval. The coordinator end-to-end test uses stubbed agent outputs only inside a disposable Git repository; no real coding swarm was billed or launched.

## Required runtime qualification remains explicit work

- T0001-002 qualifies inherited application coordinates, images and coverage support; none is silently upgraded
- T0001-003/004 repair inherited application static violations; the seed has not been declared style-clean
- T0001-005 implements/registers the Java-side Testcontainers readiness handshake and required runtime capabilities
- T0001-006 implements approved Spring Security; it is a task, not pre-existing protection
- T0005-002 and T0006-005 migrate the remaining custom embedding/vector/RAG plumbing to Spring AI
- T0008-005 owns actual full application regression and Sonar release analysis

The new dedicated Sonar configuration is prepared on port 19100. No new application stack or Sonar server was started as part of documentation/tooling preparation; execution starts owned resources only when its selected batch requires them. The older lab's running services and project files remain unchanged.
