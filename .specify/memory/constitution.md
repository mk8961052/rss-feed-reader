# RSS Feed Reader Constitution

## Core Principles

### I. Phase-Gated Scope (MVP → Extended-MVP → Post-MVP)

Every feature, spec, and task MUST declare which delivery phase it belongs to, as defined in
`StakeholderDocuments/ProjectGoals.md` and `AppFeatures.md`:

- **MVP**: add a subscription by URL; display the subscription list. Nothing else.
- **Extended-MVP**: manual refresh, item display (title + link), a single generic
  "Failed to load feed" error.
- **Post-MVP**: persistence (EF Core + SQLite), removal, polling, sanitized HTML, etc.

Rules:

- Work for a later phase MUST NOT start until the current phase's "working" definition is met
  and verified end-to-end in the browser.
- MVP code MUST NOT reference `HttpClient` for feed fetching, `System.ServiceModel.Syndication`,
  EF Core, or any background service. Adding a package or dependency MUST be traceable to a
  requirement of the current phase.
- Speculative abstractions are forbidden unless Principle IV explicitly requires them.

**Rationale**: The project is a fast proof-of-concept. Scope creep is the primary risk to
delivery, and phase labels make every review question ("is this in scope?") answerable.

### II. Untrusted Input, Safe Output (NON-NEGOTIABLE)

Subscription URLs typed by the user and all content returned by remote feeds are untrusted.

- The backend MUST reject a subscription URL that is empty/whitespace, longer than 2048
  characters, or not an absolute URI with scheme `http` or `https` (`Uri.TryCreate` with
  `UriKind.Absolute` plus an explicit scheme check). This is a structural safety guard, not
  feed validation: the app still MUST NOT check whether the URL is a real RSS/Atom feed in MVP.
- Rejected input MUST return HTTP 400 with a short `ProblemDetails` message; the UI MUST show
  that message and MUST NOT add the entry to the list.
- All user- and feed-derived text MUST be rendered through Razor's default HTML encoding.
  `MarkupString`, `@((MarkupString)...)`, and `innerHTML` via JS interop are FORBIDDEN until a
  post-MVP feature introduces `HtmlSanitizer` and a spec explicitly approves its use.
- Any URL rendered as an `href` (subscription or feed item link) MUST be re-checked for an
  `http`/`https` scheme at render time; anything else (e.g. `javascript:`, `data:`) MUST be
  shown as plain text. External links MUST use `target="_blank" rel="noopener noreferrer"`.
- Error responses MUST NOT expose exception messages, stack traces, or internal paths to the
  client. Details go to server-side logs only.

**Rationale**: A stored `javascript:` URL rendered as a link is stored XSS, even in a
single-user app, and Extended-MVP will render remote content we do not control.

### III. Safe Outbound Fetching (Extended-MVP onward)

When the backend fetches feeds, it acts on user-supplied URLs and parses remote XML.

- `HttpClient` MUST be obtained through `IHttpClientFactory` (typed or named client); creating
  `new HttpClient()` per request is forbidden.
- Every fetch MUST have an explicit timeout (default 10 seconds) and a maximum response size
  (default 5 MB) enforced while reading the stream, and MUST honor the request's
  `CancellationToken`.
- Redirects MUST be limited (max 5) and the final URI MUST still be `http`/`https`.
- XML MUST be read with `XmlReaderSettings { DtdProcessing = DtdProcessing.Prohibit,
  XmlResolver = null }` before passing to `SyndicationFeed.Load`, to prevent XXE and
  entity-expansion attacks.
- Fetches to loopback, link-local, and private address ranges SHOULD be blocked; this becomes
  MUST before the app is ever hosted anywhere other than the developer's own machine.
- Only feed item title and link are surfaced in Extended-MVP; item bodies MUST NOT be rendered.

**Rationale**: Unbounded fetching of arbitrary URLs is the classic SSRF and resource-exhaustion
vector, and default XML parsing settings are an avoidable XXE risk.

### IV. Swappable Boundaries Between UI, API, and Storage

The architecture MUST allow persistence and feed operations to be added without a rewrite.

- The backend (`backend/RSSFeedReader.Api`) is the single source of truth for subscriptions.
  The Blazor WebAssembly UI (`frontend/RSSFeedReader.UI`) MUST NOT hold an authoritative copy;
  after a successful add it MUST reflect the list returned by the API.
- All UI ↔ API communication MUST go through versionless JSON endpoints under `/api/`
  (e.g. `GET /api/subscriptions`, `POST /api/subscriptions`) using request/response DTOs.
  Domain or storage types MUST NOT be serialized directly.
- Storage MUST sit behind an interface (e.g. `ISubscriptionStore`) registered in DI. The MVP
  in-memory implementation MUST be registered as a singleton and MUST be thread-safe (`lock`
  or concurrent collection — a bare `List<T>` is not acceptable), because ASP.NET Core serves
  requests concurrently.
- Feed fetching/parsing (Extended-MVP) MUST live in a dedicated service (e.g. `IFeedService`),
  not in endpoint handlers or Razor components.
- Razor components MUST contain only UI state and event handling; API calls MUST go through an
  injected client service, not raw `HttpClient` calls scattered across components.

**Rationale**: `TechStack.md` commits to swapping in EF Core + SQLite and `BackgroundService`
later. Interfaces at these seams make that a registration change, not a rewrite.

### V. Strict, Verifiable Code Quality

- All projects MUST enable `<Nullable>enable</Nullable>`, `<ImplicitUsings>enable</ImplicitUsings>`,
  and `<TreatWarningsAsErrors>true</TreatWarningsAsErrors>`, with .NET analyzers on
  (`<AnalysisLevel>latest-recommended</AnalysisLevel>`).
- Code MUST pass `dotnet format --verify-no-changes`. An `.editorconfig` at the repo root is
  the single style authority.
- I/O MUST be async end-to-end; `.Result`, `.Wait()`, and `async void` (outside Blazor event
  handlers) are forbidden.
- Magic values (ports, URLs, timeouts, size limits) MUST come from configuration or named
  constants, never inline literals.
- xUnit tests are REQUIRED from MVP onward for security-relevant logic: the URL guard
  (Principle II), the in-memory store's add/list behavior, and (Extended-MVP) the hardened XML
  parsing path. Endpoint integration tests via `WebApplicationFactory` SHOULD cover each
  `/api/` route. Broader coverage is post-MVP.
- Names MUST describe domain intent (`Subscription`, `FeedItem`, `AddSubscriptionRequest`);
  generic names (`Data`, `Helper`, `Manager`, `Utils`) are not allowed.

**Rationale**: Compiler-enforced nullability and warnings-as-errors catch defects at build time
at near-zero cost, keeping a fast-moving POC from accumulating hidden debt.

## Technology & Configuration Constraints

- **Stack**: ASP.NET Core Web API backend + Blazor WebAssembly frontend, both C#. The .NET SDK
  version MUST be pinned in a root `global.json`. Approved libraries by phase: none extra for
  MVP; `System.ServiceModel.Syndication` for Extended-MVP; EF Core + SQLite, `HtmlSanitizer`,
  `HtmlAgilityPack` only in post-MVP features whose spec names them.
- **Layout**: `backend/RSSFeedReader.Api/`, `frontend/RSSFeedReader.UI/`, and test projects
  under `tests/`. All projects MUST be referenced from a single solution file.
- **Ports & origins**: backend `http://localhost:5151`, frontend `http://localhost:5213`
  (HTTPS `https://localhost:7025`), defined in each project's `launchSettings.json`.
- **Frontend API URL**: MUST be read from `wwwroot/appsettings.json` key `ApiBaseUrl`; it MUST
  NOT be hardcoded in components or services.
- **CORS**: MUST list explicit frontend origins read from backend configuration
  (e.g. `Cors:AllowedOrigins`). `AllowAnyOrigin()` is forbidden, as is `AllowCredentials()`
  unless a spec requires it.
- **Secrets**: none are needed for MVP/Extended-MVP. If any are introduced, they MUST use
  user-secrets or environment variables and MUST NOT be committed.
- **Platforms**: the app MUST build and run on Windows, macOS, and Linux; scripts and paths
  MUST NOT assume a single OS (use `Path.Combine`, no drive letters).

## Development Workflow & Quality Gates

1. **Template cleanup gate (Phase 2, blocking)**: before any UI feature work, `Home.razor`,
   `Counter.razor`, and `Weather.razor` MUST be deleted, `NavMenu.razor` updated, and exactly
   one page may declare `@page "/"`. Verify by listing `Pages/*.razor`, then
   `dotnet clean` + `dotnet build` + run, and confirm no "ambiguous route" error in the browser
   console.
2. **Build gate**: `dotnet build` of the solution MUST succeed with zero warnings, and
   `dotnet format --verify-no-changes` MUST pass.
3. **Test gate**: `dotnet test` MUST pass, including the tests mandated by Principle V.
4. **Run gate** (from `ProjectGoals.md` local checklist): backend listens on its configured
   port; frontend loads; `ApiBaseUrl` matches the backend; CORS allows the frontend origin;
   browser DevTools shows no connection or CORS errors.
5. **Phase acceptance**: MVP is accepted only when a pasted URL appears in the list without a
   page reload. Extended-MVP is accepted only when refreshing
   `https://devblogs.microsoft.com/dotnet/feed/` displays item titles as working links, and an
   unreachable URL shows "Failed to load feed" without breaking the page.
6. **Review checklist**: every change MUST be checked against Principles I–V; violations MUST
   be either fixed or recorded with justification in the plan's Complexity Tracking table.

## Governance

- This constitution supersedes ad-hoc practices and any conflicting guidance in generated
  plans, specs, or tasks. `StakeholderDocuments/` define *what* to build; this document defines
  *how* it must be built. Where they conflict, the conflict MUST be raised and resolved by
  amending one of them, not silently ignored.
- `/speckit-plan` MUST perform a Constitution Check against Principles I–V before design and
  again after design; unresolved violations block `/speckit-tasks`.
- **Amendments** require: a written change describing the motivation, an updated Sync Impact
  Report, a version bump, and a review of dependent Spec Kit templates for consistency.
- **Versioning** follows semantic versioning:
  - MAJOR: removing or redefining a principle in a backward-incompatible way.
  - MINOR: adding a principle or section, or materially expanding guidance.
  - PATCH: clarifications, wording, or typo fixes with no change in meaning.
- **Compliance review**: at each phase boundary (MVP → Extended-MVP → Post-MVP), the team MUST
  re-read this constitution and confirm the phase-specific rules (Principles I and III) still
  match the stakeholder documents.

**Version**: 1.0.0 | **Ratified**: 2026-10-23 | **Last Amended**: 2026-10-23
