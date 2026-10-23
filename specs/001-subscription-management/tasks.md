---

description: "Task list for Feed Subscription Management (MVP)"
---

# Tasks: Feed Subscription Management (MVP)

**Input**: Design documents from `/specs/001-subscription-management/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/subscriptions-api.md](contracts/subscriptions-api.md),
[quickstart.md](quickstart.md)

**Tests**: INCLUDED. Constitution Principle V requires xUnit tests for the URL validator and
the in-memory store from the MVP onward, and recommends (SHOULD) endpoint integration tests
for every `/api/` route. Write the tests in each section first and confirm they fail before
implementing.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: The user story the task belongs to (US1, US2, US3)

## Path Conventions

- Backend: `backend/RSSFeedReader.Api/`
- Frontend: `frontend/RSSFeedReader.UI/`
- Tests: `tests/RSSFeedReader.Api.Tests/`
- Root files: `global.json`, `Directory.Build.props`, `.editorconfig`, `RSSFeedReader.slnx`

## Global Rules (apply to every task)

- Target framework `net10.0`. App projects get no NuGet packages beyond the template's
  framework references (Constitution Principle I). The test project may add
  `Microsoft.AspNetCore.Mvc.Testing` only.
- No `MarkupString`, `.Result`, `.Wait()`, or `async void` (outside Blazor event handlers). Pass
  `CancellationToken` through async APIs.
- Limits, messages, ports and URLs are named constants or configuration, never inline literals.
- User-facing messages MUST match [contracts/subscriptions-api.md](contracts/subscriptions-api.md) verbatim.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Toolchain, solution, and the three projects building cleanly with zero warnings

- [ ] T001 Verify the .NET 10 SDK is installed (`dotnet --list-sdks` shows `10.0.x`; only
  6.0.400 was present at planning time, so install .NET 10 if needed). Then create
  `global.json` at the repo root containing `{"sdk": {"version": "10.0.100", "rollForward": "latestFeature"}}`
- [ ] T002 Create `Directory.Build.props` at the repo root setting, for all projects:
  `<Nullable>enable</Nullable>`, `<ImplicitUsings>enable</ImplicitUsings>`,
  `<TreatWarningsAsErrors>true</TreatWarningsAsErrors>`,
  `<AnalysisLevel>latest-recommended</AnalysisLevel>`,
  `<EnforceCodeStyleInBuild>true</EnforceCodeStyleInBuild>`
- [ ] T003 [P] Create `.editorconfig` at the repo root (`root = true`; C# conventions: 4-space
  indent, file-scoped namespaces, `var` when the type is apparent, `_camelCase` private fields,
  `System` usings first, LF-agnostic `end_of_line` unset, `insert_final_newline = true`)
- [ ] T004 [P] Create `.gitignore` at the repo root via `dotnet new gitignore`
- [ ] T005 Create the API project:
  `dotnet new webapi --no-openapi -n RSSFeedReader.Api -o backend/RSSFeedReader.Api -f net10.0`.
  Then strip the WeatherForecast sample from `backend/RSSFeedReader.Api/Program.cs`, delete
  `backend/RSSFeedReader.Api/RSSFeedReader.Api.http`, and confirm the `.csproj` has no
  `PackageReference`
- [ ] T006 [P] Create the UI project:
  `dotnet new blazorwasm -n RSSFeedReader.UI -o frontend/RSSFeedReader.UI -f net10.0` (standalone)
- [ ] T007 [P] Create the test project:
  `dotnet new xunit -n RSSFeedReader.Api.Tests -o tests/RSSFeedReader.Api.Tests -f net10.0`.
  Add `dotnet add tests/RSSFeedReader.Api.Tests package Microsoft.AspNetCore.Mvc.Testing` and a
  project reference to `backend/RSSFeedReader.Api/RSSFeedReader.Api.csproj`. Delete the
  template `UnitTest1.cs`
- [ ] T008 Create the solution `RSSFeedReader.slnx` at the repo root (`dotnet new sln -n RSSFeedReader`)
  and `dotnet sln add` all three `.csproj` files
- [ ] T009 Set `backend/RSSFeedReader.Api/Properties/launchSettings.json` to a single `http`
  profile with `"applicationUrl": "http://localhost:5151"` and `"launchBrowser": false`
- [ ] T010 [P] Set `frontend/RSSFeedReader.UI/Properties/launchSettings.json` profiles: `http`
  → `"http://localhost:5213"`, `https` → `"https://localhost:7025;http://localhost:5213"`
- [ ] T011 Run `dotnet build RSSFeedReader.slnx` and fix any template-generated warnings until
  the result is 0 warnings and 0 errors

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Template cleanup gate, configuration, error handling, domain type, store,
validator, DTOs, and empty endpoint and client shells that every story builds on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete. T012–T014 form the
constitution's **blocking template-cleanup gate**.

- [ ] T012 Delete `frontend/RSSFeedReader.UI/Pages/Home.razor`, `Pages/Counter.razor`,
  `Pages/Weather.razor` and the `wwwroot/sample-data/` folder. Rewrite
  `frontend/RSSFeedReader.UI/Layout/NavMenu.razor` so it has exactly one `NavLink`
  (`href=""`, `Match="NavLinkMatch.All"`, text "Subscriptions") and set the brand text to
  "RSS Feed Reader"
- [ ] T013 Create `frontend/RSSFeedReader.UI/Pages/Subscriptions.razor` with `@page "/"`,
  `<PageTitle>Subscriptions</PageTitle>` and an `<h1>Subscriptions</h1>` placeholder
- [ ] T014 Verify the cleanup gate: `Get-ChildItem frontend/RSSFeedReader.UI/Pages -Filter *.razor`
  lists only `NotFound.razor` and `Subscriptions.razor`, and
  `Select-String -Path frontend/RSSFeedReader.UI/Pages/*.razor -Pattern '@page "/"'` returns
  exactly one match. Then `dotnet clean` and `dotnet build`
  `frontend/RSSFeedReader.UI/RSSFeedReader.UI.csproj`, run it, and confirm the browser console
  (F12) has no "ambiguous route" error. **STOP if this fails.**
- [ ] T015 [P] Create/replace `frontend/RSSFeedReader.UI/wwwroot/appsettings.json` with
  `{"ApiBaseUrl": "http://localhost:5151/api/"}`. In `frontend/RSSFeedReader.UI/Program.cs`,
  read `builder.Configuration["ApiBaseUrl"]`, throw `InvalidOperationException("ApiBaseUrl is not configured.")`
  if it is missing (no hardcoded fallback URL), and register
  `builder.Services.AddScoped(sp => new HttpClient { BaseAddress = new Uri(apiBaseUrl) })`
- [ ] T016 [P] Add `"Cors": {"AllowedOrigins": ["http://localhost:5213", "https://localhost:7025"]}`
  to `backend/RSSFeedReader.Api/appsettings.json`. In `backend/RSSFeedReader.Api/Program.cs`,
  bind `Cors:AllowedOrigins` to `string[]`, throw at startup if it is empty, and register a
  default CORS policy using `.WithOrigins(origins).WithHeaders("Content-Type").WithMethods("GET", "POST")`
  (never `AllowAnyOrigin`/`AllowCredentials`), followed by `app.UseCors()`
- [ ] T017 In `backend/RSSFeedReader.Api/Program.cs`, add `builder.Services.AddProblemDetails()`
  and `app.UseExceptionHandler()` unconditionally (no developer exception page, so no stack
  traces reach clients), register `builder.Services.AddSingleton(TimeProvider.System)`, and
  append `public partial class Program;` at the end of the file for `WebApplicationFactory`
  (depends on T016, same file)
- [ ] T018 [P] Create `backend/RSSFeedReader.Api/Domain/Subscription.cs`:
  `public sealed record Subscription(Guid Id, string Url, string NormalizedUrl, DateTimeOffset AddedAt);`.
  Doc comments: `Url` is "user input with surrounding whitespace trimmed; 1–2048 chars;
  absolute http/https URI"; `NormalizedUrl` is "`Uri.AbsoluteUri` of `Url`; unique across the
  store; never serialized to clients"; `AddedAt` is UTC
- [ ] T019 [P] Create `backend/RSSFeedReader.Api/Storage/AddResult.cs`
  (`public enum AddResult { Added, Duplicate }`) and `backend/RSSFeedReader.Api/Storage/ISubscriptionStore.cs`
  with `IReadOnlyList<Subscription> GetAll();` (oldest first) and `AddResult TryAdd(Subscription subscription);`
- [ ] T020 [P] Write the failing unit tests in `tests/RSSFeedReader.Api.Tests/Storage/InMemorySubscriptionStoreTests.cs`:
  a new store returns an empty list; three adds are returned in insertion order; adding a
  second subscription with the same `NormalizedUrl` returns `Duplicate` and leaves the count
  unchanged; a list returned by `GetAll()` doesn't change after a later `TryAdd` (snapshot); 50
  parallel `TryAdd` calls (`Parallel.For`) with the same `NormalizedUrl` produce exactly one
  `Added` and one stored item
- [ ] T021 Implement `backend/RSSFeedReader.Api/Storage/InMemorySubscriptionStore.cs`
  (`sealed`, implements `ISubscriptionStore`). Use a private `List<Subscription>` for order and
  a `HashSet<string>` (ordinal) of `NormalizedUrl`, both guarded by one `private readonly Lock _gate = new();`.
  `TryAdd` checks the set and adds to both inside the lock; `GetAll` returns `_items.ToArray()`
  inside the lock. Register it with `builder.Services.AddSingleton<ISubscriptionStore, InMemorySubscriptionStore>()`
  in `backend/RSSFeedReader.Api/Program.cs`. Make T020 pass
- [ ] T022 [P] Write the failing unit tests in `tests/RSSFeedReader.Api.Tests/Validation/SubscriptionUrlValidatorTests.cs`.
  `[Theory]` cases:
  - `null`, `""`, `"   "` → Empty, "Enter a feed address."
  - a 2049-char `https://example.com/…` URL → TooLong, "The feed address must be 2048 characters or fewer."
  - exactly 2048 chars → Valid
  - `not a url`, `ftp://example.com/feed`, `javascript:alert(1)`, `/relative/path`,
    `mailto:a@b.c` → NotWebAddress, "Enter a web address starting with http:// or https://."
  - `"  https://example.org/rss  "` → Valid with `Url == "https://example.org/rss"`
  - `https://EXAMPLE.com/feed` and `https://example.com/feed` have the same `NormalizedUrl`
  - `https://example.com/Feed` has a different `NormalizedUrl` from `https://example.com/feed`
  - `https://example.com:443/feed` normalizes like `https://example.com/feed`
  - `https://example.com/?q=<b>x</b>` → Valid
- [ ] T023 Implement `backend/RSSFeedReader.Api/Validation/SubscriptionUrlValidator.cs` as a
  `static` class with `public const int MaxUrlLength = 2048;`, `public const string` message
  constants `EmptyMessage`, `TooLongMessage` and `NotWebAddressMessage` (exact texts from T022),
  and `public static UrlValidationResult Validate(string? input)`. Checks run in order:
  null/whitespace → Empty; `trimmed.Length > MaxUrlLength` → TooLong;
  `!Uri.TryCreate(trimmed, UriKind.Absolute, out var uri)` or scheme not
  `Uri.UriSchemeHttp`/`Uri.UriSchemeHttps` → NotWebAddress; otherwise Valid with
  `Url = trimmed` and `NormalizedUrl = uri.AbsoluteUri`. Define `UrlValidationResult` (record
  with `Outcome`, `Url`, `NormalizedUrl`, `ErrorMessage`) and the `UrlValidationOutcome` enum
  (`Valid`, `Empty`, `TooLong`, `NotWebAddress`) in
  `backend/RSSFeedReader.Api/Validation/UrlValidationResult.cs`. Make T022 pass
- [ ] T024 [P] Create the API DTOs: `backend/RSSFeedReader.Api/Contracts/AddSubscriptionRequest.cs`
  (`public sealed record AddSubscriptionRequest(string? Url);`) and
  `backend/RSSFeedReader.Api/Contracts/SubscriptionResponse.cs`
  (`public sealed record SubscriptionResponse(Guid Id, string Url, DateTimeOffset AddedAt)` with
  `static SubscriptionResponse From(Subscription s)` that does **not** expose `NormalizedUrl`)
- [ ] T025 [P] Create the mirrored UI DTOs: `frontend/RSSFeedReader.UI/Models/AddSubscriptionRequest.cs`
  (`sealed record AddSubscriptionRequest(string? Url)`) and
  `frontend/RSSFeedReader.UI/Models/SubscriptionResponse.cs`
  (`sealed record SubscriptionResponse(Guid Id, string Url, DateTimeOffset AddedAt)`)
- [ ] T026 Create `backend/RSSFeedReader.Api/Endpoints/SubscriptionEndpoints.cs` with
  `public static class SubscriptionEndpoints` and `public static IEndpointRouteBuilder MapSubscriptionEndpoints(this IEndpointRouteBuilder app)`,
  which creates `app.MapGroup("/api/subscriptions")` with no routes yet. Call
  `app.MapSubscriptionEndpoints()` from `backend/RSSFeedReader.Api/Program.cs` after `UseCors()`
- [ ] T027 Create `frontend/RSSFeedReader.UI/Services/AddSubscriptionResult.cs`: an abstract
  record with nested `Success`, `Rejected(string Message)` and `Unavailable` cases. Create
  `frontend/RSSFeedReader.UI/Services/SubscriptionsApiClient.cs` (`sealed`, constructor takes
  `HttpClient`, `private const string SubscriptionsPath = "subscriptions";`, no methods yet) and
  register it with `builder.Services.AddScoped<SubscriptionsApiClient>()` in
  `frontend/RSSFeedReader.UI/Program.cs`
- [ ] T028 Run `dotnet build RSSFeedReader.slnx` (0 warnings) and `dotnet test` (T020 and T022 green)

**Checkpoint**: The foundation is ready, the cleanup gate has passed, and the validator and
store are tested. User stories can begin.

---

## Phase 3: User Story 1 - Add a feed subscription by URL (Priority: P1) 🎯 MVP

**Goal**: The user pastes a feed URL, presses Add or Enter, and sees it appear in the list
immediately, with the input cleared.

**Independent Test**: Open `http://localhost:5213`, add `https://devblogs.microsoft.com/dotnet/feed/`,
and confirm it appears in the list within 1 s without a page reload (quickstart rows 2, 3, 4, 12).

### Tests for User Story 1 ⚠️ (write first, confirm they fail)

- [ ] T029 [P] [US1] Create `tests/RSSFeedReader.Api.Tests/Endpoints/SubscriptionEndpointsTests.cs`.
  Each test creates its **own** `new WebApplicationFactory<Program>()` (disposed) so the
  singleton store is isolated. Add tests:
  - POST `{"url":"  https://devblogs.microsoft.com/dotnet/feed/  "}` → `201 Created`, the body
    `url` is the trimmed value, `id` is a non-empty Guid, and the `Location` header is
    `/api/subscriptions/{id}`
  - POST then GET → the GET array contains exactly that one subscription
  - Two concurrent POSTs of the same new URL (`Task.WhenAll`) → one 201 and one 409, and GET
    returns exactly one item

### Implementation for User Story 1

- [ ] T030 [US1] Add `POST /` to the group in `backend/RSSFeedReader.Api/Endpoints/SubscriptionEndpoints.cs`.
  The handler is `static Results<Created<SubscriptionResponse>, ValidationProblem, ProblemHttpResult> AddSubscription(AddSubscriptionRequest? request, ISubscriptionStore store, TimeProvider time)`:
  1. Run `SubscriptionUrlValidator.Validate(request?.Url)`. If it isn't valid →
     `TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["url"] = [result.ErrorMessage] })`.
  2. Otherwise build `new Subscription(Guid.NewGuid(), result.Url, result.NormalizedUrl, time.GetUtcNow())`.
  3. `store.TryAdd` → `Duplicate` → `TypedResults.Problem(title: "Already subscribed", detail: "This feed is already in your subscription list.", statusCode: StatusCodes.Status409Conflict)`
     (keep the title and detail as named constants).
  4. `Added` → `TypedResults.Created($"/api/subscriptions/{id}", SubscriptionResponse.From(subscription))`.
- [ ] T031 [US1] Add `GET /` to the same group in `backend/RSSFeedReader.Api/Endpoints/SubscriptionEndpoints.cs`,
  returning `TypedResults.Ok(store.GetAll().Select(SubscriptionResponse.From).ToArray())`
  (always an array, never null). T029 must pass
- [ ] T032 [US1] Implement two methods in `frontend/RSSFeedReader.UI/Services/SubscriptionsApiClient.cs`:
  - `Task<IReadOnlyList<SubscriptionResponse>?> GetSubscriptionsAsync(CancellationToken ct)`:
    `GetFromJsonAsync<SubscriptionResponse[]>(SubscriptionsPath, ct)`. Returns `null` on
    `HttpRequestException`, a non-success status, or `JsonException`.
  - `Task<AddSubscriptionResult> AddSubscriptionAsync(string url, CancellationToken ct)`:
    `PostAsJsonAsync(SubscriptionsPath, new AddSubscriptionRequest(url), ct)`. Returns
    `Success` on 201. For now, maps any other outcome, including exceptions, to `Unavailable`
    (refined in T042).
- [ ] T033 [US1] Implement the add flow in `frontend/RSSFeedReader.UI/Pages/Subscriptions.razor`:
  - Inject `SubscriptionsApiClient`.
  - A `<form @onsubmit="AddAsync">` contains a `<label>`-associated `<input type="text">`
    bound to `_newUrl` (`@bind:event="oninput"`, placeholder "Paste a feed URL") and
    `<button type="submit" disabled="@_isSubmitting">Add</button>`. Enter submits through the
    form (FR-001).
  - `AddAsync` sets `_isSubmitting = true` and returns early if it is already submitting
    (FR-012). It calls `AddSubscriptionAsync(_newUrl, ...)`. On `Success`, it clears `_newUrl`
    and reloads `_subscriptions` via `GetSubscriptionsAsync` (the API is the source of truth;
    no optimistic append). It resets `_isSubmitting` in `finally`.
  - Render `_subscriptions` as `<ul>` with `<li>@subscription.Url</li>`, as plain encoded text
    (no `MarkupString`, no `<a>`) (FR-009).
- [ ] T034 [US1] Validate manually using [quickstart.md](quickstart.md) section 4, rows 2, 3, 4
  and 12, with both apps running

**Checkpoint**: User Story 1 works end to end. This is the demonstrable MVP core.

---

## Phase 4: User Story 2 - View the list of subscriptions (Priority: P2)

**Goal**: On opening the app, the user sees all current subscriptions oldest first, or a clear
empty state, and the list survives a browser refresh while the API is running.

**Independent Test**: Open the app with no data and see "No subscriptions yet". Add three
subscriptions, press F5, and see all three in the same order (quickstart rows 1, 9, 11).

### Tests for User Story 2 ⚠️ (write first, confirm they fail)

- [ ] T035 [P] [US2] Add tests to `tests/RSSFeedReader.Api.Tests/Endpoints/SubscriptionEndpointsTests.cs`:
  - GET on a fresh factory → `200` with JSON body exactly `[]`
  - POST `https://a.example/feed`, `https://b.example/feed` and `https://c.example/feed` → GET
    returns them in that order
  - The raw GET JSON contains no `normalizedUrl` property, and every item has `id`, `url` and
    `addedAt` (camelCase)

### Implementation for User Story 2

- [ ] T036 [US2] In `frontend/RSSFeedReader.UI/Pages/Subscriptions.razor`, implement
  `OnInitializedAsync`. It loads the list via `GetSubscriptionsAsync` and tracks `_isLoading`
  and `_loadFailed`, then renders exactly one of:
  - "Loading…" while loading
  - "Could not load subscriptions." when the result is `null`
  - "No subscriptions yet" when the list is empty (FR-008)
  - otherwise the `<ul>` from T033, in API order (oldest first, FR-007)
- [ ] T037 [P] [US2] Create `frontend/RSSFeedReader.UI/Pages/Subscriptions.razor.css`. The list
  container gets `max-height: 60vh; overflow-y: auto;` and list items get
  `overflow-wrap: anywhere;` so 100+ entries and 2048-char URLs stay usable (SC-004). Keep
  styling minimal and functional
- [ ] T038 [US2] Validate manually using [quickstart.md](quickstart.md) rows 1, 9 and 11, plus
  add 100 subscriptions with
  `1..100 | % { Invoke-RestMethod http://localhost:5151/api/subscriptions -Method Post -ContentType application/json -Body "{`"url`":`"https://example.com/feed/$_`"}" }`,
  reload the page, and confirm the list
  scrolls and a new add still appears within 1 s

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Be told when an entry cannot be added (Priority: P3)

**Goal**: Empty, non-web, over-long and duplicate inputs are refused with a specific message,
the typed text stays in the field, and a down API shows a generic message.

**Independent Test**: Submit an empty value, `javascript:alert(1)`, `not a url` and a
duplicate in different letter case. Each is refused with its contract message and the list is
unchanged (quickstart rows 5–8 and 10).

### Tests for User Story 3 ⚠️ (write first, confirm they fail)

- [ ] T039 [P] [US3] Add tests to `tests/RSSFeedReader.Api.Tests/Endpoints/SubscriptionEndpointsTests.cs`:
  - A `[Theory]` over the request bodies `{}`, `{"url":""}`, `{"url":"   "}`,
    `{"url":"<2049 chars>"}`, `{"url":"not a url"}`, `{"url":"ftp://example.com/feed"}`,
    `{"url":"javascript:alert(1)"}` and `{"url":"/relative/path"}` → `400`, content type
    `application/problem+json`, and `errors.url[0]` equal to the exact contract message. A
    follow-up GET is `[]`
  - POST `https://example.com/feed`, then `https://EXAMPLE.com/feed` → second is `409` with
    `title` "Already subscribed" and `detail` "This feed is already in your subscription list."
  - POST `https://example.com/Feed` after `https://example.com/feed` → `201`
  - POST with a malformed JSON body (`{"url":`) → `400`, and the response body does not contain
    "Exception", "   at " or a file path
- [ ] T040 [P] [US3] Create `frontend/RSSFeedReader.UI/Models/ProblemResponse.cs`:
  `sealed record ProblemResponse(string? Title, string? Detail, Dictionary<string, string[]>? Errors)`,
  used to read `application/problem+json`

### Implementation for User Story 3

- [ ] T041 [US3] Run T039 against the existing endpoint and fix any gaps in
  `backend/RSSFeedReader.Api/Endpoints/SubscriptionEndpoints.cs` and
  `backend/RSSFeedReader.Api/Program.cs`. If the framework rejects a missing body before the
  handler runs, ensure `{}` still yields the "Enter a feed address." message
- [ ] T042 [US3] Refine `AddSubscriptionAsync` in `frontend/RSSFeedReader.UI/Services/SubscriptionsApiClient.cs`:
  - `400` → read `ProblemResponse` and return `Rejected(errors["url"][0])`
  - `409` → `Rejected(detail)`
  - If the message is missing → `Rejected("The subscription could not be added.")`
  - `HttpRequestException`, `TaskCanceledException` (not caller-cancelled), 5xx, or an
    unreadable body → `Unavailable`

  Keep all messages as `private const string`
- [ ] T043 [US3] Update `frontend/RSSFeedReader.UI/Pages/Subscriptions.razor` to hold
  `_errorMessage` and render it in `<p role="alert">` above the list when it is set:
  - `Rejected(message)` → show the message and **keep** `_newUrl` (FR-004)
  - `Unavailable` → show "Could not add the subscription. Please try again." and keep
    `_newUrl` (FR-011)
  - `Success` → clear `_errorMessage`
  - Clear `_errorMessage` at the start of each submit
- [ ] T044 [US3] Validate manually using [quickstart.md](quickstart.md) rows 5, 6, 7, 8 and 10.
  Confirm no JavaScript alert fires for `javascript:alert(1)` and that `<b>x</b>` is shown
  literally

**Checkpoint**: All user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: CORS verification, constitution compliance audit, and final gates

- [ ] T045 [P] Create `tests/RSSFeedReader.Api.Tests/Endpoints/CorsTests.cs` with two tests:
  - A preflight `OPTIONS /api/subscriptions` with `Origin: http://localhost:5213` and
    `Access-Control-Request-Method: POST` → the response has
    `Access-Control-Allow-Origin: http://localhost:5213`
  - The same request from `Origin: http://evil.example` → no `Access-Control-Allow-Origin` header
- [ ] T046 [P] Create `README.md` at the repo root: a one-paragraph purpose statement (MVP,
  in-memory, single user), prerequisites (.NET 10 SDK), the two `dotnet run` commands, and a
  link to `specs/001-subscription-management/quickstart.md`
- [ ] T047 Run the constitution compliance audit with `Select-String` over `backend/`,
  `frontend/` and `tests/` (`*.cs`, `*.razor`). Expect **zero** matches for `MarkupString`,
  `\.Result\b`, `\.Wait\(`, `AllowAnyOrigin`, `AllowCredentials`, `new HttpClient\(` (except
  the scoped registration in `frontend/RSSFeedReader.UI/Program.cs`), `SyndicationFeed` and
  `localhost` (except `appsettings.json`, `launchSettings.json` and test files). Fix any
  violations
- [ ] T048 Run the gates from quickstart section 1: `dotnet build RSSFeedReader.slnx` (0 warnings),
  `dotnet format RSSFeedReader.slnx --verify-no-changes` and `dotnet test RSSFeedReader.slnx`
  (all green)
- [ ] T049 Run all of [quickstart.md](quickstart.md) (sections 1–4) end to end and record the
  MVP as accepted

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies. T001 (SDK) blocks everything.
- **Foundational (Phase 2)**: Depends on Setup. T012–T014 (cleanup gate) MUST pass before
  any UI story task. The whole phase blocks all user stories.
- **US1 (Phase 3)**: Depends on Foundational.
- **US2 (Phase 4)**: Depends on Foundational. It uses the GET endpoint (T031) and the list
  markup (T033) from US1, so in practice it runs after US1.
- **US3 (Phase 5)**: Depends on Foundational and the POST endpoint (T030) and add flow (T033)
  from US1.
- **Polish (Phase 6)**: Depends on all stories.

### User Story Dependencies

```text
Setup → Foundational ─┬─> US1 (P1) ─┬─> US2 (P2)
                      │             └─> US3 (P3)
                      └─(validator/store already tested)
US2 and US3 touch different concerns and can proceed in parallel after US1, but both edit
Subscriptions.razor, so merge sequentially.
```

### Within Each Story

- Tests first, and confirm they fail.
- Backend endpoint, then UI client service, then Razor page, then manual validation.

### Key Task Dependencies

- T005/T006/T007 → T008 → T011
- T016 → T017 → T021 (same `Program.cs`), and T026 after T017
- T018, T019 → T020 → T021
- T018 → T022 → T023
- T023, T021, T024, T026 → T030 → T031
- T025, T027 → T032 → T033
- T030 → T039/T041. T040 → T042 → T043

---

## Parallel Examples

### Phase 1

```text
T003 .editorconfig  |  T004 .gitignore  |  T006 UI project  |  T007 test project  |  T010 UI launchSettings
```

### Phase 2

```text
T015 UI appsettings + HttpClient   |  T016 API CORS config
T018 Subscription record           |  T019 store interface + AddResult
T020 store tests                   |  T022 validator tests
T024 API DTOs                      |  T025 UI DTOs
```

### User Story 1

```text
T029 endpoint integration tests (write first)
then T030 → T031 (backend)  in parallel with  T032 (UI client, depends only on T025/T027)
then T033 (page) → T034 (manual check)
```

### User Story 2

```text
T035 GET tests  |  T037 page CSS
```

### User Story 3

```text
T039 rejection tests  |  T040 ProblemResponse model
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup (install the .NET 10 SDK first)
2. Phase 2: Foundational, including the **blocking template-cleanup gate** (T014)
3. Phase 3: User Story 1
4. **STOP and VALIDATE**: quickstart rows 2, 3, 4 and 12. This already satisfies the
   stakeholder "MVP working" definition (paste a URL → see the list update)

### Incremental Delivery

1. Setup + Foundational → validator and store tested and green
2. US1 → add flow demo (MVP)
3. US2 → empty state, initial load, refresh survival, scaling to 100+ entries
4. US3 → friendly rejection and outage messages
5. Polish → CORS tests, audit, full quickstart acceptance

### Out of Scope (do not implement; Constitution Principle I)

Feed fetching and parsing, refresh, item display, persistence, removal, links in the list, and
OpenAPI. These belong to Extended-MVP or Post-MVP specs.

---

## Notes

- [P] = different files and no dependency on incomplete tasks
- Commit after each task or logical group, and stop at any checkpoint to validate a story on
  its own
- If a task conflicts with the constitution, stop and raise it. Do not silently deviate
