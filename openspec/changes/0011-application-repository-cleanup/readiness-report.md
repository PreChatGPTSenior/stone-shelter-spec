# T0011-001 — Minimal scaffold readiness

Date: 2026-10-27. Covered batch: T0011-001 and T0011-002.

**Result: READY for the revised, explicitly authorized minimal scaffold cleanup.**
The earlier application-ready scope/report is superseded and archived outside
this repository; its backend and JVM-handshake conditions were removed from the
requested deliverable before implementation, not falsely reported as passing.

| Component | Expected | Observed |
|---|---|---|
| Target/base | stone-shelter-spec main, known local plans | Correct origin; d1a88c56955b938efbbdb441ec98c3a6f6463741; local plans belong to this chat |
| Backup | Matching complete Git branch snapshot | Five source/backup refs match; no tags |
| Exact scope | Reviewed paths, no broad filesystem deletion | 881 tracked delete paths and 52 retain paths enumerated; no missing/symlink delete path |
| Product/rules | Preserve specifications and required source material | Original eight capability bodies archived for comparison; 35 retained product/data/contract/rule/fixture hashes verified |
| Node/npm | 24.21.0 / 11.19.0 | Exact versions |
| Python | 3.14.8 | Exact version |
| Java for analyzers | JAVA_HOME selects 23.0.2 | Explicit JDK 23.0.2 confirmed |
| OpenSpec | 1.14.3 | Exact version; revised cleanup change passes strict validation |
| Checkstyle/PMD | 14.3.0 / 7.28.0, declared SHA-256 | Installed artifacts match both checksums; distributions available |
| Linters | Existing pinned ESLint/TypeScript/React Hooks dependencies | Existing analyzer lock installed and consistent with manifest |
| Runtime/deployment | None required | This chat's Docker stacks, isolated provider and frontend stopped; no volume/sibling deletion |

Environment preparation from the preceding readiness run supplied the required
local tools. No model inference, paid calls, business import or application test
is needed or was run for this revised readiness. Refresh if the toolchain, source,
manifest or ownership changes before resuming. T0011-002 may now execute only its
revised manifest and focused verification; no feature, commit or push is included.
