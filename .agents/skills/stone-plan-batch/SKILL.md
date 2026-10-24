---
name: stone-plan-batch
description: Prepare a bounded Stone Shelter task batch and explain dependencies before implementation.
---

# Stone Plan Batch

Include execution/agent-budgets.json and the read-only budgets action in planning. Identify the required Codex/OpenRouter pools, configured limits, available capacity and unsettled calls. A plan neither reserves tokens nor proves live compatibility. Zero or insufficient limits and any persistent stop must be resolved explicitly before launch; do not modify accounting to make a plan executable.

Read execution/tasks.json, execution/agent-models.json and the selected OpenSpec change. When read-only planning commands are authorized, run python3 tools/orchestrate.py plan --tasks <IDs> and python3 tools/orchestrate.py models --tasks <IDs> --model-preset <preset>. Identify missing dependencies, write ownership, effective role/ticket model bindings and required environments. Configuration-only output never proves provider compatibility. Do not launch agents or live model preflight unless the current request authorizes execution; an instruction not to run code also prohibits these planning scripts. Use templates/task-packet.json for proposed new tickets; validate the graph before adding a task when execution is allowed.
