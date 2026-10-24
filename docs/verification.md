# Preparation verification

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
