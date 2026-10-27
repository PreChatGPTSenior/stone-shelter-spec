# Start here

1. Read the [OpenSpec capability index](openspec/specs/README.md). It preserves
   all 85 product requirement IDs and 194 scenarios from the supplied target.
2. Consult product-baseline for the original specification, visual references,
   acceptance matrix and fixed qualification inputs; data contains the approved
   encyclopedia fixture and contracts contains the maintained AsyncAPI schema.
3. Follow [environment-setup.md](environment-setup.md) to install only OpenSpec
   and the static analyzers, then run npm run check.
4. Choose the language/model experiment and create an OpenSpec change and task
   plan with readiness first before adding implementation code. Review explicit
   changes to the original Java/Spring/React technical assumptions when needed.

This tree contains no backend, frontend, Docker stack, Sonar server, browser-test
runner, provider credentials, model policy or Agent Warden integration. Product
release requirements describe the future implementation; they do not require
those services to open or validate this scaffold. Static fixture success is not
business acceptance. Runtime and integration can be added later when requested.
