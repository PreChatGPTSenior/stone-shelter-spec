# Minimal scaffold cleanup tasks

Scope revised by the user's explicit request on 2026-10-27, including confirmed
removal of Java/Spring and React implementations. The earlier runtime readiness
result is superseded; fresh whole-batch readiness is READY for this scope.
See [readiness-report.md](readiness-report.md).

- [x] **T0011-001 — Verify readiness for the complete minimal cleanup batch.**
  Cover T0011-001 and T0011-002 together: target/backup, exact paths, product/rule
  preservation and pinned OpenSpec/static-analysis prerequisites.
- [x] **T0011-002 — Remove implementation and runtime; qualify the minimal scaffold.**
  Depends on fresh successful T0011-001. Apply path-manifest.json, preserve product
  requirements and analyzer rules, then run only the checks in cleanup-plan.md.

The latest user request authorizes this revised cleanup after readiness. No
application feature, deployment, Warden integration, commit, push or merge is
included. See [requirements/scenarios](specs/application-repository/spec.md) and
[plan](cleanup-plan.md).

Completed for the revised scope on 2026-10-27; see [verification-report.md](verification-report.md).
