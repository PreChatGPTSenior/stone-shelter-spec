---
name: stone-run-batch
description: Execute an explicitly authorized Stone Shelter ticket batch through isolated development and review sessions.
---

# Stone Run Batch

Read START-HERE.md, docs/execution/handoffs.md and the exact selected task packets. Check that the user authorized these IDs or the full batch. Run python3 tools/orchestrate.py run --tasks <IDs> --workers 2 --execute from the new execution repository. Never target sibling projects. The coordinator owns task commits, resources and state; do not independently mark DONE. On BLOCKED inspect the original failure, preserve evidence and correct only authorized setup; retry requires a new diagnosed attempt.
