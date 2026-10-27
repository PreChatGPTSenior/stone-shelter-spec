# Stone Shelter specification scaffold

This is a minimal starting repository for evaluating implementations against the
supplied product specification. No application, agent runner or deployment is
bundled. Start at START-HERE.md and the relevant openspec/specs capability.

- Read the relevant requirements/scenarios before implementation. Change the
  specification first when behavior or a technical assumption changes.
- Keep product-baseline immutable source evidence; OpenSpec is maintained authority.
  Product requirements include original technical choices. Explicitly agree and
  record changes needed for another language/framework before implementing it.
- Every implementation plan starts with an explicit readiness task covering the
  complete requested batch. Run only requested tasks and focused checks.
- Use pinned OpenSpec and static analyzers; no floating versions or silent rule
  suppression. Google Checkstyle and standard/custom PMD rules are the Java
  profile; ESLint/TypeScript/React Hooks rules are the JS/TS profile.
- Do not claim analyzer fixtures are application acceptance. Missing source,
  missing tools and failed checks must fail rather than report a fabricated PASS.
- Generate HTTP contracts from the chosen implementation; do not maintain a
  competing handwritten OpenAPI. Update AsyncAPI before Kafka implementation.
- Do not add runtime services, deployment, Sonar, Playwright, provider/model calls
  or Agent Warden integration unless explicitly requested. No replacement harness.
- Preserve Git history, external backups, private data and sibling projects. Never
  print secret values. Exclude engineering-log paths before traversal.
- All artifacts are English. No commit, push or merge without explicit request.
