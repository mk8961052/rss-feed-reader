---

description: "Task list for Feed Subscription Management (MVP) — Spring Boot + React/Vite"
---

# Tasks: Feed Subscription Management (MVP)

**Input**: Design documents from `/specs/001-subscription-management/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/subscriptions-api.md](contracts/subscriptions-api.md),
[quickstart.md](quickstart.md)

**Tests**: INCLUDED. Constitution v2.0.0 Principle V requires JUnit tests for the URL validator
and the in-memory store (including concurrency), and Vitest tests for the frontend API
module's mapping. It recommends (SHOULD) MockMvc tests for every `/api/` route. Write the tests
in each section first and confirm they fail before implementing.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: The user story the task belongs to (US1, US2, US3)

## Path Conventions

- Backend Java: `backend/src/main/java/com/rssfeedreader/…`, tests in
  `backend/src/test/java/com/rssfeedreader/…`
- Backend config: `backend/src/main/resources/application.yml`
- Frontend: `frontend/src/…`
- Package shorthand: **`sub/`** = `backend/src/main/java/com/rssfeedreader/subscription/`,
  **`subtest/`** = `backend/src/test/java/com/rssfeedreader/subscription/`

## Global Rules (apply to every task)

- **Backend**:
  - No dependencies beyond `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test`.
  - No `RestClient`/`WebClient`/`HttpClient`, ROME, JPA or `@Scheduled` (Principle I).
  - Constructor injection only.
  - Every package has a `package-info.java` with `@org.jspecify.annotations.NullMarked`.
  - Nullable parameters and fields are annotated `@Nullable`.
- **Frontend**:
  - No `dangerouslySetInnerHTML`, no `innerHTML`, no `any`, and no `fetch` outside
    `src/api/subscriptionsApi.ts`.
- **Both**:
  - Messages, limits, ports and URLs are named constants or configuration.
  - User-facing messages MUST match [contracts/subscriptions-api.md](contracts/subscriptions-api.md)
    verbatim.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Both apps scaffolded, quality tooling wired, and every gate green on the empty
projects

- [X] T001 Create a root `.gitignore` covering `backend/target/`, `frontend/node_modules/`,
  `frontend/dist/`, `*.local`, `.idea/`, `*.iml`, `.vscode/`, `.DS_Store`, `Thumbs.db` and
  `*.log`
- [X] T002 Generate the backend:
  1. Download
     `https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.2&javaVersion=17&groupId=com.rssfeedreader&artifactId=rss-feed-reader-api&name=rss-feed-reader-api&packageName=com.rssfeedreader&dependencies=web`
     to the scratchpad.
  2. Extract it into `backend/`. Keep `mvnw`, `mvnw.cmd` and `.mvn/`.
  3. Rename the generated application class to `RssFeedReaderApplication` in
     `backend/src/main/java/com/rssfeedreader/RssFeedReaderApplication.java`.
  4. Delete `HELP.md` and `application.properties`.
- [X] T003 [P] Scaffold the frontend: `npm create vite@latest frontend -- --template react-ts`,
  then `npm install` in `frontend/`. Record the resolved React, Vite and TypeScript versions in
  the task notes. *(Resolved: create-vite 9.3.1, React 19.3.1, Vite 8.3.4, TypeScript 6.0.3,
  and oxlint 1.88.0 as the template linter in place of ESLint.)*
- [X] T004 Edit `backend/pom.xml` *(Spotless 3.10.4 with google-java-format 1.28.0, the newest that runs on JDK 17)*:
  - Remove the empty `<licenses>`, `<developers>`, `<scm>`, `<url>`, `<name>` and
    `<description>` placeholders.
  - Configure `maven-compiler-plugin` with `<compilerArgs><arg>-Xlint:all</arg></compilerArgs>`
    and `<failOnWarning>true</failOnWarning>`. Exclude a lint category only with an XML comment
    explaining why.
  - Add `maven-enforcer-plugin` (execution `enforce`, phase `validate`) with
    `requireJavaVersion [17,)`, `requireMavenVersion [3.9,)`, `dependencyConvergence` and
    `banDuplicatePomDependencyVersions`.
  - Add `spotless-maven-plugin` using `googleJavaFormat` and `removeUnusedImports`, with the
    `check` goal bound to `verify`. Pick the newest google-java-format version that runs on
    JDK 17, and verify it with `.\mvnw.cmd spotless:apply`.
- [X] T005 Create `backend/src/main/java/com/rssfeedreader/package-info.java` with
  `@NullMarked`. Run `.\mvnw.cmd -f backend/pom.xml verify` and fix until it is green (zero
  compiler warnings, Spotless clean, Enforcer passes, and the generated context-load test
  passes)
- [X] T006 [P] Frontend tooling:
  - `npm install -D prettier vitest` in `frontend/`.
  - Add `frontend/.prettierrc.json` (`{}` defaults) and `frontend/.prettierignore` (`dist`,
    `node_modules`).
  - Add `frontend/.nvmrc` containing `24`.
  - In `frontend/package.json`, set `"engines": {"node": ">=24"}` and the scripts:
    - `"lint": "oxlint --deny-warnings"` (the template's linter)
    - `"format": "prettier --write ."`
    - `"format:check": "prettier --check ."`
    - `"typecheck": "tsc -b"`
    - `"test": "vitest run"`
  - Confirm `"strict": true` in `frontend/tsconfig.app.json`.
- [X] T007 [P] Set `frontend/vite.config.ts` to `server: { port: 5173, strictPort: true }` and
  add a Vitest `test: { environment: "node" }` block (with the `/// <reference types="vitest/config" />`
  triple-slash reference if the TS types need it)
- [X] T008 Run the frontend gates in `frontend/`: `npm run lint`, `npm run format` then
  `npm run format:check`, `npm run typecheck`, and `npm run build`. All must pass.
  (`npm test` may report "no test files" at this point, which is acceptable.)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Template cleanup gate, configuration, error handling, domain, store, validator,
DTOs, and the controller, service and API-module shells every story builds on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete. T009–T010 form the
constitution's **blocking template-cleanup gate**.

- [X] T009 Clean up the Vite template:
  - Delete `frontend/src/assets/` (`hero.png`, `react.svg`, `vite.svg`),
    `frontend/public/icons.svg`, `frontend/src/App.css` and `frontend/README.md`.
  - Replace `frontend/public/favicon.svg` with a neutral feed icon.
  - Replace `frontend/src/App.tsx` with a component that renders
    `<main><h1>Subscriptions</h1></main>`.
  - Reset `frontend/src/index.css` to minimal base styles: system font, `max-width: 48rem`
    centered content, `1rem` padding.
  - In `frontend/index.html`, set `<title>RSS Feed Reader</title>`.
- [X] T010 Verify the cleanup gate. With `$p = 'count is','get started','hero.png','react.svg','vite.svg','icons.svg'`,
  both `Get-ChildItem frontend/src -Recurse -File | Select-String -Pattern $p` and
  `Select-String -Path frontend/index.html -Pattern $p` must return no output. Then `npm run build` and `npm run dev`, open `http://localhost:5173`, and
  confirm the DevTools console shows no errors (no 404s). **STOP if this fails.**
- [X] T011 [P] Create `frontend/.env` with `VITE_API_BASE_URL=http://localhost:8080/api/`.
  Declare `interface ImportMetaEnv { readonly VITE_API_BASE_URL?: string }` in
  `frontend/src/vite-env.d.ts` (create it if the template no longer ships it). Create
  `frontend/src/config.ts`, which exports `apiBaseUrl`: the env value, or
  `throw new Error("VITE_API_BASE_URL is not configured.")` if it's missing or empty. There is
  no hardcoded fallback
- [X] T012 [P] Create `backend/src/main/resources/application.yml` with:
  - `server.port: 8080`
  - `server.error.include-stacktrace: never`
  - `server.error.include-message: never`
  - `spring.mvc.problemdetails.enabled: true`
  - `app.cors.allowed-origins: [http://localhost:5173]`
- [X] T013 [P] Create `backend/src/main/java/com/rssfeedreader/config/package-info.java`
  (`@NullMarked`), `config/CorsProperties.java`
  (`@ConfigurationProperties("app.cors") public record CorsProperties(List<String> allowedOrigins)`,
  whose compact constructor throws `IllegalStateException` if the list is null or empty) and
  `config/ClockConfig.java` (`@Configuration` with a `@Bean Clock clock()` returning
  `Clock.systemUTC()`). Enable the properties with
  `@ConfigurationPropertiesScan` on `RssFeedReaderApplication`
- [X] T014 Create `backend/src/main/java/com/rssfeedreader/config/WebConfig.java`: a
  `@Configuration` implementing `WebMvcConfigurer`, with `CorsProperties` constructor-injected.
  `addCorsMappings` registers `/api/**` with
  `.allowedOrigins(props.allowedOrigins().toArray(String[]::new))`, `.allowedMethods("GET", "POST")`
  and `.allowedHeaders("Content-Type")`. Never use `"*"` or `allowCredentials(true)`
  (depends on T013)
- [X] T015 [P] Create `sub/package-info.java` (`@NullMarked`) and `sub/Subscription.java`:
  `public record Subscription(UUID id, String url, String normalizedUrl, Instant addedAt)`.
  Its Javadoc states: `url` is "user input with surrounding whitespace trimmed; 1–2048 chars;
  absolute http/https URI with a host"; `normalizedUrl` is "unique across the store; never
  serialized to clients"; `addedAt` is UTC
- [X] T016 [P] Create `sub/SubscriptionStore.java`, an interface with:
  - `List<Subscription> findAll();`: an immutable snapshot, oldest first
  - `boolean addIfAbsent(Subscription subscription);`: false if `normalizedUrl` already exists
- [X] T017 [P] Write the failing tests in `subtest/InMemorySubscriptionStoreTest.java`
  (JUnit 5 + AssertJ):
  - A new store's `findAll()` is empty.
  - Three adds come back in insertion order.
  - A second subscription with the same `normalizedUrl` → `addIfAbsent` returns `false` and
    the size is unchanged.
  - The list from `findAll()` is unmodifiable and doesn't change after a later add.
  - 50 threads (an `ExecutorService` plus a `CountDownLatch` start gate) call `addIfAbsent`
    with the same `normalizedUrl` and different ids → exactly one returns `true` and
    `findAll()` has size 1.
- [X] T018 Implement `sub/InMemorySubscriptionStore.java`: a `@Repository` `final` class
  implementing `SubscriptionStore`. It has
  `private final ReentrantLock lock = new ReentrantLock();`,
  `private final List<Subscription> items = new ArrayList<>();` and
  `private final Set<String> keys = new HashSet<>();`.
  - `addIfAbsent`: under the lock, `if (!keys.add(s.normalizedUrl())) return false; items.add(s); return true;`
  - `findAll`: under the lock, `return List.copyOf(items);`

  Make T017 pass.
- [X] T019 [P] Write the failing tests in `subtest/SubscriptionUrlValidatorTest.java`
  (`@ParameterizedTest`):
  - `null`, `""`, `"   "` → invalid, "Enter a feed address."
  - A 2049-char `https://example.com/` + padding URL → invalid, "The feed address must be 2048
    characters or fewer."
  - Exactly 2048 chars → valid
  - `not a url`, `ftp://example.com/feed`, `javascript:alert(1)`, `/relative/path`,
    `mailto:a@b.c`, `http://` → invalid, "Enter a web address starting with http:// or https://."
  - `"  https://example.org/rss  "` → valid with `url() == "https://example.org/rss"`
  - `HTTPS://EXAMPLE.com/feed`, `https://example.com/feed` and `https://example.com:443/feed`
    all have the same `normalizedUrl`
  - `https://example.com/Feed` has a different `normalizedUrl` from `https://example.com/feed`
  - `https://example.com:8443/feed` keeps `:8443` in its key
  - `https://example.com` and `https://example.com/` share a key
  - `https://example.com/?q=<b>x</b>` → valid
  - `https://example.com` (no feed check) → valid
- [X] T020 Implement `sub/UrlValidationResult.java`, a `record UrlValidationResult(boolean valid, @Nullable String url, @Nullable String normalizedUrl, @Nullable String errorMessage)`
  with static factories `valid(url, normalizedUrl)` and `invalid(message)`. Also implement
  `sub/SubscriptionUrlValidator.java`: a `final` class with a private constructor,
  `public static final int MAX_URL_LENGTH = 2048;`, `public static final String` constants
  `EMPTY_MESSAGE`, `TOO_LONG_MESSAGE` and `NOT_WEB_ADDRESS_MESSAGE` (the texts from T019), and
  `public static UrlValidationResult validate(@Nullable String input)`. Checks run in order:
  1. null or blank → Empty
  2. `trimmed.length() > MAX_URL_LENGTH` → TooLong
  3. Parse with
     `UriComponentsBuilder.fromUriString(trimmed, UriComponentsBuilder.ParserType.WHAT_WG).build()`
     inside `try/catch (IllegalArgumentException)`. If parsing fails, the scheme isn't
     `http`/`https` (case-insensitive) or the host is null or blank → NotWebAddress.
  4. Otherwise the result is Valid. `normalizedUrl` = `scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT)`
     + (port is `-1` or the scheme default 80/443 ? "" : ":" + port) + (path empty ? "/" :
     path) + (query != null ? "?" + query : "") + (fragment != null ? "#" + fragment : "").

  Make T019 pass. If the WHATWG parser behaves differently than expected, adjust the
  normalization while keeping every T019 expectation (research R5).
- [X] T021 [P] Create `sub/AddSubscriptionRequest.java`
  (`public record AddSubscriptionRequest(@Nullable String url)`) and
  `sub/SubscriptionResponse.java` (`public record SubscriptionResponse(UUID id, String url, Instant addedAt)`
  with `static SubscriptionResponse from(Subscription s)`; it does **not** expose
  `normalizedUrl`)
- [X] T022 [P] Create `sub/InvalidSubscriptionUrlException.java`
  (`public final class … extends RuntimeException`, constructor takes the user message) and
  `sub/DuplicateSubscriptionException.java` (`public final class … extends RuntimeException`,
  no-arg constructor). Add `@Serial private static final long serialVersionUID = 1L;` to both
  so `-Xlint:serial` stays quiet
- [X] T023 Create `backend/src/main/java/com/rssfeedreader/web/package-info.java`
  (`@NullMarked`) and `web/ApiExceptionHandler.java`: `@RestControllerAdvice` extending
  `ResponseEntityExceptionHandler`, with a `private static final Logger`. Handlers:
  - `InvalidSubscriptionUrlException` → `ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.getMessage())`,
    title "Invalid feed address"
  - `DuplicateSubscriptionException` → 409, title "Already subscribed", detail "This feed is
    already in your subscription list."
  - `Exception` (catch-all) → `log.error("Unhandled exception", ex)`, then 500 with title
    "Unexpected error" and detail "An unexpected error occurred."

  Keep all titles and details as `private static final String` constants.
- [X] T024 Create `sub/SubscriptionService.java`: a `@Service` with `SubscriptionStore` and
  `Clock` constructor-injected and an empty `add` method shell (implemented in US1)
- [X] T025 Create `sub/SubscriptionController.java`: `@RestController`,
  `@RequestMapping("/api/subscriptions")`, with `SubscriptionService` and `SubscriptionStore`
  constructor-injected and no endpoints yet
- [X] T026 [P] Create `frontend/src/api/subscriptionsApi.ts`. It exports:
  - `type Subscription = { id: string; url: string; addedAt: string }`
  - `type AddSubscriptionResult = { kind: "success" } | { kind: "rejected"; message: string } | { kind: "unavailable" }`
  - the message constants `ADD_REJECTED_FALLBACK = "The subscription could not be added."`
  - `function createSubscriptionsApi(baseUrl: string, fetchFn: typeof fetch = fetch)`, which
    returns an object with stub `getSubscriptions` and `addSubscription` methods (implemented
    in US1)
  - `const subscriptionsApi = createSubscriptionsApi(apiBaseUrl)`, where `apiBaseUrl` comes
    from `../config`
- [X] T027 Run the gates: `.\mvnw.cmd -f backend/pom.xml verify` (T017 and T019 green, zero
  warnings), and `npm run lint`, `npm run typecheck`, `npm run build` in `frontend/`

**Checkpoint**: The foundation is ready, the cleanup gate has passed, and the validator and
store are tested. User stories can begin.

---

## Phase 3: User Story 1 - Add a feed subscription by URL (Priority: P1) 🎯 MVP

**Goal**: The user pastes a feed URL, presses Add or Enter, and sees it appear in the list
immediately, with the input cleared.

**Independent Test**: Open `http://localhost:5173`, add `https://devblogs.microsoft.com/dotnet/feed/`,
and confirm it appears in the list within 1 s without a reload (quickstart rows 2, 3, 4, 12).

### Tests for User Story 1 ⚠️ (write first, confirm they fail)

- [X] T028 [P] [US1] Create `subtest/SubscriptionServiceTest.java`. Construct the service
  directly with a `new InMemorySubscriptionStore()` and
  `Clock.fixed(Instant.parse("2026-10-24T09:00:00Z"), ZoneOffset.UTC)`. Tests:
  - `add("  https://example.org/rss  ")` returns a subscription with the trimmed url, a
    non-null id and `addedAt` equal to the fixed instant.
  - Adding a duplicate in different case throws `DuplicateSubscriptionException`.
  - `add("not a url")` throws `InvalidSubscriptionUrlException` with
    `NOT_WEB_ADDRESS_MESSAGE`, and the store stays empty.
- [X] T029 [P] [US1] Create `subtest/SubscriptionControllerTest.java` with
  `@SpringBootTest`, `@AutoConfigureMockMvc` and
  `@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)`, with
  `MockMvc` injected. (Use the Spring Boot 4 package for `@AutoConfigureMockMvc`; check the
  imports against the test starter.) Tests:
  - POST `{"url":"  https://devblogs.microsoft.com/dotnet/feed/  "}` → 201, `$.url` is the
    trimmed value, `$.id` is a UUID, and the `Location` header ends with `/api/subscriptions/{id}`.
  - POST then GET → a JSON array of length 1 with that url.

### Implementation for User Story 1

- [X] T030 [US1] Implement `SubscriptionService.add(@Nullable String rawUrl)` in
  `sub/SubscriptionService.java`:
  1. `var result = SubscriptionUrlValidator.validate(rawUrl);` If it's invalid →
     `throw new InvalidSubscriptionUrlException(result.errorMessage())`.
  2. Create `new Subscription(UUID.randomUUID(), result.url(), result.normalizedUrl(), clock.instant())`.
  3. If `!store.addIfAbsent(s)` → `throw new DuplicateSubscriptionException()`.
  4. Return `s`.

  Make T028 pass.
- [X] T031 [US1] Add the endpoints in `sub/SubscriptionController.java`:
  - `@PostMapping ResponseEntity<SubscriptionResponse> add(@RequestBody AddSubscriptionRequest request)`
    → `service.add(request.url())` →
    `ResponseEntity.created(URI.create("/api/subscriptions/" + s.id())).body(SubscriptionResponse.from(s))`
  - `@GetMapping List<SubscriptionResponse> list()` →
    `store.findAll().stream().map(SubscriptionResponse::from).toList()`

  Make T029 pass.
- [X] T032 [US1] Implement both methods in `frontend/src/api/subscriptionsApi.ts`. Build the
  URL as `new URL("subscriptions", baseUrl)`.
  - `getSubscriptions(signal?: AbortSignal): Promise<Subscription[] | null>`: `fetchFn` with
    `Accept: application/json`. Returns `null` if `fetch` throws (except `AbortError`, which
    is rethrown), the response isn't `ok`, or the body isn't an array.
  - `addSubscription(url: string): Promise<AddSubscriptionResult>`: POST with
    `Content-Type: application/json` and body `JSON.stringify({ url })`. Status `201` →
    `{kind:"success"}`. For now, everything else, including thrown errors, →
    `{kind:"unavailable"}` (refined in T042).
- [X] T033 [US1] Create `frontend/src/components/SubscriptionsPage.tsx` and render it from
  `frontend/src/App.tsx`. The component has:
  - State: `newUrl: string`, `subscriptions: Subscription[]` and `isSubmitting: boolean`, plus
    `inFlight = useRef(false)`.
  - A `<form onSubmit={handleSubmit}>` containing `<label htmlFor="feed-url">Feed URL</label>`,
    `<input id="feed-url" type="text" value={newUrl} onChange=… placeholder="Paste a feed URL" />`
    and `<button type="submit" disabled={isSubmitting}>Add</button>`. Enter submits via the
    form (FR-001).
  - `handleSubmit`:
    1. `e.preventDefault()`. If `inFlight.current` is set, return; otherwise set it and set
       `isSubmitting` to true (FR-012).
    2. `await subscriptionsApi.addSubscription(newUrl)`.
    3. On `success`: `setNewUrl("")`, then re-fetch with `getSubscriptions()` and set the list
       if it isn't null. The API is the source of truth; never append optimistically.
    4. `finally`: reset `inFlight` and `isSubmitting`.
  - Rendering: `<ul className="subscription-list">{subscriptions.map(s => <li key={s.id}>{s.url}</li>)}</ul>`,
    as escaped text with no `<a>` (FR-009).
- [ ] T034 [US1] Validate manually with [quickstart.md](quickstart.md) section 5, rows 2, 3, 4
  and 12, with both apps running

**Checkpoint**: User Story 1 works end to end. This is the demonstrable MVP core.

---

## Phase 4: User Story 2 - View the list of subscriptions (Priority: P2)

**Goal**: On opening the app, the user sees all current subscriptions oldest first, or a clear
empty state, and the list survives a browser refresh while the API is running.

**Independent Test**: A fresh backend shows "No subscriptions yet". Add three subscriptions,
press F5, and they're shown in the same order (quickstart rows 1, 9, 11).

### Tests for User Story 2 ⚠️ (write first, confirm they fail)

- [X] T035 [P] [US2] Add these tests to `subtest/SubscriptionControllerTest.java`:
  - GET on a fresh context → 200 with body exactly `[]`.
  - POST `https://a.example/feed`, `https://b.example/feed` and `https://c.example/feed` →
    GET `$[0].url`, `$[1].url` and `$[2].url` come back in that order.
  - Every item has `id`, `url` and `addedAt` (an ISO-8601 string) and has no `normalizedUrl`
    (`jsonPath("$[0].normalizedUrl").doesNotExist()`).
- [X] T036 [P] [US2] Create `frontend/src/api/subscriptionsApi.test.ts` (Vitest), using a fake
  `fetchFn` built with `vi.fn` that returns `new Response(...)`. Tests for `getSubscriptions`:
  - A 200 JSON array → the array
  - A 500 → `null`
  - A rejected promise (`TypeError`) → `null`
  - A 200 non-array body → `null`
  - Requests target `${baseUrl}subscriptions`

### Implementation for User Story 2

- [X] T037 [US2] In `frontend/src/components/SubscriptionsPage.tsx`, load the list on mount in
  a `useEffect`, using an `AbortController` that is aborted in the cleanup. Track
  `loadState: "loading" | "loaded" | "failed"` and render exactly one of:
  - "Loading…"
  - "Could not load subscriptions." (when `getSubscriptions` returns `null`)
  - "No subscriptions yet" (empty array, FR-008)
  - the `<ul>` from T033, in API order (oldest first, FR-007)
- [X] T038 [P] [US2] Create `frontend/src/components/SubscriptionsPage.css` (imported by the
  component). `.subscription-list` gets `max-height: 60vh; overflow-y: auto; padding-left: 1.25rem;`
  and `.subscription-list li` gets `overflow-wrap: anywhere;`, so 100+ entries and 2048-char
  URLs stay usable (SC-004). Keep styling minimal and functional
- [ ] T039 [US2] Validate manually with [quickstart.md](quickstart.md) rows 1, 9 and 11, plus
  the 100-subscription loop in quickstart section 6. Reload, confirm the list scrolls, and
  confirm a new add still appears within 1 s

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Be told when an entry cannot be added (Priority: P3)

**Goal**: Empty, non-web, over-long and duplicate inputs are refused with a specific message,
the typed text stays in the field, and a down API shows a generic message.

**Independent Test**: Submit an empty value, `javascript:alert(1)`, `not a url` and a
duplicate in different case. Each is refused with its contract message and the list is
unchanged (quickstart rows 5–8 and 10).

### Tests for User Story 3 ⚠️ (write first, confirm they fail)

- [X] T040 [P] [US3] Add these tests to `subtest/SubscriptionControllerTest.java`:
  - A `@ParameterizedTest` over the request bodies `{}`, `{"url":null}`, `{"url":""}`,
    `{"url":"   "}`, a 2049-char URL, `{"url":"not a url"}`,
    `{"url":"ftp://example.com/feed"}`, `{"url":"javascript:alert(1)"}` and
    `{"url":"/relative/path"}` → 400, content type `application/problem+json`, `$.title`
    "Invalid feed address", and `$.detail` equal to the exact contract message. A follow-up
    GET is `[]`.
  - POST `https://example.com/feed`, then `https://EXAMPLE.com/feed` → 409, `$.title`
    "Already subscribed", `$.detail` "This feed is already in your subscription list."
  - POST `https://example.com/Feed` after `https://example.com/feed` → 201.
  - POST `https://example.com/?q=<b>x</b>` → 201.
  - POST with the malformed body `{"url":` → 400. The response body doesn't contain
    "Exception", "\tat ", ".java" or "com.rssfeedreader".
- [X] T041 [P] [US3] Add `addSubscription` tests to `frontend/src/api/subscriptionsApi.test.ts`:
  - 201 → `{kind:"success"}`, and the request was a POST with a JSON body `{"url":…}`.
  - A 400 problem+json with `detail` → `{kind:"rejected", message: detail}`.
  - A 409 with `detail` → rejected with that detail.
  - A 400 without `detail`, or with a non-JSON body → rejected with `ADD_REJECTED_FALLBACK`.
  - A 500 → `{kind:"unavailable"}`.
  - A thrown `TypeError` → `{kind:"unavailable"}`.

### Implementation for User Story 3

- [X] T042 [US3] Refine `addSubscription` in `frontend/src/api/subscriptionsApi.ts`:
  - `400` or `409` → parse the JSON safely. If `detail` is a non-empty string, return
    `{kind:"rejected", message: detail}`; otherwise return rejected with
    `ADD_REJECTED_FALLBACK`.
  - Any other non-201 status or a thrown error → `{kind:"unavailable"}`.

  Make T041 pass.
- [X] T043 [US3] Run T040 against the backend and fix any gaps in `sub/SubscriptionController.java`
  and `web/ApiExceptionHandler.java`. Make sure `{}`/`null` reach the validator (giving "Enter
  a feed address.") and that the malformed-JSON response from `ResponseEntityExceptionHandler`
  carries only a generic detail. Override `handleHttpMessageNotReadable` if the default detail
  leaks parser text.
- [X] T044 [US3] In `frontend/src/components/SubscriptionsPage.tsx`, add `errorMessage` state.
  Clear it at the start of each submit, and render it in `<p role="alert">` above the list
  when it is set. Handle the add result:
  - `rejected` → show `message` and **keep** `newUrl` (FR-004)
  - `unavailable` → show `ADD_UNAVAILABLE_MESSAGE` ("Could not add the subscription. Please
    try again.", exported from `subscriptionsApi.ts`) and keep `newUrl` (FR-011)
  - `success` → no error
- [ ] T045 [US3] Validate manually with [quickstart.md](quickstart.md) rows 5, 6, 7, 8 and 10.
  Confirm that no JavaScript alert fires for `javascript:alert(1)` and that `<b>x</b>` is
  shown literally

**Checkpoint**: All user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: CORS verification, constitution compliance audit, docs, and final gates

- [X] T046 [P] Create `backend/src/test/java/com/rssfeedreader/config/CorsTest.java`
  (`@SpringBootTest` + `@AutoConfigureMockMvc`) with two tests:
  - An `OPTIONS /api/subscriptions` preflight with `Origin: http://localhost:5173` and
    `Access-Control-Request-Method: POST` → `Access-Control-Allow-Origin: http://localhost:5173`.
  - The same preflight from `Origin: http://evil.example` → 403 with no
    `Access-Control-Allow-Origin` header.
- [X] T047 [P] Create `README.md` at the repo root covering:
  - Purpose (MVP, in-memory, single user)
  - Prerequisites (JDK 17+, Node 24+)
  - Run commands: `cd backend; ./mvnw spring-boot:run` and `cd frontend; npm ci; npm run dev`
  - A link to `specs/001-subscription-management/quickstart.md`
- [X] T048 Run the constitution compliance audit with `Select-String` over `backend/src` and
  `frontend/src`. Expect **zero** matches for:
  - `dangerouslySetInnerHTML`, `innerHTML`, `: any\b`, `as any`
  - `@Autowired` on fields
  - `allowedOrigins\("\*"\)`, `allowedOriginPatterns`, `allowCredentials`
  - `RestClient`, `WebClient`, `HttpClient`, `rometools`, `@Scheduled`
  - `fetch\(` outside `src/api/subscriptionsApi.ts`
  - `localhost` in `.java`/`.ts(x)` sources outside tests

  Also confirm every package under `backend/src/main/java` has a `package-info.java`. Fix any
  violations.
- [X] T049 Run every gate in quickstart sections 1–3: `.\mvnw.cmd verify` in `backend/`, and
  `npm ci`, `npm run lint`, `npm run format:check`, `npm run typecheck`, `npm test` and
  `npm run build` in `frontend/`. All must be green
- [ ] T050 Run all of [quickstart.md](quickstart.md) (sections 1–5) end to end and record the
  MVP as accepted

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies. T002 → T004 → T005 (backend chain); T003 → T006/T007
  → T008 (frontend chain). The two chains are independent.
- **Foundational (Phase 2)**: Depends on Setup. T009–T010 (cleanup gate) MUST pass before any
  frontend story task. The whole phase blocks all user stories.
- **US1 (Phase 3)**: Depends on Foundational.
- **US2 (Phase 4)**: Depends on Foundational. It reuses the GET endpoint (T031) and the list
  markup (T033) from US1, so in practice it runs after US1.
- **US3 (Phase 5)**: Depends on the POST endpoint (T031) and the add flow (T033) from US1.
- **Polish (Phase 6)**: Depends on all stories.

### User Story Dependencies

```text
Setup → Foundational → US1 (P1) ─┬─> US2 (P2)
                                 └─> US3 (P3)
US2 and US3 are independent of each other, but both edit SubscriptionsPage.tsx,
SubscriptionControllerTest.java and subscriptionsApi.test.ts, so merge them sequentially.
```

### Key Task Dependencies

- T012, T013 → T014. T015, T016 → T017 → T018. T015 → T019 → T020
- T018, T020, T022 → T024 → T030. T021, T025, T030 → T031
- T011 → T026 → T032 → T033 → T037/T044
- T023 → T040/T043. T041 → T042 → T044

---

## Parallel Examples

### Phase 1

```text
Backend chain: T002 → T004 → T005    ||    Frontend chain: T003 → (T006 | T007) → T008
```

### Phase 2

```text
T011 frontend env/config  |  T012 application.yml  |  T013 CorsProperties + Clock
T015 Subscription record  |  T016 store interface
T017 store tests          |  T019 validator tests
T021 DTOs                 |  T022 exceptions        |  T026 API module shell
```

### User Story 1

```text
T028 service tests  |  T029 controller tests       (write first)
T030 → T031 (backend)   ||   T032 → T033 (frontend)
```

### User Story 2

```text
T035 controller GET tests  |  T036 Vitest getSubscriptions tests  |  T038 CSS
```

### User Story 3

```text
T040 controller rejection tests  |  T041 Vitest addSubscription tests
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup (no installs needed: JDK 17, Maven and Node 24 are present)
2. Phase 2: Foundational, including the **blocking template-cleanup gate** (T010)
3. Phase 3: User Story 1
4. **STOP and VALIDATE**: quickstart rows 2, 3, 4 and 12. This already satisfies the
   stakeholder "MVP working" definition.

### Incremental Delivery

1. Setup + Foundational → validator and store tested and green
2. US1 → add flow demo (MVP)
3. US2 → empty state, initial load, refresh survival, scaling to 100+ entries
4. US3 → friendly rejection and outage messages
5. Polish → CORS tests, audit, README, full quickstart acceptance

### Out of Scope (do not implement; Constitution Principle I)

Feed fetching and parsing, refresh, item display, persistence, removal, links in the list,
OpenAPI/Swagger, a router, and component tests. These belong to Extended-MVP or Post-MVP specs.

---

## Notes

- [P] = different files and no dependency on incomplete tasks
- Commit after each task or logical group, and stop at any checkpoint to validate a story on
  its own
- If a task conflicts with the constitution, stop and raise it. Do not silently deviate
