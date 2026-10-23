# Implementation Plan: Feed Subscription Management (MVP)

**Branch**: `001-subscription-management` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-subscription-management/spec.md`

**Delivery Phase**: MVP (Constitution Principle I)

## Summary

Let a single local user add RSS/Atom feed subscriptions by URL and see them listed. An
ASP.NET Core Minimal API (`backend/RSSFeedReader.Api`) exposes `GET` and `POST
/api/subscriptions`. It validates URL structure (absolute `http`/`https`, ≤ 2048 chars),
rejects duplicates by normalized URL, and stores subscriptions in a thread-safe in-memory
singleton behind `ISubscriptionStore`. A standalone Blazor WebAssembly app
(`frontend/RSSFeedReader.UI`) provides one page at `/` with an input, an Add button (Enter
also submits) and the list. It calls the API through a small client service and always
re-reads the list from the API. No feed fetching or parsing (Extended-MVP).

## Technical Context

**Language/Version**: C# on .NET 10 (LTS), SDK pinned via `global.json` (research R1)

**Primary Dependencies**: ASP.NET Core Minimal APIs; Blazor WebAssembly (standalone). No
additional NuGet packages in the app projects. Tests: xUnit, `Microsoft.AspNetCore.Mvc.Testing`.

**Storage**: In-memory singleton (`InMemorySubscriptionStore`, lock-guarded), lost on API
restart (FR-010)

**Testing**: xUnit unit and integration tests (`WebApplicationFactory<Program>`) in
`tests/RSSFeedReader.Api.Tests`. Manual UI validation via [quickstart.md](quickstart.md).

**Target Platform**: Local development on Windows, macOS and Linux. API on Kestrel
`http://localhost:5151`. UI on `http://localhost:5213` / `https://localhost:7025`.

**Project Type**: Web application (separate backend API and WebAssembly frontend)

**Performance Goals**: An added subscription is visible within 1 s (SC-002), and this holds
with 100+ subscriptions (SC-004). Both are trivially met in memory.

**Constraints**: No feed fetching, parsing or persistence (FR-013, Principle I). URL ≤ 2048
chars. No exception details sent to the client. CORS limited to explicit origins.

**Scale/Scope**: 1 user, 1 page, 2 endpoints, roughly hundreds of subscriptions at most

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Pre-design | Post-design |
|-----------|------|------------|-------------|
| I. Phase-Gated Scope | Feature is labeled MVP; no `HttpClient` feed fetching, Syndication, EF Core or background services; every package is traceable | ✅ | ✅ The OpenAPI package is dropped (R3); only test packages are added |
| II. Untrusted Input, Safe Output | Backend rejects empty, > 2048 or non-http(s) URLs with 400 `ProblemDetails`; UI shows the message; no `MarkupString`; no exception leakage | ✅ | ✅ Validator (R5), ProblemDetails plus exception handler (R7), encoded text rendering with no links (R10) |
| III. Safe Outbound Fetching | Applies from Extended-MVP | N/A | N/A (no outbound calls) |
| IV. Swappable Boundaries | API is the source of truth; `/api/` JSON DTOs; `ISubscriptionStore` singleton, thread-safe; component calls go through an injected client service | ✅ | ✅ Lock-guarded store (R6), DTO mapping excludes `NormalizedUrl`, UI re-GETs after POST (R9) |
| V. Code Quality | Nullable, warnings as errors, analyzers, `dotnet format`; async; no magic values; xUnit tests for validator and store | ✅ | ✅ `Directory.Build.props` (R13); limits and messages as named constants; ports and origins in config (R11); test plan (R12) |
| Tech constraints | `global.json` pin; layout `backend/`, `frontend/`, `tests/`; single solution; config-driven `ApiBaseUrl` and CORS | ✅ | ✅ |
| Workflow gates | Template cleanup gate; build/format/test/run gates; MVP acceptance | ✅ | ✅ Encoded in [quickstart.md](quickstart.md) |

**Result**: PASS, with no violations and no Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/001-subscription-management/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R13
├── data-model.md        # Phase 1: entity, validation and DTOs
├── quickstart.md        # Phase 1: build/run/acceptance guide
├── contracts/
│   └── subscriptions-api.md   # Phase 1: HTTP + UI behavior contract
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
global.json                      # SDK pin 10.0.100, rollForward latestFeature
Directory.Build.props            # Nullable, ImplicitUsings, TreatWarningsAsErrors, AnalysisLevel
.editorconfig                    # Single style authority
RSSFeedReader.slnx               # All three projects

backend/RSSFeedReader.Api/
├── Program.cs                   # DI, ProblemDetails, exception handler, CORS, MapSubscriptionEndpoints
├── appsettings.json             # Cors:AllowedOrigins
├── Properties/launchSettings.json   # http://localhost:5151
├── Contracts/
│   ├── AddSubscriptionRequest.cs
│   └── SubscriptionResponse.cs
├── Domain/
│   └── Subscription.cs          # sealed record
├── Validation/
│   └── SubscriptionUrlValidator.cs  # pure; MaxUrlLength = 2048, message constants
├── Storage/
│   ├── ISubscriptionStore.cs
│   ├── AddResult.cs
│   └── InMemorySubscriptionStore.cs # singleton, lock-guarded
└── Endpoints/
    └── SubscriptionEndpoints.cs # MapGroup("/api/subscriptions"): GET, POST

frontend/RSSFeedReader.UI/
├── Program.cs                   # HttpClient BaseAddress from ApiBaseUrl; SubscriptionsApiClient
├── wwwroot/appsettings.json     # ApiBaseUrl = http://localhost:5151/api/
├── Properties/launchSettings.json   # http 5213, https 7025
├── Models/
│   ├── AddSubscriptionRequest.cs
│   └── SubscriptionResponse.cs
├── Services/
│   ├── SubscriptionsApiClient.cs
│   └── AddSubscriptionResult.cs # Success | Rejected(message) | Unavailable
├── Pages/
│   ├── Subscriptions.razor      # @page "/"
│   └── NotFound.razor           # kept from template
└── Layout/
    └── NavMenu.razor            # single "Subscriptions" link
    # Removed: Pages/Home.razor, Pages/Counter.razor, Pages/Weather.razor, wwwroot/sample-data/

tests/RSSFeedReader.Api.Tests/
├── Validation/SubscriptionUrlValidatorTests.cs
├── Storage/InMemorySubscriptionStoreTests.cs
└── Endpoints/SubscriptionEndpointsTests.cs  # WebApplicationFactory<Program>: 201/400/409/GET/CORS
```

**Structure Decision**: Web application layout as mandated by the constitution (Technology &
Configuration Constraints): `backend/RSSFeedReader.Api`, `frontend/RSSFeedReader.UI`, and one
test project under `tests/`, all in `RSSFeedReader.slnx`. There is no shared contracts project
in the MVP: the DTOs are duplicated (research R4) and guarded by integration tests.
`Program` in the API needs `public partial class Program;` so `WebApplicationFactory` can see it.

## Complexity Tracking

No constitution violations. Nothing to justify.
