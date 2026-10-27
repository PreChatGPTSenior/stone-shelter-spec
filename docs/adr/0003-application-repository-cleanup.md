# ADR 0003 — Minimal specification and static-analysis scaffold

Status: Accepted, revised by explicit user direction on 2026-10-27.

Feature: [0011-application-repository-cleanup](../../openspec/changes/0011-application-repository-cleanup/proposal.md).

## Decision

This repository is an initial specification/quality scaffold for evaluating
implementations by different languages and models. The user explicitly chose
removal of the Java/Spring and React implementation. Retain OpenSpec product
requirements and source materials, AsyncAPI, knowledge/qualification data and
standalone pinned ESLint, Checkstyle and PMD rules/fixtures. No application,
runtime, deployment or execution product is bundled.

All 85 requirement IDs and 194 scenarios are promoted into canonical OpenSpec
specs. Only R002's obsolete task-registry clause is removed. Original technical
choices remain explicit product requirements; a later language experiment must
review any needed requirement changes before implementation.

T0011-001 readiness is refreshed against this scope before T0011-002. Earlier
backend curl/test-JVM blockers describe the superseded application-ready plan;
this minimal project does not require those deleted dependencies. No remaining
required quality check is waived. Old evidence is archived outside the source tree.

## Confirmed exclusions

- **OOS-0011-001 — Agent Warden integration is deferred.** No adapter, router,
  execution contract, hook or integration placeholder is included.
- **OOS-0011-002 — The generic execution product belongs to Agent Warden.** Remove
  its coordinator, dispatch, skills/templates, model policy and accounting.
- **OOS-0011-003 — No application implementation in the initial scaffold.** Remove
  both the Java/Spring and React implementations, their builds and existing tests;
  later experiments start from the supplied specification.
- **OOS-0011-004 — Runtime/deployment and heavy qualification are deferred.** Remove
  Docker stacks, Sonar, Playwright and runtime/test-environment gates. They can be
  designed and added later; retained product release requirements stay visible.

Keep these IDs/meanings synchronized with
[out-of-scope-decisions.md](../out-of-scope-decisions.md).

## Boundaries

Preserve Git history, the verified remote backup, user IDE state, application
volumes and sibling projects. Stop only services created by this chat. No paid
model calls, new benchmark/agent harness, commit, push or merge is authorized.
