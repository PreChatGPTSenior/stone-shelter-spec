# Minimal cleanup plan

The user approved removal of both implementations and all runtime/deployment
coupling. This plan supersedes the former application-ready plan.

1. Refresh T0011-001 for T0011-001 and T0011-002: verify target/backup refs,
   account for local planning files, enumerate exact paths and save original
   product requirements/scenarios and preserved-material hashes outside the tree.
2. Promote the eight product capability documents to openspec/specs, preserving
   all 85 requirement IDs and 194 scenarios. Change only R002's obsolete registry
   clause. Keep product source materials, qualification/source data and AsyncAPI.
3. Retain existing analyzer pins, PMD/Checkstyle/ESLint rules and their fixtures.
   Replace the packet-based wrapper with direct setup/java/lint/fixtures commands.
4. Rewrite root guidance, OpenSpec context and package scripts. Remove Playwright
   from the root manifest/lockfile; retain OpenSpec at its existing exact version.
5. Delete only the manifest's enumerated tracked paths: both application trees,
   deployment/infrastructure, runtime/Sonar/browser configuration, execution
   product and registry, skills/templates and obsolete docs/change wrappers.
6. Remove generated dependencies inside deleted application/browser directories
   and archive this chat's private Sonar setup outside the repository. Never
   follow symlinks or touch Git, IDE state, human-only paths or application volumes.
7. Check strict OpenSpec, unchanged product/rule hashes, requirement/scenario
   identities, source-tree contents and positive/negative analyzer fixtures.
8. Report measured results and stop. Do not implement a feature or create/push
   a commit automatically.

No Docker, database, storage, model, Sonar or actual application-test JVM is a
prerequisite of the revised batch. Java is needed only to run the Java analyzers;
Node/npm and Python are needed for OpenSpec and the lightweight analyzer tooling.
The earlier backend curl and JVM-handshake findings remain historical evidence
of the superseded application-ready scope, not waived scaffold prerequisites.

## Focused verification

- Strict OpenSpec validation of all retained canonical capabilities and this change.
- The existing quality/static-analysis/fixtures positive/negative Java and React
  cases against real Checkstyle, PMD and ESLint, including every named PMD rule.
- Canonical requirement/scenario comparisons and byte-for-byte hashes for retained
  product materials, data, AsyncAPI, analyzer rules and fixtures.
- Package/lock consistency, Python syntax, relative documentation links and final
  inventory: no implementation, deployment, Sonar, Playwright or coordinator code.

These are analyzer fixture checks, not an application test suite or business PASS.
