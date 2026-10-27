# Minimal environment setup

Pinned tooling: Node 24.21.0, npm 11.19.0, Python 3.14.8 and OpenSpec 1.14.3.
Java static analysis uses JDK 23.0.2, Checkstyle 14.3.0 and PMD 7.28.0.
Analyzer artifact URLs/checksums are in quality/static-analysis/toolchain.json;
Node analyzer versions are exact and locked in that directory.

```sh
npm ci --ignore-scripts --no-audit --no-fund --cache .cache/npm
npm run quality:setup
npm run check:specs
```

For Java checks, explicitly supply JAVA_HOME selecting JDK 23.0.2. On macOS:

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 23.0.2)" npm run check:quality
JAVA_HOME="$(/usr/libexec/java_home -v 23.0.2)" npm run check:java -- path/to/java/source
```

Check JavaScript/TypeScript explicitly:

```sh
npm run lint -- path/to/js-or-ts/source
```

npm run check validates all OpenSpec documents and the existing positive/negative
analyzer fixtures; supply the same JAVA_HOME. The wrapper rejects empty or unsafe
source paths and propagates real tool/rule failures. These checks do not execute
an application or evaluate model output.

Setup downloads only the declared checksum-verified analyzer artifacts and locked
Node packages into ignored local caches/dependencies. No Docker, database, model,
Sonar, deployment credentials, Maven application build or provider key is needed.
Choose any additional language/runtime and its qualification explicitly for a
future experiment rather than silently changing the supplied product target.
