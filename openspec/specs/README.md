# Product capability specifications

OpenSpec is the maintained target authority; the original source specification,
visual references, acceptance matrix and qualification inputs remain in
[product-baseline](../../product-baseline/specification.json).

| Capability | Requirements |
|---|---|
| [Foundation](foundation/spec.md) | Scope, actors, security, shared contracts and technical constraints |
| [Catalog and details](catalog-details/spec.md) | Public catalog and navigation |
| [Stone management](stone-management/spec.md) | Authorized management and photos |
| [Adoption lifecycle](adoption-lifecycle/spec.md) | Requests, resolution, privacy and races |
| [Reliable indexing](reliable-indexing/spec.md) | Durable indexing and source recovery |
| [Conversation](spring-ai-conversation/spec.md) | Grounded search/chat and model admission |
| [Streaming/browser behavior](streaming-browser/spec.md) | Streaming, accessibility and session behavior |
| [Release qualification](release-qualification/spec.md) | Fixed AI scenario/oracle thresholds |

All 85 requirement IDs and 194 scenarios survive the scaffold extraction.
The original technical assumptions remain visible; a different-language
experiment must explicitly record any intended changes before implementation.
These are future product requirements, not completed features or installed
runtime services. The scaffold itself requires only OpenSpec/static-analysis tools.
