# Supervised subscription batch progress

Execution policy: [subscription-execution.md](subscription-execution.md). Source baseline: PR #3 head `d1c8d218491faae09a460ef63195854e3e7dbdf9`. Native subscription roles are distinct from the locked production dispatcher. Unknown native per-agent token/cost values remain unknown.

| Task | Developer / reviewer / coordinator evidence | Status |
|---|---|---|
| T0010-001 | Whole-batch tools/storage/recovery ready; quota observed; production USD policies unchanged | ACCEPTED for supervised development |
| T0010-002 | Independent reviewer found root ancestor-order P1; developer reproduced with metadata mock, repaired; 38 maintenance tests passed; reviewer APPROVED and coordinator inspected diff/hash/red/green | ACCEPTED offline bootstrap; production launch locked |
| T0010-003 | Previous 111-test and CI proof reviewed; affected maintenance module rechecked after repair (38); unchanged baseline proof retained | ACCEPTED scoped baseline; final aggregate deferred to 019 |
| T0010-004 | Candidate-bound trusted checks; config-only/mixed/unknown path controls; 30 guarded offline tests; independent APPROVED and coordinator acceptance after three attempts | ACCEPTED offline WA-004 |
| T0010-005–020 | Pending sequential implementation/review | NOT ACCEPTED |

T0010-002 actors: developer `/root/maintenance_developer`, requested `gpt-6.1-sol / medium`; reviewer `/root/maintenance_reviewer`, requested `gpt-6.1-sol / high`; coordinator current root session. The reviewer returned CHANGES_REQUESTED then APPROVED; coordinator accepted only after inspecting matching final source hashes and intended failure/passing test evidence. Per-agent token totals are not exposed by native tooling.

Private evidence: `.cache/t0010-subscription-run/events.jsonl`, `bootstrap-review-1.json`, and `T0010-002/attempt-1/developer-repair-handoff.json` with its red/green logs. No private runtime records are committed. The sanitized source identities for the repair are `191e2e1ba2fe8a3de567718cccb403097ccb1cbb8a13a235881e1015827d5c3b` (maintenance) and `56f2f08b1bf7e59bf42af85c93379c8c73e5af679a7806707cf065c759e3a7e5` (maintenance tests).

T0010-004 uses the same developer/reviewer roles. The first review found four defects (package-only admission, enforcement scripts, unvalidated Spring resources and loose runtime pins); coordinator review additionally found unmapped SQL/extensionless paths and required explicit non-executable allowances. Final negative/control evidence passes 25 candidate checks plus five workflow cases. Private evidence is `T0010-004/attempt-3/developer-handoff.json` and `T0010-004/coordinator-acceptance.json` under the run directory. Actual analyzer/build/provider qualification is not claimed; unsupported configuration paths block pending reviewed mappings. Production budgets remain zero.
