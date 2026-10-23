<!--
Sync Impact Report
==================
Version change: 1.0.0 → 2.0.0
Bump rationale: MAJOR. The technology stack changed from ASP.NET Core + Blazor WebAssembly to
Spring Boot (Java) + React/Vite (TypeScript). Every .NET-specific mechanic mandated by
Principles II–V and the Technology/Workflow sections has been redefined with a Java or
TypeScript equivalent. Code written against v1.0.0 cannot comply with v2.0.0.

Modified principles (titles unchanged; mechanics redefined):
  - I. Phase-Gated Scope: forbidden-in-MVP list is now RestClient/HttpClient, ROME, JPA, @Scheduled
  - II. Untrusted Input, Safe Output: java.net.URI checks; Spring ProblemDetail; React rendering
    rules (no dangerouslySetInnerHTML); server.error.* hardening
  - III. Safe Outbound Fetching: RestClient via injected RestClient.Builder; manual redirect
    handling; ROME with doctypes disallowed / secure DocumentBuilderFactory
  - IV. Swappable Boundaries: SubscriptionStore interface + Spring singleton bean; React API module
  - V. Strict, Verifiable Code Quality: javac -Xlint -Werror, Spotless, JSpecify @NullMarked,
    Maven Enforcer; TypeScript strict, template linter (oxlint) --deny-warnings, Prettier;
    JUnit 5 + Vitest

Sections modified:
  - Technology & Configuration Constraints: Java 17 / Spring Boot 4.1.x / Maven Wrapper;
    Node 24 / React 19 / Vite; ports 8080 and 5173; app.cors.allowed-origins; VITE_API_BASE_URL
  - Development Workflow & Quality Gates: Vite template cleanup gate replaces Blazor demo-page gate;
    mvnw verify + npm lint/typecheck/test/build gates

Added sections: none
Removed sections: none

Stakeholder documents updated alongside this amendment:
  - StakeholderDocuments/TechStack.md rewritten for Spring Boot + React/Vite
  - StakeholderDocuments/ProjectGoals.md and AppFeatures.md: .NET-specific lines replaced

Dependent artifacts requiring regeneration:
  - specs/001-subscription-management/plan.md, research.md, data-model.md, contracts/,
    quickstart.md, tasks.md (all .NET-specific) — regenerate via /speckit-plan + /speckit-tasks
  - specs/001-subscription-management/spec.md — technology-agnostic; no change required

Deferred TODOs: none
-->

# RSS Feed Reader Constitution

## Core Principles

### I. Phase-Gated Scope (MVP → Extended-MVP → Post-MVP)

Every feature, spec, and task MUST declare which delivery phase it belongs to, as defined in
`StakeholderDocuments/ProjectGoals.md` and `AppFeatures.md`:

- **MVP**: add a subscription by URL; display the subscription list. Nothing else.
- **Extended-MVP**: manual refresh, item display (title + link), a single generic
  "Failed to load feed" error.
- **Post-MVP**: persistence (Spring Data JPA), removal, polling, sanitized HTML, etc.

Rules:

- Work for a later phase MUST NOT start until the current phase's "working" definition is met
  and verified end-to-end in the browser.
- MVP code MUST NOT use `RestClient`, `WebClient`, `java.net.http.HttpClient` or any other
  outbound HTTP client, ROME, Spring Data/JPA, or `@Scheduled`/background tasks. Every Maven
  dependency and npm package MUST be traceable to a requirement of the current phase.
- Speculative abstractions are forbidden unless Principle IV explicitly requires them.

**Rationale**: The project is a fast proof-of-concept. Scope creep is the primary risk to
delivery, and phase labels make every review question ("is this in scope?") answerable.

### II. Untrusted Input, Safe Output (NON-NEGOTIABLE)

Subscription URLs typed by the user and all content returned by remote feeds are untrusted.

- The backend MUST reject a subscription URL that is empty/blank, longer than 2048 characters,
  or not an absolute URI with scheme `http` or `https` (case-insensitive) and a non-empty host
  (parsed leniently, as a browser would, so that unencoded characters such as `<` in a query
  do not cause rejection). This is a structural safety guard, not feed validation: the app still MUST
  NOT check whether the URL is a real RSS/Atom feed in MVP.
- Rejected input MUST return HTTP 400 as an RFC 9457 `ProblemDetail`; the UI MUST show its
  message and MUST NOT add the entry to the list.
- The frontend MUST render all user- and feed-derived text through React's default JSX
  escaping. `dangerouslySetInnerHTML`, direct `innerHTML`/`outerHTML` assignment, and
  `eval`-like APIs are FORBIDDEN until a post-MVP feature introduces a sanitizer (e.g.
  DOMPurify) and a spec explicitly approves its use.
- Any URL rendered as an `href` (subscription or feed item link) MUST be re-checked for an
  `http:`/`https:` protocol at render time (via `new URL(...)`); anything else MUST be shown as
  plain text. External links MUST use `target="_blank" rel="noopener noreferrer"`.
- Error responses MUST NOT expose exception messages, stack traces, or internal paths. The
  backend MUST set `server.error.include-stacktrace=never`,
  `server.error.include-message=never`, and handle unexpected exceptions in a
  `@RestControllerAdvice` that logs server-side and returns a generic `ProblemDetail`.

**Rationale**: A stored `javascript:` URL rendered as a link is stored XSS, even in a
single-user app, and Extended-MVP will render remote content we do not control.

### III. Safe Outbound Fetching (Extended-MVP onward)

When the backend fetches feeds, it acts on user-supplied URLs and parses remote XML.

- HTTP calls MUST use Spring's `RestClient`, built from the injected, auto-configured
  `RestClient.Builder` (one shared, configured client bean); constructing ad-hoc clients per
  request is forbidden.
- Every fetch MUST have explicit connect and read timeouts (default 10 seconds each) and a
  maximum response size (default 5 MB) enforced while reading the body stream (e.g. a bounded
  `InputStream`), never by buffering the whole body first.
- Automatic redirects MUST be disabled; redirects are followed manually up to 5 hops, and every
  hop's target MUST still be `http`/`https`.
- XML MUST be parsed with doctype declarations disallowed: ROME's `SyndFeedInput` with
  `allowDoctypes` left `false` (its secure default), or, for any direct JAXP use, a
  `DocumentBuilderFactory`/`XMLInputFactory` with `disallow-doctype-decl` enabled, external
  entities disabled, and `FEATURE_SECURE_PROCESSING` on. This prevents XXE and
  entity-expansion attacks.
- Fetches to loopback, link-local, and private address ranges SHOULD be blocked; this becomes
  MUST before the app is ever hosted anywhere other than the developer's own machine.
- Only feed item title and link are surfaced in Extended-MVP; item bodies MUST NOT be rendered.

**Rationale**: Unbounded fetching of arbitrary URLs is the classic SSRF and resource-exhaustion
vector, and permissive XML parser defaults are an avoidable XXE risk.

### IV. Swappable Boundaries Between UI, API, and Storage

The architecture MUST allow persistence and feed operations to be added without a rewrite.

- The Spring Boot backend (`backend/`) is the single source of truth for subscriptions. The
  React frontend (`frontend/`) MUST NOT hold an authoritative copy; after a successful add it
  MUST reflect the list returned by the API.
- All UI ↔ API communication MUST go through JSON endpoints under `/api/`
  (e.g. `GET /api/subscriptions`, `POST /api/subscriptions`) using request/response DTOs
  (Java `record`s). Domain or storage types MUST NOT be serialized directly.
- Storage MUST sit behind an interface (e.g. `SubscriptionStore`) implemented by a Spring
  singleton bean. The MVP in-memory implementation MUST be thread-safe (a lock or concurrent
  collection guarding check-then-act operations — an unguarded `ArrayList` is not acceptable),
  because Spring MVC serves requests concurrently.
- Controllers MUST be thin: validation and business rules live in plain, unit-testable classes
  (e.g. a validator and a `SubscriptionService`), not in controller methods.
- Feed fetching/parsing (Extended-MVP) MUST live in a dedicated service (e.g. `FeedService`),
  not in controllers or React components.
- React components MUST contain only UI state and event handling; HTTP calls MUST go through a
  single typed API module (e.g. `src/api/subscriptionsApi.ts`), not ad-hoc `fetch` calls
  scattered across components.

**Rationale**: `TechStack.md` commits to adding Spring Data JPA and scheduled polling later.
Interfaces at these seams make that a bean swap, not a rewrite.

### V. Strict, Verifiable Code Quality

Backend (Java):

- `javac` MUST run with `-Xlint:all` and fail on warnings (`maven-compiler-plugin`
  `failOnWarning=true`); individual lint categories may be excluded only with a recorded reason.
- Formatting MUST be enforced by Spotless (google-java-format) and checked in the build
  (`spotless:check` bound to `verify`).
- Every production package MUST have a `package-info.java` annotated with JSpecify
  `@NullMarked`; nullable values MUST be annotated `@Nullable`. NullAway enforcement SHOULD be
  added once the build JDK is 21+.
- Maven Enforcer MUST require the pinned Java and Maven versions and ban duplicate/conflicting
  dependency versions.
- Constructor injection only; field injection (`@Autowired` on fields) is forbidden.

Frontend (TypeScript):

- `tsconfig` MUST have `"strict": true`; `any` is forbidden except at a validated JSON
  boundary with a comment.
- The linter shipped by the Vite React template (currently oxlint) MUST pass with warnings
  treated as errors (`--deny-warnings`); Prettier formatting MUST pass `--check`.

Both:

- Magic values (ports, URLs, timeouts, size limits, user messages) MUST come from configuration
  or named constants, never inline literals.
- Tests are REQUIRED from MVP onward for security-relevant logic: the URL guard
  (Principle II), the in-memory store's add/list/duplicate behavior including concurrency
  (JUnit 5), and the frontend API module's response-to-result mapping (Vitest). Controller
  tests (`@WebMvcTest` + MockMvc) SHOULD cover every `/api/` route and status code. Broader
  coverage is post-MVP.
- Names MUST describe domain intent (`Subscription`, `FeedItem`, `AddSubscriptionRequest`);
  generic names (`Data`, `Helper`, `Manager`, `Utils`) are not allowed.

**Rationale**: Build-enforced lint, formatting, and nullness rules catch defects at near-zero
cost, keeping a fast-moving POC from accumulating hidden debt.

## Technology & Configuration Constraints

- **Stack**: Spring Boot 4.1.x on Java 17 (LTS) backend; React 19 + TypeScript + Vite frontend
  on Node.js 24 (LTS). Backend builds MUST use the committed Maven Wrapper (`mvnw`); the Java
  release is pinned via `<java.version>` and Maven Enforcer. The Node version is pinned in
  `frontend/.nvmrc` and `package.json` `engines`.
- **Approved dependencies by phase**:
  - MVP backend: the Spring MVC web starter and its matching test starter(s) (test scope), as
    generated by start.spring.io for "Spring Web". Build plugins: compiler, Spotless, Enforcer.
  - MVP frontend: React, React DOM, the Vite React template's dev toolchain (TypeScript,
    oxlint, `@vitejs/plugin-react`), Prettier, Vitest. No router, state library, or UI kit.
  - Extended-MVP: ROME. Post-MVP: Spring Data JPA, OWASP Java HTML Sanitizer, DOMPurify,
    jsoup — only in features whose spec names them.
- **Layout**: `backend/` (single Maven module, package root `com.rssfeedreader`) and
  `frontend/` (Vite app). Backend tests follow Maven convention under `backend/src/test/java`;
  frontend tests are colocated as `*.test.ts(x)`.
- **Ports & origins**: backend `http://localhost:8080` (`server.port` in
  `application.yml`); frontend `http://localhost:5173` (`vite.config.ts`, `strictPort: true`).
- **Frontend API URL**: MUST be read from `import.meta.env.VITE_API_BASE_URL` (defined in
  `frontend/.env`); a missing value MUST fail fast, never fall back to a hardcoded URL.
- **CORS**: MUST list explicit frontend origins from `app.cors.allowed-origins`, applied to
  `/api/**` only via a `WebMvcConfigurer`. `allowedOrigins("*")`, `allowedOriginPatterns("*")`,
  and `allowCredentials(true)` are forbidden unless a spec requires them.
- **Secrets**: none are needed for MVP/Extended-MVP. If any are introduced, they MUST come from
  environment variables (never committed `.env` values for secrets); `frontend/.env` may hold
  only non-secret `VITE_*` settings, since everything in it ships to the browser.
- **Platforms**: the app MUST build and run on Windows, macOS, and Linux; use `mvnw`/`mvnw.cmd`
  and npm scripts, never shell-specific build steps.

## Development Workflow & Quality Gates

1. **Template cleanup gate (Phase 2, blocking)**: before any UI feature work, the Vite template
   demo (counter, "Get started" hero, documentation/social links), its images
   (`src/assets/*`, `public/icons.svg`), template CSS, and template README MUST be removed and
   `index.html` retitled. Verify that a case-insensitive search of `frontend/src` and
   `frontend/index.html` for `count is|get started|hero.png|react.svg|vite.svg|icons.svg`
   returns no matches, then `npm run build` and a browser check showing no console errors.
2. **Backend gate**: `./mvnw verify` MUST succeed — compile with zero warnings, Spotless check,
   Enforcer rules, and all tests.
3. **Frontend gate**: `npm run lint` (zero warnings), `npm run format:check`, `npm run typecheck`,
   `npm test` (Vitest, run mode), and `npm run build` MUST all pass.
4. **Run gate** (from `ProjectGoals.md` local checklist): backend listens on its configured
   port; frontend loads; `VITE_API_BASE_URL` matches the backend; CORS allows the frontend
   origin; browser DevTools shows no connection or CORS errors.
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
  - MAJOR: removing or redefining a principle in a backward-incompatible way (including a
    technology-stack change that redefines mandated mechanics).
  - MINOR: adding a principle or section, or materially expanding guidance.
  - PATCH: clarifications, wording, or typo fixes with no change in meaning.
- **Compliance review**: at each phase boundary (MVP → Extended-MVP → Post-MVP), the team MUST
  re-read this constitution and confirm the phase-specific rules (Principles I and III) still
  match the stakeholder documents.

**Version**: 2.0.0 | **Ratified**: 2026-10-23 | **Last Amended**: 2026-10-24
