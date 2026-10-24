# Tooling change tasks

This list is separate from `execution/tasks.json`: these tasks prepare the coordinator and are not application-agent tickets. IDs are preserved; runtime qualification must not be silently treated as completed.

## 1. Environment readiness
- [x] T0009-001 Rechecked for follow-up T0009-010–T0009-012 and the explicitly requested T0009-008 publication: permitted control-plane files present; Python 3.14.8, Node 24.21.0 and npm 11.19.0 match pins. Local and remote PR branches share the verified baseline; Git identity is configured and CI contains offline checks only. READY for the authorized corrections, verification and publication. Application services/accounts/models NOT_CHECKED and unnecessary for this batch. No agents or model requests authorized.

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

Application implementation agents remain unrequested and MUST NOT be launched by these preparation steps.
