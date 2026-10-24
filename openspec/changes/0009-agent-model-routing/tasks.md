# Tooling change tasks

This list is separate from `execution/tasks.json`: these tasks prepare the coordinator and are not application-agent tickets. IDs are preserved; runtime qualification must not be silently treated as completed.

## 1. Environment readiness
- [x] T0009-001 Rechecked for T0009-013 and the requested T0009-008 publication: Python 3.14.8, Node 24.21.0 and npm 11.19.0 match pins; the working scope is clean and PR #1 matches local commit 80c2ec0. READY for offline fixes, harmless process/Git fixtures and publication. Application services/accounts/models NOT_CHECKED and unnecessary for this batch. No agents or model requests authorized; both USD limits remain zero.

## 2. Authorized authoring
- [x] T0009-002 Author explicit presets, role/ticket override resolution and secret-free router settings.
- [x] T0009-003 Author catalog/protocol preflight, stale-evidence checks and explicit runner wiring.
- [x] T0009-004 Author invocation/turn usage and timing receipts with failure preservation and locked aggregation.
- [x] T0009-005 Author OpenRouter examples, handoff documentation and focused offline test cases.

## 3. Deferred verification
- [x] T0009-006 Run the now-authorized focused offline model/budget tests and OpenSpec/package checks, diagnose failures and record observed results in docs/verification.md.
- [ ] T0009-007 With credentials and explicit authorization for billable probes, qualify requested models, catalog schemas, effort levels, image metadata and real tool/schema/usage behavior; record observations without secrets.
- [x] T0009-008 Publish the authorized change as English PR #1 and check offline CI (code commit 84a15ef passed; latest PR checks remain the source for current-head status). No merge performed.
- [x] T0009-009 Implement separate persistent Codex/OpenRouter token budgets, concurrent reservations, feature/call admission, consumption monitoring and fail-closed stopping; qualify with offline fake-process tests.
- [x] T0009-010 Add correlated durable workflow/tool history, attribution, read-only timeline/recovery inspection and verified audit backup/restore; preserve interrupted work without automatic replay.
- [x] T0009-011 Add separate USD budget admission with pinned estimated prices and token reporting; distinguish estimates from invoices/subscription quota and block unknown prices/history migration.
- [x] T0009-012 Qualify crash/restart, history integrity, attribution, backup/restore, USD arithmetic and workflow handoffs with focused offline tests; 88 tests and 9 strict OpenSpec changes pass locally; live qualification remains pending.
- [x] T0009-013 Close all four review findings: serialize budget stop versus task finalization, supervise child lifetime independently of the coordinator, reject missing/inconsistent execution databases, and verify every recorded gate artifact on recovery/backup. Fourteen new regressions pass; total 102 offline tests. Publish the verified corrections under T0009-008 without activating budgets; current-head PR checks provide publication evidence.

Application implementation agents remain unrequested and MUST NOT be launched by these preparation steps.
