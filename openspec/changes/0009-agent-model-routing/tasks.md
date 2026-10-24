# Tooling change tasks

This list is separate from `execution/tasks.json`: these tasks prepare the coordinator and are not application-agent tickets. IDs are preserved; runtime qualification must not be silently treated as completed.

## 1. Environment readiness
- [x] T0009-001 Recheck prerequisites for the requested T0009-006, T0009-008 and T0009-009 batch: permitted repository and control-plane inputs present; Python 3.14.8, Node 24.21.0 and npm 11.19.0 match pins. READY for offline wrapper development, tests and PR publication. Application services, live accounts and models NOT_CHECKED and not required for this batch. No application agents or model requests authorized.

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

Application implementation agents remain unrequested and MUST NOT be launched by these preparation steps.
