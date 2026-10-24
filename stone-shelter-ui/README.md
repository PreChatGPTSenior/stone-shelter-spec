# Stone Shelter UI

React + TypeScript catalog, details/gallery, stone creation and adoption. Use
Node 24.21.0 and npm 11.19.0. Normal development and production builds use the
real API; mocks are an explicit alternative.

## Run with the Backend

The UI supports both the full Docker stack and standalone host development
with hot updates. For the full stack, from the repository root:

```sh
docker compose --profile frontend up -d --build --wait --wait-timeout 180
```

Open http://localhost:5173/stone-shelter/catalog; override `FRONTEND_PORT` for
another published port. Vite inside the frontend container proxies to
`http://stone-shelter-api:8080`. The base Compose command omits the frontend.
Before using the host command on the same port, stop only the frontend service:

```sh
docker compose --profile frontend stop stone-shelter-ui
```

The host mode described below retains Vite hot updates without Docker rebuilds
or backend restarts. Stop the host Vite process before returning to Docker mode.
Backend/data services and their volumes remain running while modes switch.

From the repository root, prepare `.env` from `.env.example` on first launch,
then start the current backend:

```sh
cp .env.example .env # First launch only; preserve an existing .env.
docker compose up -d --build
```

When updating an existing environment, retain its settings and add missing
`MINIO_DRAFT_BUCKET` and `STONE_PHOTO_PLACEHOLDER_URL` values from `.env.example`.
Rebuild an older API image: current UI creation requires draft uploads and the
server-owned admission date. PostgreSQL and MinIO volumes persist across rebuilds.

Then prepare and start the UI:

```sh
cd stone-shelter-ui
npm ci
cp .env.example .env.local # First launch only.
npm run dev:api
```

`npm run dev` also uses the API. Open the local URL printed by Vite and navigate
to `/stone-shelter/catalog`. `API_PROXY_TARGET` in `.env.local` supplies the backend
origin; its example is `http://localhost:8080`. Vite proxies `/api` and `/images`
in development and preview. Keep `VITE_API_BASE_URL` empty for same-origin
requests. Addresses are configurable; no MinIO/database credentials reach the UI.
The backend's `MINIO_BROWSER_ENDPOINT` must be reachable from the browser.

An empty real database displays an empty catalog. Add stones through the UI or
explicitly use the existing [API data loader](../specs/0008-add-stone-api/data-loader.md).
No launch or ordinary test command seeds the database automatically.

## Run with Mocks

```sh
npm run dev:mock
```

This selects Vite's `mock` mode. It needs neither backend nor MinIO and starts
with 30 AVAILABLE stones. Mock-created stones, uploaded previews and reservations
remain in memory until a full reload. API errors never silently enable mocks.
The catalog shows only AVAILABLE stones in both modes; reserved stones stay
readable through their detail URL.

## Existing UI Flows

- Catalog: `/stone-shelter/catalog`, filters, six sort choices and page sizes
  8/12/24. Counts and pagination use API response metadata.
- Details: `/stone-shelter/stones/{id}`, complete biography, characteristics,
  ordered photo gallery and adoption application.
- Creation: `/stone-shelter/add-stone`, name/type/size, optional biography and
  zero to 16 JPEG/PNG/WebP photos up to 10 MiB each in the existing 4 × 4 grid.
  Draft files upload separately; creation sends ordered UUID references, and
  the backend assigns admissionDate.

Loading, recoverable errors and manual retry are visible. Invalid IDs/HTTP 404
show Stone not found. Creation failure retains the form and uploaded references;
partial upload failure retains successful photos in selection order. No write is
retried automatically. Successful creation/reservation refreshes the catalog
without resetting session choices. Reserving a stone removes it from the
AVAILABLE-only catalog and updates its details in the same modal flow.

Existing SPA history, catalog scroll/session restoration, canonical links and
legacy redirects remain. Missing/broken images use `/placeholder-rock.png`.

## Build and Verify

```sh
npm run lint
npm run test -- --run
npm run build
npm run preview
```

Ordinary UI tests explicitly use the mock adapter, with isolated runtime state;
HTTP-boundary tests stub fetch. They require no running backend. The API build
excludes mock datasets. To build a standalone mock variant:

```sh
npm run build:mock
npm run preview -- --mode mock
```

The last build determines the contents of `dist`; run `npm run build` again to
restore the API artifact. Backend/API base URL selection is made at build time.

An explicitly invoked real transport test is also available:

```sh
API_PROXY_TARGET=http://127.0.0.1:8080 npm run test:api
```

Point this command at an isolated backend with photo storage enabled and an
empty catalog. It executes the actual UI API adapter through a temporary Vite
proxy, creates two test stones, verifies gallery bytes/readback/reservations,
then deletes its stones and closes its proxy. Backend object cleanup follows the
existing storage lifecycle; dispose isolated infrastructure to remove all test
objects, including drafts from failed runs. This command is never part of the
ordinary UI test suite or startup.

## Production Routing

Serve `index.html` for client page URLs while serving assets normally. Route
`/api` and `/images` to the backend using a same-origin reverse proxy. An optional
`VITE_API_BASE_URL` can instead specify a backend base URL at build time; that
backend must permit the UI origin through CORS. Vite's development proxy is not
included in static production assets. The `/stone-shelter` page prefix does not
relocate API routes or assets. Hosting and backend CORS changes are outside this
ticket.

## Stone Chatbot Demo

Catalog and stone details include a collapsible chat demo. Its UUID, messages,
draft and pending request survive client-side navigation through a shared
provider. Reload starts a new conversation. API mode calls
`POST /api/v1/chat/messages`; explicit mock mode returns the same fixed answer.
The two demo links address Mars (1) and Luna (3) in the mock catalog. A real
catalog may not contain them and uses the ordinary not-found page. Spring AI
and server history are deferred to a later feature.

Run only the chatbot UI tests with:

```sh
npm run test -- --run src/api/chatbotApi.test.ts src/features/stone-chatbot
```

After an API build, the chatbot transport smoke can run against any backend
exposing the stub, without creating or changing catalog data:

```sh
API_PROXY_TARGET=http://127.0.0.1:8080 node scripts/chatbot-smoke.mjs
```

This checks the actual adapter, development/preview proxies, validation and
standalone mock mode. Scope and acceptance evidence live in
[0012-stone-chatbot](../specs/0012-stone-chatbot/spec.md).

Decisions, scope and evidence are in
[0011-ui-api-integration](../specs/0011-ui-api-integration/spec.md) and its
[acceptance criteria](../specs/0011-ui-api-integration/acceptance.md).
