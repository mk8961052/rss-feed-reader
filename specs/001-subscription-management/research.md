# Research: Feed Subscription Management (MVP)

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-10-23

Each entry records a decision, why it was made, and what else was considered.

## R1. .NET version and SDK pin

- **Decision**: Target **.NET 10 (LTS)** for all projects (`net10.0`). Pin it in a root
  `global.json` with `"version": "10.0.100", "rollForward": "latestFeature"`.
- **Rationale**: .NET 10 is the current LTS release, supported until November 2028. The
  workstation currently has only SDK `6.0.400`. .NET 6 has been out of support since November
  2024 and must not be used for new code. The stakeholder docs' template cleanup steps
  (`Home.razor`, `Counter.razor`, `Weather.razor`, `NotFound.razor`) match the .NET 8+/10 Blazor
  WebAssembly template. The .NET 6 template had `Index.razor`/`FetchData.razor` instead.
- **Alternatives considered**: .NET 8 LTS (support ends November 2026, about a month away,
  so rejected). .NET 9 STS (shorter support window, no benefit here).
- **Prerequisite**: install the .NET 10 SDK before starting implementation (see
  [quickstart.md](quickstart.md)).

## R2. Backend API style

- **Decision**: ASP.NET Core **Minimal APIs**, using a `MapGroup("/api/subscriptions")` route
  group and `TypedResults`. Endpoints live in `Endpoints/SubscriptionEndpoints.cs`, not inline
  in `Program.cs`.
- **Rationale**: Two endpoints don't justify controllers. `TypedResults` gives compile-checked
  response types, which keeps the contract explicit and makes integration tests easy.
- **Alternatives considered**: MVC controllers (more ceremony, no benefit at this size).

## R3. Project template options

- **Decision**: Create the API with `dotnet new webapi --no-openapi` (Minimal API is the
  default) and the UI with `dotnet new blazorwasm` (standalone, no hosting).
- **Rationale**: Constitution Principle I says every package must be traceable to a
  current-phase requirement. OpenAPI/Swagger isn't required by the spec. The contract is
  documented in [contracts/subscriptions-api.md](contracts/subscriptions-api.md).
- **Alternatives considered**: Keeping OpenAPI (deferred; easy to add later). Hosted Blazor
  (contradicts the TechStack separation of frontend and backend).

## R4. Sharing DTOs between UI and API

- **Decision**: **Duplicate** the two small DTO records (`AddSubscriptionRequest`,
  `SubscriptionResponse`) in the API and the UI. Compatibility is enforced by the API
  integration tests, which assert the JSON shape documented in the contract.
- **Rationale**: Avoids a fourth project. The constitution layout names only backend, frontend
  and tests, and Principle I forbids speculative structure. Two records are trivially kept in
  sync.
- **Alternatives considered**: A `shared/RSSFeedReader.Contracts` class library. Revisit it
  in Extended-MVP if the number of DTOs grows (it would need a constitution layout amendment or
  a Complexity Tracking entry).

## R5. URL validation (FR-002, FR-004) and duplicate key (FR-005)

- **Decision**: A static, pure `SubscriptionUrlValidator.Validate(string? input)` in the API
  returns either a validated result (trimmed display URL and normalized key) or an error code.
  Rules, in order:
  1. null/whitespace → `Empty`
  2. trimmed length > 2048 → `TooLong`
  3. `Uri.TryCreate(trimmed, UriKind.Absolute, out uri)` fails, or `uri.Scheme` is not
     `Uri.UriSchemeHttp`/`Uri.UriSchemeHttps` → `NotWebAddress`

  Duplicate key = `uri.AbsoluteUri`, compared with `StringComparison.Ordinal`.
- **Rationale**: `Uri` canonicalizes scheme and host to lowercase, so `https://EXAMPLE.com/feed`
  and `https://example.com/feed` produce the same key, while path and query case is kept
  (paths are case-sensitive on many servers). It also drops default ports (`:443`), which is an
  acceptable extra normalization. A pure function is easy to unit-test (Principle V).
- **Alternatives considered**: Regex validation (fragile and hard to read). Case-insensitive
  whole-URL comparison (would wrongly merge distinct feeds that differ only in path case).

## R6. Thread-safe in-memory store

- **Decision**: `ISubscriptionStore` with `IReadOnlyList<Subscription> GetAll()` and
  `AddResult TryAdd(Subscription)`. `InMemorySubscriptionStore` is a singleton, backed by a
  `List<Subscription>` (insertion order) plus a `HashSet<string>` of normalized keys, guarded by
  a single `lock`. `GetAll` returns a snapshot copy.
- **Rationale**: Checking for a duplicate and adding must be atomic so that a double-submit
  race can't create two entries (FR-012, edge cases). A `ConcurrentDictionary` alone doesn't
  preserve insertion order (FR-007). A lock is simple and contention is negligible for one
  user. The interface lets EF Core replace this post-MVP with a DI registration change
  (Principle IV).
- **Alternatives considered**: `ConcurrentDictionary` plus a separate order counter (more
  moving parts). `ImmutableList` with `Interlocked.CompareExchange` (more complex than a lock).

## R7. Error responses

- **Decision**: `builder.Services.AddProblemDetails()` plus `app.UseExceptionHandler()` in all
  environments. Validation failures return `400` `ValidationProblem` with the error under the
  `url` key; duplicates return `409 Conflict` `Problem`. Messages are fixed, user-facing
  strings from the contract. No exception text reaches the client.
- **Rationale**: Constitution Principle II (no internal details to the client). The UI can show
  `detail`/`errors.url[0]` directly. Using 409 for duplicates lets the UI and tests tell them
  apart from malformed input.
- **Alternatives considered**: Returning 200 with an error flag (non-idiomatic, hides
  failures). The developer exception page (leaks stack traces).

## R8. Timestamps

- **Decision**: Inject `TimeProvider` (registered as `TimeProvider.System`) and stamp
  `AddedAt` as `DateTimeOffset` in UTC.
- **Rationale**: Deterministic tests use `FakeTimeProvider`, or simply ordering assertions.
  The list orders by insertion, so `AddedAt` is informational and future-proof for persistence.
- **Alternatives considered**: `DateTime.UtcNow` directly (not testable).

## R9. Frontend structure and API calls

- **Decision**:
  - A single routable page `Pages/Subscriptions.razor` (`@page "/"`).
  - A `Services/SubscriptionsApiClient.cs` wrapping `HttpClient` (registered scoped, with
    `BaseAddress` from `ApiBaseUrl` as shown in TechStack.md). It returns a small result type:
    `Success` / `Rejected(message)` / `Unavailable`.
  - The page uses a `<form @onsubmit>` so Enter and the button both submit (FR-001).
  - An `isSubmitting` flag disables the button (FR-012).
  - After a successful add, the page reloads the list via `GET` (Principle IV: the API is the
    source of truth).
- **Rationale**: Keeps HTTP and error mapping out of the component (Principle IV). Plain
  `HttpClient` is what the WebAssembly template already registers. `IHttpClientFactory` is
  mandated only for backend outbound feed fetching (Principle III), which arrives in
  Extended-MVP.
- **Alternatives considered**: `EditForm` with DataAnnotations (validation would be duplicated
  client-side and drift from server rules). Optimistic append of the posted item (violates the
  source-of-truth rule).

## R10. Rendering safety

- **Decision**: Render each URL as `<li>@subscription.Url</li>`, using Razor's default
  encoding, with no `MarkupString`. URLs are **not** rendered as links in the MVP (spec
  Assumptions).
- **Rationale**: Satisfies FR-009 and Principle II with no extra code. When links are added
  later, the render-time scheme check from Principle II applies.

## R11. CORS and configuration

- **Decision**: Backend `appsettings.json` contains `"Cors": { "AllowedOrigins":
  ["http://localhost:5213", "https://localhost:7025"] }`, bound and applied with
  `WithOrigins(...)`, `WithHeaders("Content-Type")`, `WithMethods("GET", "POST")`. Frontend
  `wwwroot/appsettings.json` contains `"ApiBaseUrl": "http://localhost:5151/api/"`.
- **Rationale**: TechStack port contract and constitution CORS rules (explicit origins, no
  `AllowAnyOrigin`, no credentials). Narrow headers and methods reflect exactly what the MVP
  uses.
- **Alternatives considered**: `AllowAnyHeader/AllowAnyMethod` (broader than needed).

## R12. Testing approach

- **Decision**: One test project, `tests/RSSFeedReader.Api.Tests` (xUnit), with:
  - **Unit tests**: `SubscriptionUrlValidator` (each rule plus the `javascript:`, `ftp:`,
    markup-character and case-folding cases) and `InMemorySubscriptionStore` (order,
    duplicate rejection, concurrent `TryAdd` of the same key yields exactly one entry).
  - **Integration tests**: `WebApplicationFactory<Program>` covering every contract scenario
    (201/400/409/GET order and shape) plus a CORS preflight from an allowed and a disallowed
    origin.

  UI verification is manual via [quickstart.md](quickstart.md).
- **Rationale**: Covers the constitution-mandated tests (Principle V) plus the SHOULD-level
  endpoint tests. A component-testing library (bUnit) isn't in the approved list and the UI
  logic is thin.
- **Alternatives considered**: bUnit UI tests (deferred), Playwright E2E tests (too heavy for
  a POC).

## R13. Build quality settings

- **Decision**: A root `Directory.Build.props` sets `Nullable`, `ImplicitUsings`,
  `TreatWarningsAsErrors`, `AnalysisLevel=latest-recommended` and
  `EnforceCodeStyleInBuild=true` for every project. A root `.editorconfig` defines style.
  Solution file: `RSSFeedReader.slnx` (the .NET 10 default format).
- **Rationale**: One place to enforce Principle V across all three projects.
