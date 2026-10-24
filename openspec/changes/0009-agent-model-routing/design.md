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

Turn-end telemetry cannot enforce a true pre-consumption cap: already in-flight work may overrun. Preserve full observed consumption, never clamp it to the reservation. Tokens are the budget unit; price, provider invoices and subscription quota are deliberately not inferred. Provider-side monetary caps are separate controls. The standalone protocol examples are outside coordinator accounting and are not execution paths for project features.
