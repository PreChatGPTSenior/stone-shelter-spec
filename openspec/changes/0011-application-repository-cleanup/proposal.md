# Minimal specification and quality scaffold

## Why

The user changed the target on 2026-10-27: this repository is an initial project
for evaluating implementations by different languages and models against a given
product specification. They explicitly selected removal of both the Java/Spring
and React implementation. A running Stone Shelter release is no longer the
cleanup deliverable.

## What Changes

- Retain OpenSpec product requirements/scenarios and the source product materials,
  qualification data, visual references and AsyncAPI specification they need.
- Retain lightweight, directly invocable ESLint, Checkstyle and PMD with pinned
  versions, existing rules and positive/negative analyzer fixtures.
- Remove both application implementations, framework builds, deployment assets,
  Sonar, Playwright, runtime gates, coordinator, skills, role/model/budget policy,
  execution registry and obsolete implementation packets/documentation.
- Promote all eight product capability documents into openspec/specs without
  losing requirement IDs or scenarios; remove the obsolete registry clause in
  R002. Product source materials and other product requirement text stay intact.
- Rewrite project guidance and package scripts for the minimal scaffold.

## Capabilities

### New Capabilities

- `application-repository`: minimal specification/static-analysis project with
  no bundled implementation, runtime or execution integration.

## Impact

The working target is stone-shelter-spec, main d1a88c56955b938efbbdb441ec98c3a6f6463741.
The separately verified stone-shelter-spec-backup retains all five original
branches and reachable history. No commits, pushes or history rewrites are
included. Git history, user IDE state, application volumes and sibling projects
are preserved.

This revision supersedes the earlier application-ready cleanup plan. Its failed
backend image and unimplemented test-JVM handshake are not prerequisites of a
specification-only scaffold. No still-required check is waived: readiness must
be refreshed against this smaller declared scope before deletion.

See [requirements/scenarios](specs/application-repository/spec.md),
[tasks](tasks.md), [plan](cleanup-plan.md) and [exact path manifest](path-manifest.json).
