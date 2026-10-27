# Bounded cleanup packets

## T0011-001

Read the revised proposal, capability, design, cleanup-plan and path-manifest.
Check the whole T0011-001/T0011-002 batch before deletion. Writes: this change's
readiness-report.md, path-manifest observation metadata and tasks.md status.
Record READY/BLOCKED with measured versions, exact source/backup identities,
known local changes, protected-content comparisons and owned-service disposition.
No runtime/application/model check is required by the new scaffold scope.

## T0011-002

Depends on successful fresh T0011-001. Write/delete only path-manifest.json's
listed paths and this change's status/report documents; root guidance/package
configuration and the lightweight analyzer wrapper are explicitly in scope.
Delete both implementations and all runtime/deployment/execution coupling.
Retain product materials and analyzer rules/fixtures byte-for-byte. Verification:
only the strict OpenSpec, existing analyzer fixture, preservation, link and final
inventory checks in cleanup-plan.md. No application tests or feature work.
Stop on a failed required check or an unreviewed path/requirement change. No commit,
push, merge, private volume deletion or sibling changes.
