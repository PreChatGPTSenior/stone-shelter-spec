# Design

## Decision
Use the existing Codex CLI runner and Python standard library. `execution/agent-models.json` is the secret-free policy. `tools/execution/model_policy.py` resolves it, and `agent_runtime.py` performs explicitly authorized invocation/admission/accounting. Model selection is passed as explicit CLI arguments; no change to global Codex configuration, sandbox policy or stored authentication is required.

OpenRouter uses its Responses endpoint and Codex's command-backed credential protocol to refresh model metadata. The helper reads only the named environment variable; no key is stored in the policy, command arguments or reports. Profiles and schemas use exact user-requested slugs. A source catalog declaration plus a real bounded read-only tool/schema/usage loop is required; sample configuration alone is not compatibility evidence.

## Precedence and evidence
Preset role → ticket default → ticket role. A replacement profile resets inherited effort before the override's effort applies. Every receipt includes hashes of the effective provider/model/effort binding and the entire policy. A ticket/role gets the effective binding, never an agent-selected substitute. Unknown models, unsupported effort, missing image metadata for browser roles, missing credentials, malformed events and stale/mutated receipts block.

The initial admission covers every unique combination in the selected remaining batch. Frontiers refresh expired combinations; each agent checks receipt age again. A receipt may expire during a long task, which blocks the next role until a newly authorized retry/preflight. This avoids an unattended switch of model or effort.

## Accounting
The runner timestamps CLI events while consuming stdout, keeps stderr separately and appends one locked JSONL record per invocation. `turn.completed` usage is summed once; cached input/reasoning output remain subsets. Missing/incomplete values stay unknown, with known partial counts preserved. Failed process calls retain receipts. The agent result schema remains unchanged; the coordinator adds a separate envelope with selection, accounting and preflight evidence.

Selected model and provider-reported observed model are separate. Missing observations are not fabricated. Real billing amounts are not available from the existing Codex JSON usage protocol, so monetary cost remains null. Preflight is metered and labelled separately. Sum of concurrent session durations is not wall-clock batch duration.

## Verification boundary
The follow-up user request authorizes offline wrapper tests, fixes and PR publication. Inference processes are fakes and network connections are denied in the model/budget tests. No application, agent, credential helper or model API call is run. Live Codex catalog shapes/account access and exact provider effort support remain NOT_VERIFIED; the code is fail-closed if qualification fails. Current observations are recorded in docs/verification.md.

## Separate budgets and cancellation
Use the Python standard library and a private SQLite ledger, separate from task state. Both pools default to zero. Route determines the pool; model identity never moves GPT-through-OpenRouter charges into Codex. Every process atomically reserves an invocation allowance; every reported terminal turn updates monotonic known consumption. Final complete usage settles the reservation. Unknown usage holds capacity and latches a stop; proven process-start failure releases it. Orphaned reservations on restart latch a stop instead of assuming no usage.

The coordinator checks before frontiers/features, invocations and stage/integration boundaries. Running model processes poll the shared stop; either pool's limit or an invocation allowance stops all model development. Existing local test processes may finish, but cannot proceed to another stage or DONE. Stops persist across restarts and retries, with no automatic reconciliation/reset. Live budget-policy mutation stops the run. Missing accounting beside an existing usage ledger blocks migration instead of treating historical calls as free.

Turn-end telemetry cannot enforce a true pre-consumption cap: already in-flight work may overrun. Preserve full observed consumption, never clamp it to the reservation. Schema 2 adds configured USD estimates using integer microdollars and pinned expiring per-model rates; schema 1 token budgets remain compatible. Tokens stay in all reports, actual invoices/subscription quota remain unknown, and accounting-unit changes with history require reconciliation. Default USD limits are zero and rates are empty. Provider-side monetary caps are separate controls.

## Correlated evidence and recovery
The local SQLite journal records observed event order, task/requirement/acceptance context, attempt/batch/session/role, tool items, explicit decisions, scoped before/after file hashes, gates and integration intent/completion. Raw JSONL and atomic receipts are synced to disk; the journal uses WAL/FULL transactions and a hash chain. State transitions separately record owner/commit/claim/release. Startup checks unfinished ownership, invocations, integrations and budget state before live preflight.

Three databases, raw files and Git are not one transaction. Interrupted cross-store operations remain unresolved and block; there is no automatic tool replay, lease stealing or inference that an absent receipt means no side effect. The new inspection CLI verifies timelines/evidence and creates verified audit backups with SQLite's backup API; restore targets a new directory. It restores audit history, not worktrees, application volumes or ephemeral model context. Full recovery needs separately retained source/data and reviewed reconciliation. SQLite is retained for the single-host coordinator; no new database/framework is introduced. See docs/execution/workflow-assurance.md for operational steps, limitations and offline/live proof boundaries.

## Review corrections (T0009-013)
Recheck budget stops after integration intent and serialize final DONE publication against stop writers in a short accounting transaction. Do not hold that transaction across Git: a stop during merge must leave an unresolved intent and BLOCKED task, with the actual Git result retained for reconciliation. This establishes an explicit completion/stop ordering without claiming a distributed transaction or retroactive enforcement of delayed usage.

Use a standard-library POSIX supervisor with a coordinator-owned pipe and independent timeout. A live group anchor keeps the process-group identity owned even after the CLI exits; it relays the CLI result and remains until group cleanup. Parent loss, timeout or cancellation triggers TERM/KILL of the owned group. No stale PID lookup is used. A killed coordinator cannot settle usage, so reservations/history remain unresolved. Deliberately escaped groups, remote work already accepted and simultaneous supervisor/OS failure remain outside this local lifetime guarantee.

Recovery cross-checks task events against current rows/owners/commits and invocation sessions against budget rows before any database initialization. Gate events now retain file hashes and all stage artifact references are verified, including copied analyzer output under the audit run. Verified backups reject gaps and preserve these copies independently of analyzer caches; complete preflight-only histories do not require a task database.
