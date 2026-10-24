## ADDED Requirements

### Requirement: Explicit role and ticket model policy
The coordinator MUST resolve an explicit provider, exact model ID and reasoning effort before each agent call, using preset role, then ticket default, then ticket role precedence. Unknown fields, roles, tickets or profiles MUST block. A profile replacement MUST reset previous profile values before applying its own override fields.

#### Scenario: Default GPT development and review
- **WHEN** a ticket uses the default preset with no overrides
- **THEN** the developer uses `gpt-6.1-sol` with `medium` and code/test reviewers use `gpt-6.1-sol` with `high`.

#### Scenario: Mixed OpenRouter selection
- **WHEN** the selected preset is `mixed-openrouter`
- **THEN** development requests `qwen/qwen3-coder-next`, code/test review requests `z-ai/glm-5.3-flash`, and QA/browser roles request `minimax/minimax-m3.1-flash`, subject to successful capability admission.

#### Scenario: Ticket-specific role override
- **GIVEN** a preset and ticket default choose one model profile
- **WHEN** the ticket assigns another profile and effort to its developer
- **THEN** only the resolved override scope changes and the effective choice is recorded in that agent's receipt.

### Requirement: Secret-free router authentication
The configuration MUST store only a credential environment-variable name and an HTTPS API base URL without embedded credentials. The coordinator MUST supply provider/model/effort without modifying global configuration or disabling execution protections. Known credential values MUST be redacted from stored CLI logs and final results.

#### Scenario: Missing credential
- **WHEN** the selected router credential is unavailable
- **THEN** admission blocks with the variable name and no application agent starts or secret value appears in reports.

### Requirement: Compatibility admission before launch
The coordinator MUST check the pinned CLI and refreshed model catalog, advertised effort and image modality where required, then demonstrate a real read-only tool-result round trip, structured response and usage event stream for every selected unique combination. A listing or plain-text success alone MUST NOT grant admission. Changed, missing or expired evidence MUST block the next agent call; no automatic model substitution or reasoning downgrade is permitted.

#### Scenario: Unsupported effort or unknown catalog shape
- **WHEN** the selected level is absent from the model catalog or its schema cannot be validated
- **THEN** the coordinator blocks before implementation and preserves the failure state.

#### Scenario: Live compatibility probe
- **GIVEN** explicit authorization for live model checks and available credentials
- **WHEN** the model reads the scratch sentinel through a successful local tool call and returns the required result with usage
- **THEN** admission records the selection/policy hashes, actual probe evidence, CLI version, timestamp and expiry scope.

#### Scenario: Configuration preview
- **WHEN** the user requests resolved model choices without execution
- **THEN** the coordinator makes no model calls and reports CONFIGURATION_ONLY and NOT_CHECKED compatibility.

### Requirement: Model and usage evidence for each iteration
The coordinator MUST attach requested selection to every agent handoff and log each invocation and observed turn with task, role, iteration, UTC start/end, monotonic elapsed time and available token counters. Preflight and integration re-review calls MUST be included with distinct labels. Cached input and reasoning output MUST NOT be double-counted. Missing usage or monetary cost MUST remain unknown, not zero or fabricated.

#### Scenario: Multiple turns in a repair iteration
- **WHEN** a developer performs multiple model turns in a second task attempt
- **THEN** each observed turn retains its own duration and token counts, and the invocation total sums input plus output once under that task/attempt/model binding.

#### Scenario: Failed or interrupted invocation
- **WHEN** the process fails, times out or returns incomplete usage
- **THEN** known usage, elapsed time and failure evidence remain in the private ledger and no incomplete result passes admission.

### Requirement: No-execution authoring boundary
Preparing configuration and code MUST NOT launch application agents, call model APIs, run tests or claim compatibility without explicit execution authorization. The pre-existing application quality gates MUST remain required after model admission.

#### Scenario: Current preparation request
- **WHEN** the user authorizes files but prohibits running code
- **THEN** the change is authored with verification NOT_RUN, no credentials are fetched, and historical CI is not presented as validation of the new change.

#### Scenario: Offline qualification authorization
- **WHEN** the user subsequently authorizes offline tests and PR publication but prohibits agents and model requests
- **THEN** tests use fake inference processes and temporary ledgers, live qualification remains pending, and only the wrapper change is published.

### Requirement: Independent persistent provider budgets
The coordinator MUST maintain separate Codex and OpenRouter budgets measured in observed input plus output tokens, including probes, repairs and integration reviews. Limits default to zero until configured. Provider routing, not model name, determines the pool. Consumption MUST persist across retries, presets and coordinator restarts; one pool MUST NOT borrow from the other. Unknown usage MUST NOT become free usage. Currency cost and account quota MUST NOT be inferred from tokens.

#### Scenario: Feature admission and concurrent calls
- **WHEN** a feature/frontier or any model invocation is about to start
- **THEN** the coordinator checks its required pools and atomically reserves the configured invocation allowance before starting each model process; concurrent reservations cannot exceed remaining capacity.

#### Scenario: Limit reached during execution
- **WHEN** observed consumption reaches a pool limit or an invocation allowance
- **THEN** the coordinator records a persistent stop, cancels running model processes on the next monitoring tick, blocks subsequent features/calls/reviews/merges and does not mark the affected task DONE.

#### Scenario: Missing usage or interrupted coordinator
- **WHEN** usage is missing, malformed or a previous reservation remains open after a coordinator restart
- **THEN** the reserved amount remains charged and development blocks pending explicit accounting reconciliation; retrying a task does not reset the budget.

#### Scenario: Delayed telemetry
- **WHEN** the CLI reports tokens only at turn completion
- **THEN** receipts preserve observed overrun and documentation states that cancellation cannot prevent tokens already consumed by an in-flight turn; this is a local token guard, not a guaranteed provider-side monetary cap.
