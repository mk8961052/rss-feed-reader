# Research: Feed Subscription Management (MVP)

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-10-24
**Stack**: Spring Boot + React/Vite (Constitution v2.0.0)

Each entry records a decision, why it was made, and what else was considered.

## R1. Runtime versions

- **Decision**:
  - Backend: **Java 17**, **Spring Boot 4.1.2** (current default on start.spring.io), and
    **Maven 3.9** through the committed Maven Wrapper.
  - Frontend: **Node 24** with React 19, TypeScript and Vite, using whatever versions the
    current `create-vite` `react-ts` template pins.
- **Rationale**: The workstation already has Zulu JDK 17.0.10, Maven 3.9.12 and Node 24.13.1,
  so nothing needs installing. Spring Boot 4 requires Java 17 as a minimum. Java 21 or 25 would
  enable NullAway and newer language features, but isn't installed. Moving up later is a
  one-line `<java.version>` change.
- **Alternatives considered**: Spring Boot 3.5 (open-source support has ended). Java 21/25
  (would need an install). Gradle (not installed; Maven is the user's familiar tool).

## R2. Backend project generation

- **Decision**: Download a start.spring.io project (`type=maven-project`, `bootVersion=4.1.2`,
  `javaVersion=17`, `groupId=com.rssfeedreader`, `artifactId=rss-feed-reader-api`,
  `dependencies=web`) into `backend/`. This includes `mvnw`/`mvnw.cmd`. The generated
  dependencies are exactly `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test`
  (test scope).
- **Rationale**: This is the canonical, reproducible scaffold. "Web" is the only starter the
  MVP needs (Constitution Principle I). Validation is a hand-written pure class (R5), so
  `spring-boot-starter-validation` isn't needed.
- **Alternatives considered**: A hand-written POM (error-prone). Adding Bean Validation
  (`@NotBlank`/`@URL`), rejected because the rules (scheme allow-list, lenient parsing,
  normalization) don't fit annotations cleanly.

## R3. Backend package layout

- **Decision**: Package-by-feature under `com.rssfeedreader`:
  - `com.rssfeedreader`: `RssFeedReaderApplication`
  - `com.rssfeedreader.config`: `CorsProperties` (`@ConfigurationProperties("app.cors")`
    record), `WebConfig` (`WebMvcConfigurer` for CORS), `ClockConfig` (`Clock` bean)
  - `com.rssfeedreader.web`: `ApiExceptionHandler` (`@RestControllerAdvice` extending
    `ResponseEntityExceptionHandler`)
  - `com.rssfeedreader.subscription`: domain, store, validator, service, controller, DTOs and
    exceptions

  Every package has a `package-info.java` with `@NullMarked`.
- **Rationale**: This keeps the whole feature in one place. Cross-cutting configuration and
  error handling are separate. `@NullMarked` is required by Constitution Principle V, and
  JSpecify comes in transitively with Spring Framework 7, so no new dependency is needed.
- **Alternatives considered**: Package-by-layer (`controller/`, `service/`, …), which spreads a
  tiny feature across many packages.

## R4. Control flow for add outcomes

- **Decision**: `SubscriptionService.add(String rawUrl)` returns the created `Subscription`, or
  throws a domain exception: `InvalidSubscriptionUrlException(message)` or
  `DuplicateSubscriptionException`. `ApiExceptionHandler` maps them to `ProblemDetail` 400 and
  409. The controller is a one-liner that returns `201 Created`.
- **Rationale**: This is the idiomatic Spring MVC approach. It keeps the controller thin
  (Principle IV) and keeps HTTP types out of the service. Problem responses are produced in
  one place (Principle II).
- **Alternatives considered**: A sealed result type plus `ResponseEntity<?>` in the controller
  (loses typed return values). Services throwing Spring's `ErrorResponseException` (couples
  the domain to HTTP).

## R5. URL validation (FR-002, FR-004) and duplicate key (FR-005)

- **Decision**: Use a pure `SubscriptionUrlValidator` (no Spring bean state, static
  `validate(@Nullable String input)`) that returns a `UrlValidationResult`. It runs these
  checks in order:
  1. null or blank → `"Enter a feed address."`
  2. trimmed length > 2048 → `"The feed address must be 2048 characters or fewer."`
  3. Parse with `UriComponentsBuilder.fromUriString(trimmed, ParserType.WHAT_WG)` (lenient,
     browser-style). If parsing fails, the scheme isn't `http`/`https` (case-insensitive), or
     the host is null or blank → `"Enter a web address starting with http:// or https://."`

  The **normalized key** is the lowercase scheme, then `://`, then the lowercase host, then
  `:port` only if the port isn't the scheme default (80/443), then the path as given (`/` if
  empty), then `?query` and `#fragment` if present.
- **Rationale**:
  - `java.net.URI` rejects unencoded `<`, `>` and spaces in a query, which would wrongly
    reject the spec's edge case `https://example.com/?q=<b>x</b>`.
  - The WHATWG parser behaves like the browser that will eventually open the link.
  - Lowercasing only the scheme and host matches FR-005: paths are case-sensitive on many
    servers.
  - Tests pin this behaviour so a parser change can't regress silently.
- **Alternatives considered**: `java.net.URI` with manual pre-encoding (fragile). `java.net.URL`
  (deprecated constructors, and DNS side effects in `equals`). Regex (unreadable).
- **Verification**: if T-tests show the WHATWG parser normalizes more than expected (for
  example punycode or path dot-segments), that's acceptable as long as the FR-005 examples
  still pass.

## R6. Thread-safe in-memory store

- **Decision**: Interface `SubscriptionStore` with `List<Subscription> findAll()` (oldest
  first, immutable snapshot) and `boolean addIfAbsent(Subscription s)`. `InMemorySubscriptionStore`
  (`@Repository`, singleton) holds an `ArrayList<Subscription>` plus a `HashSet<String>` of
  normalized keys, both guarded by one `ReentrantLock`. `findAll` returns `List.copyOf(...)`.
- **Rationale**: Check-then-act must be atomic, so a double submit can't create two entries
  (FR-012). `ConcurrentHashMap` alone doesn't preserve insertion order (FR-007), and the lock
  keeps the code obvious. The interface lets Spring Data JPA replace it post-MVP (Principle IV).
- **Alternatives considered**: `ConcurrentHashMap.putIfAbsent` plus `CopyOnWriteArrayList` (two
  structures that can disagree). `Collections.synchronizedList` (doesn't make check-then-act
  atomic).

## R7. Error responses

- **Decision**:
  - `spring.mvc.problemdetails.enabled: true`, `server.error.include-stacktrace: never`,
    `server.error.include-message: never`.
  - `ApiExceptionHandler extends ResponseEntityExceptionHandler`:
    - `InvalidSubscriptionUrlException` → 400, title "Invalid feed address", `detail` = the
      validator message
    - `DuplicateSubscriptionException` → 409, title "Already subscribed", detail "This feed is
      already in your subscription list."
    - Any other `Exception` → log at ERROR and return 500 with title "Unexpected error" and
      detail "An unexpected error occurred." only.
  - Malformed JSON is handled by the base class as a 400 with a generic detail.
- **Rationale**: Constitution Principle II. Using `detail` for both 400 and 409 gives the UI one
  field to read.
- **Alternatives considered**: An `errors.url[]` map like ASP.NET's ValidationProblem, rejected
  because there's only one field and Spring's idiom is `detail`.

## R8. Timestamps

- **Decision**: Inject a `java.time.Clock` bean (`Clock.systemUTC()`). Stamp `addedAt` as an
  `Instant`, serialized as an ISO-8601 string by Jackson (Spring Boot's default
  `write-dates-as-timestamps=false`).
- **Rationale**: Tests can supply `Clock.fixed(...)`. The list is ordered by insertion, so
  `addedAt` is informational.

## R9. CORS and configuration

- **Decision**:
  - `application.yml`: `server.port: 8080` and `app.cors.allowed-origins: [http://localhost:5173]`.
  - `CorsProperties(List<String> allowedOrigins)` is validated non-empty in its compact
    constructor (fail fast).
  - `WebConfig.addCorsMappings` maps `/api/**` with `allowedOrigins(...)`,
    `allowedMethods("GET","POST")` and `allowedHeaders("Content-Type")`. No credentials.
- **Rationale**: The TechStack port contract, plus the constitution's CORS rules.
- **Alternatives considered**: `@CrossOrigin` on the controller (scatters configuration and
  hardcodes origins).

## R10. Backend code-quality tooling

- **Decision**:
  - `maven-compiler-plugin`: `<compilerArgs>-Xlint:all</compilerArgs>`,
    `<failOnWarning>true</failOnWarning>`. A category may be excluded with a POM comment if
    Spring/Jackson make it unavoidable.
  - `spotless-maven-plugin` 3.10.4 with `googleJavaFormat` **1.28.0**, the newest release that
    runs on JDK 17 (1.37.0 fails with an `InvocationTargetException`). The `check` goal is bound
    to `verify`.
  - `maven-enforcer-plugin`: `requireJavaVersion [17,)`, `requireMavenVersion [3.9,)`,
    `dependencyConvergence`, `banDuplicatePomDependencyVersions`.
- **Rationale**: These are the Java equivalents of the warnings-as-errors, `dotnet format` and
  SDK pin rules (Principle V).

## R11. Frontend structure

- **Decision**:
  - `npm create vite@latest frontend -- --template react-ts`. No router (one page).
  - `src/config.ts` exports `apiBaseUrl` from `import.meta.env.VITE_API_BASE_URL` and throws
    if it's missing.
  - `src/api/subscriptionsApi.ts` exports types and `createSubscriptionsApi(baseUrl, fetchFn = fetch)`
    with:
    - `getSubscriptions(signal?)` → `Promise<Subscription[] | null>`, where `null` means
      unavailable
    - `addSubscription(url)` → `Promise<AddSubscriptionResult>`, a discriminated union:
      `{kind:"success"} | {kind:"rejected", message} | {kind:"unavailable"}`
  - `src/components/SubscriptionsPage.tsx` holds UI state only.
  - `App.tsx` renders `<SubscriptionsPage />`.
  - Styling is plain CSS in `SubscriptionsPage.css`.
- **Rationale**: Principle IV (one typed API module, thin components). Injecting `fetchFn`
  makes the module testable in Vitest without a DOM.
- **Alternatives considered**: React Query or SWR (not on the approved list, and overkill for
  two calls). React Router (one route).

## R12. Double-submit guard (FR-012)

- **Decision**: Use `isSubmitting` state to disable the button, plus a `useRef<boolean>`
  in-flight flag checked synchronously at the top of the submit handler. The server's atomic
  `addIfAbsent` is the final guard.
- **Rationale**: React state updates are asynchronous, so two fast submit events could both
  see `isSubmitting === false`. The ref closes that gap.

## R13. Rendering safety

- **Decision**: Render `<li>{subscription.url}</li>` (JSX-escaped text) with no `<a>` in the
  MVP and no `dangerouslySetInnerHTML`. Show errors in `<p role="alert">`.
- **Rationale**: FR-009 and Principle II. React 19 also blocks `javascript:` URLs in `href`,
  but the MVP renders no links at all.

## R14. Frontend quality tooling and tests

- **Decision**:
  - Keep the template's linter. create-vite 9 ships **oxlint** (react, typescript and oxc
    plugins) instead of ESLint, so no ESLint is added (Principle I).
  - Add Prettier and Vitest as dev dependencies.
  - `package.json` scripts:
    - `lint`: `oxlint --deny-warnings`
    - `format:check`: `prettier --check .`
    - `format`: `prettier --write .`
    - `typecheck`: `tsc -b`
    - `test`: `vitest run`
    - `build`: as in the template
  - Vitest runs in the `node` environment (Node 24 has `fetch`, `Request` and `Response`
    built in). No jsdom and no component tests in the MVP.
  - `engines.node: ">=24"`, plus `.nvmrc` containing `24`.
- **Rationale**: The constitution requires Vitest tests for the API module's mapping and
  nothing more. Component tests are post-MVP.

## R15. Backend testing approach

- **Decision**: JUnit 5 with AssertJ (from the test starter).
  - **Unit tests**: `SubscriptionUrlValidatorTest` (each rule and the edge-case inputs),
    `InMemorySubscriptionStoreTest` (order, duplicates, snapshot, a 50-thread concurrent
    `addIfAbsent` with exactly one winner) and `SubscriptionServiceTest` (trimming, fixed
    `Clock`, exceptions).
  - **Web tests**: `@SpringBootTest` + `@AutoConfigureMockMvc` with
    `@DirtiesContext(AFTER_EACH_TEST_METHOD)` to isolate the singleton store. Covers 201, 400,
    409, malformed JSON, GET order and shape, and CORS preflight from allowed and disallowed
    origins.
- **Rationale**: A full context exercises the real CORS config, advice and Jackson settings.
  The app is tiny, so rebuilding the context per test is acceptable.
- **Alternatives considered**: `@WebMvcTest` slices (CORS and advice would need manual
  imports). A test-only `clear()` on the store (leaks test concerns into production code).
