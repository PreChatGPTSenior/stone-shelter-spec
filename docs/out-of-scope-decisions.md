# Confirmed out-of-scope decisions

Only user-confirmed exclusions are listed. These decisions do not authorize a
later implementation, integration or publication.

| ID | Confirmed decision | Feature / ADR |
|---|---|---|
| OOS-0011-001 | Defer all Agent Warden integration; no adapter, router, contract, hook or placeholder. | [0011](../openspec/changes/0011-application-repository-cleanup/proposal.md), [ADR 0003](adr/0003-application-repository-cleanup.md) |
| OOS-0011-002 | Generic coordination, dispatch, skills/templates, model policy and accounting belong to Agent Warden and are removed here. | [0011](../openspec/changes/0011-application-repository-cleanup/proposal.md), [ADR 0003](adr/0003-application-repository-cleanup.md) |
| OOS-0011-003 | Remove the Java/Spring and React implementation; retain an initial specification/quality scaffold for later experiments. | [0011](../openspec/changes/0011-application-repository-cleanup/proposal.md), [ADR 0003](adr/0003-application-repository-cleanup.md) |
| OOS-0011-004 | Defer runtime/deployment, Sonar, Playwright and runtime/test-environment gates; add them later when requested. | [0011](../openspec/changes/0011-application-repository-cleanup/proposal.md), [ADR 0003](adr/0003-application-repository-cleanup.md) |

Confirmed on 2026-10-27, including explicit confirmation to delete the implementation.
Keep IDs and meaning synchronized in both records whenever an exclusion changes.
