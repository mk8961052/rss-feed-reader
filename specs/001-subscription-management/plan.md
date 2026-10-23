# Implementation Plan: Feed Subscription Management (MVP)

**Branch**: `001-subscription-management` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-subscription-management/spec.md`

**Delivery Phase**: MVP (Constitution v2.0.0, Principle I)

## Summary

Let a single local user add RSS/Atom feed subscriptions by URL and see them listed. A Spring
Boot 4.1 REST API (`backend/`) exposes `GET` and `POST /api/subscriptions`. It:

- validates URL structure (absolute `http`/`https` with a host, ≤ 2048 chars, parsed
  browser-style)
- rejects duplicates by normalized URL
- stores subscriptions in a thread-safe in-memory singleton behind `SubscriptionStore`
- returns RFC 9457 `ProblemDetail` errors

A React 19 + Vite SPA (`frontend/`) provides one page with an input, an Add button (Enter also
submits) and the list. It talks to the API only through a typed API module and always re-reads
the list from the API. There is no feed fetching or parsing (that's Extended-MVP).

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript (frontend, version pinned by the
create-vite `react-ts` template)

**Primary Dependencies**:
- Backend: Spring Boot 4.1.2 (`spring-boot-starter-webmvc`)
- Frontend: React 19 and Vite
- Build/quality: Maven Wrapper, Spotless (google-java-format 1.28.0), Maven Enforcer,
  oxlint (shipped by create-vite 9), Prettier

**Storage**: An in-memory singleton bean (`InMemorySubscriptionStore`, guarded by a
`ReentrantLock`). Data is lost when the JVM restarts (FR-010).

**Testing**: JUnit 5 + AssertJ + MockMvc (`spring-boot-starter-webmvc-test`) for the backend;
Vitest (node environment) for the frontend API module. The UI is validated manually via
[quickstart.md](quickstart.md).

**Target Platform**: Local development on Windows, macOS and Linux. API on
`http://localhost:8080`; UI on the Vite dev server at `http://localhost:5173`.

**Project Type**: Web application (separate backend API and SPA frontend)

**Performance Goals**: An added subscription is visible within 1 s (SC-002), and this holds
with 100+ subscriptions (SC-004). Both are trivial in memory.

**Constraints**:
- No outbound HTTP, feed parsing or persistence (FR-013, Principle I).
- URL ≤ 2048 chars.
- No exception details sent to the client.
- CORS limited to explicit origins on `/api/**`.

**Scale/Scope**: 1 user, 1 page, 2 endpoints, at most a few hundred subscriptions

**Environment check (2026-10-24)**: Zulu JDK 17.0.10, Maven 3.9.12 and Node 24.13.1 are
installed, so no installs are needed.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Pre-design | Post-design |
|-----------|------|------------|-------------|
| I. Phase-Gated Scope | Labeled MVP. No `RestClient`/HTTP client, ROME, JPA or `@Scheduled`. Every dependency is traceable | ✅ | ✅ Backend has only the webmvc starter and its test starter (R2). Frontend adds only Prettier and Vitest to the template (R14) |
| II. Untrusted Input, Safe Output | 400 `ProblemDetail` for blank, > 2048 or non-http(s)/hostless URLs; UI shows `detail`; no `dangerouslySetInnerHTML`; `server.error.*` hardened; catch-all advice | ✅ | ✅ Lenient WHATWG parse (R5), `ApiExceptionHandler` (R7), escaped text with no links (R13) |
| III. Safe Outbound Fetching | Applies from Extended-MVP | N/A | N/A (no outbound calls) |
| IV. Swappable Boundaries | API is the source of truth; `/api/` DTO records; `SubscriptionStore` singleton, thread-safe; thin controller; single TS API module | ✅ | ✅ Lock-guarded store (R6), service plus exceptions (R4), `subscriptionsApi.ts` (R11), re-GET after POST |
| V. Code Quality | `-Xlint:all` + `failOnWarning`; Spotless; `@NullMarked` packages; Enforcer; constructor injection; TS strict; oxlint `--deny-warnings`; Prettier; JUnit tests for validator and store; Vitest for API mapping | ✅ | ✅ R10, R14, R15; messages, limits and ports are constants or config |
| Tech constraints | Java 17 / Boot 4.1.x / mvnw; Node 24 pinned; ports 8080/5173; `VITE_API_BASE_URL` fails fast; `app.cors.allowed-origins` | ✅ | ✅ R1, R9, R11 |
| Workflow gates | Vite template cleanup gate; backend gate; frontend gates; run gate; MVP acceptance | ✅ | ✅ Encoded in [quickstart.md](quickstart.md) |

**Result**: PASS, with no violations and no Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/001-subscription-management/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R15
├── data-model.md        # Phase 1: entity, validation, store, DTOs
├── quickstart.md        # Phase 1: gates, run and acceptance guide
├── contracts/
│   └── subscriptions-api.md   # Phase 1: HTTP + UI behavior contract
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
.gitignore                         # target/, node_modules/, dist/, IDE files, *.local

backend/                           # Spring Boot 4.1.2, Java 17, Maven Wrapper
├── mvnw, mvnw.cmd, .mvn/wrapper/
├── pom.xml                        # webmvc starter + test starter; compiler -Xlint:all/failOnWarning; Spotless; Enforcer
└── src/
    ├── main/
    │   ├── java/com/rssfeedreader/
    │   │   ├── RssFeedReaderApplication.java
    │   │   ├── package-info.java                 # @NullMarked (every package has one)
    │   │   ├── config/
    │   │   │   ├── ClockConfig.java              # Clock.systemUTC() bean
    │   │   │   ├── CorsProperties.java           # @ConfigurationProperties("app.cors") record, non-empty
    │   │   │   └── WebConfig.java                # WebMvcConfigurer: CORS on /api/**
    │   │   ├── web/
    │   │   │   └── ApiExceptionHandler.java      # @RestControllerAdvice → ProblemDetail 400/409/500
    │   │   └── subscription/
    │   │       ├── Subscription.java             # record (id, url, normalizedUrl, addedAt)
    │   │       ├── SubscriptionStore.java        # interface
    │   │       ├── InMemorySubscriptionStore.java# @Repository, ReentrantLock
    │   │       ├── SubscriptionUrlValidator.java # pure, static validate()
    │   │       ├── UrlValidationResult.java
    │   │       ├── SubscriptionService.java      # @Service: validate → create → addIfAbsent
    │   │       ├── InvalidSubscriptionUrlException.java
    │   │       ├── DuplicateSubscriptionException.java
    │   │       ├── AddSubscriptionRequest.java   # record DTO
    │   │       ├── SubscriptionResponse.java     # record DTO, from(Subscription)
    │   │       └── SubscriptionController.java   # @RestController /api/subscriptions GET, POST
    │   └── resources/
    │       └── application.yml                   # server.port, app.cors, problemdetails, server.error.*
    └── test/java/com/rssfeedreader/subscription/
        ├── SubscriptionUrlValidatorTest.java
        ├── InMemorySubscriptionStoreTest.java
        ├── SubscriptionServiceTest.java
        ├── SubscriptionControllerTest.java       # @SpringBootTest + MockMvc, @DirtiesContext
        └── CorsTest.java                         # (in com.rssfeedreader.config)

frontend/                          # Vite react-ts template, cleaned
├── .env                           # VITE_API_BASE_URL=http://localhost:8080/api/
├── .nvmrc                         # 24
├── .prettierrc.json, .prettierignore
├── index.html                     # title "RSS Feed Reader"
├── package.json                   # scripts: dev, build, lint, format, format:check, typecheck, test; engines.node >=24
├── vite.config.ts                 # server.port 5173, strictPort; vitest environment node
├── .oxlintrc.json                 # from template
└── src/
    ├── main.tsx
    ├── App.tsx                    # renders <SubscriptionsPage />
    ├── index.css                  # minimal base styles
    ├── config.ts                  # apiBaseUrl from import.meta.env, fail fast
    ├── vite-env.d.ts              # typed ImportMetaEnv.VITE_API_BASE_URL
    ├── api/
    │   ├── subscriptionsApi.ts    # types + createSubscriptionsApi(baseUrl, fetchFn)
    │   └── subscriptionsApi.test.ts
    └── components/
        ├── SubscriptionsPage.tsx
        └── SubscriptionsPage.css
    # Removed: src/assets/ (hero.png, react.svg, vite.svg), public/icons.svg, App.css, README.md, demo markup
```

**Structure Decision**: Two top-level apps, as mandated by Constitution v2.0.0: `backend/` (a
single Maven module with package root `com.rssfeedreader`, tests under Maven's
`src/test/java`) and `frontend/` (a Vite app with colocated tests). No shared code. The DTO
shape is guarded by backend MockMvc tests and the frontend Vitest mapping tests against
[the contract](contracts/subscriptions-api.md).

## Complexity Tracking

No constitution violations. Nothing to justify.
