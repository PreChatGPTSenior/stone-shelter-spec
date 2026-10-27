# Design: minimal source tree

Keep only product specification material, OpenSpec and standalone static analysis.
The product source snapshot, data, AsyncAPI and analyzer rules/fixtures are
preserved byte-for-byte. The eight product delta documents become canonical
OpenSpec specs with Purpose and Requirements sections. All 85 requirement IDs
and 194 scenarios survive. R002 loses only the deleted task-registry clause.
No business acceptance is silently rewritten to suit a later implementation.

Product requirements still contain the original technical choices and future
release requirements. They are target requirements, not installed scaffold
services. A language/framework experiment must declare and review any relevant
technical-requirement changes before implementation; this cleanup does not
pretend the inherited Java-specific requirements are language-neutral.

The only root Node dependency is pinned OpenSpec. The existing analyzer package
keeps ESLint/TypeScript/React Hooks tooling. A small Python standard-library CLI
invokes the real Checkstyle, PMD and ESLint tools directly: no packets, approvals,
handoffs, evidence engine, scheduler or backend is required. Analyzer assets are
checksum-verified; no new framework or floating version is introduced.

README, START-HERE and setup instructions become a coherent minimal entry point.
The previous registry/48 execution packets and old change wrappers are removed;
product scenarios remain authoritative in openspec/specs. New implementation
plans will be prepared for each experiment, beginning with explicit readiness.

Previous cleanup/readiness evidence is archived outside the source tree. Only
services started by this chat are stopped; no existing application volume or
sibling service is removed. Generated dependencies under the deleted application
and browser directories are removed safely without following symlinks. Private
Sonar setup files are moved to the external task archive. Git and user IDE state
remain untouched.

The user authorized this revised cleanup. T0011-001 first checks the whole new
batch, including backup/target, exact paths, preserved product identities and
pinned local analyzer prerequisites. T0011-002 performs deletion only after that
readiness passes. Verification is limited to strict OpenSpec and the existing
analyzer positive/negative fixture set. No application, model or deployment test
is part of this scaffold cleanup.
