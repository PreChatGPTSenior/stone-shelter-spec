# Frontend execution rules

Read ../AGENTS.md and the selected packet first. Use exact package-lock dependencies.
Feature components/hooks/state belong in src/features; reusable domain UI in src/domain; common layout in src/components/Common; API adapters/DTOs in src/api; mock adapters/fixtures in src/mocks. Components never import mock datasets.
Preserve PascalCase component directories/files and separate enums under src/enums.
Use generated backend OpenAPI as the wire-contract reference. Keep explicit API and mock modes.
Required before review: ESLint, TypeScript and production build; run the focused Vitest selectors in the packet. Real browser acceptance uses isolated API mode with Playwright.
Do not reuse or stop another ticket's server. Shared files are owned according to execution/tasks.json; coordinate changes through the integrator.
