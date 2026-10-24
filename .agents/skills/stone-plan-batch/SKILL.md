---
name: stone-plan-batch
description: Prepare a bounded Stone Shelter task batch and explain dependencies before implementation.
---

# Stone Plan Batch

Read execution/tasks.json and the selected OpenSpec change. Run python3 tools/orchestrate.py plan --tasks <IDs>. Identify missing dependencies, write ownership and required environments. Do not launch agents unless the current request authorizes execution. Use templates/task-packet.json for proposed new tickets; validate the graph before adding a task.
