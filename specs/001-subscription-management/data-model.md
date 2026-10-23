# Data Model: Feed Subscription Management (MVP)

**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md)

All data lives in memory in the backend for the lifetime of the Spring Boot process
(FR-010). The frontend holds only a display copy fetched from the API.

## Entity: Subscription (backend domain record)

`record Subscription(UUID id, String url, String normalizedUrl, Instant addedAt)` in
`com.rssfeedreader.subscription`.

| Field | Type | Rules |
|-------|------|-------|
| `id` | `UUID` | Assigned by the server on creation (`UUID.randomUUID()`). Immutable. |
| `url` | `String` | User input with surrounding whitespace trimmed; 1–2048 chars; absolute `http`/`https` URI with a host (FR-002). Stored and displayed verbatim (FR-009). |
| `normalizedUrl` | `String` | Lowercase scheme + `://` + lowercase host + non-default `:port` + path (`/` if empty) + `?query` + `#fragment` (research R5). Unique across the store (FR-005). **Never serialized** to clients. |
| `addedAt` | `Instant` | UTC, from the injected `Clock` at creation. |

**Invariants**

- No two subscriptions share a `normalizedUrl`.
- The store preserves insertion order. `findAll()` returns oldest first (FR-007).
- Records are immutable. There is no update or delete in the MVP.

**Lifecycle**: `(input) → validated → added`. A rejected input never becomes an entity.

## Value: UrlValidationResult

`SubscriptionUrlValidator.validate(@Nullable String input)` returns a result that is either
valid (with `url` and `normalizedUrl`) or invalid (with `errorMessage`):

| Check (in order) | Condition | Message (constant, verbatim in contract) |
|------------------|-----------|------------------------------------------|
| Empty | input null, empty or blank | "Enter a feed address." |
| TooLong | trimmed length > 2048 (`MAX_URL_LENGTH`) | "The feed address must be 2048 characters or fewer." |
| NotWebAddress | unparseable, scheme not `http`/`https`, or no host | "Enter a web address starting with http:// or https://." |
| Valid | otherwise | carries `url` (trimmed) and `normalizedUrl` |

## Store contract

`SubscriptionStore`:

- `List<Subscription> findAll()`: an immutable snapshot, oldest first
- `boolean addIfAbsent(Subscription s)`: `true` if added, `false` if `normalizedUrl` already
  exists. The check and the insert happen atomically under one lock (research R6).

## Service outcomes

`SubscriptionService.add(@Nullable String rawUrl)` →

- a `Subscription` on success
- `InvalidSubscriptionUrlException(message)` → HTTP 400
- `DuplicateSubscriptionException` → HTTP 409

## Wire DTOs (API ↔ UI)

| Java record (`com.rssfeedreader.subscription`) | TypeScript type (`frontend/src/api/subscriptionsApi.ts`) |
|---|---|
| `AddSubscriptionRequest(@Nullable String url)` | `{ url: string }` (request body) |
| `SubscriptionResponse(UUID id, String url, Instant addedAt)` with `static from(Subscription)` | `Subscription { id: string; url: string; addedAt: string }` |

The mapping from `Subscription` to `SubscriptionResponse` drops `normalizedUrl` (Principle IV).
The JSON uses camelCase field names (Jackson default).

## Frontend result type

```ts
type AddSubscriptionResult =
  | { kind: "success" }
  | { kind: "rejected"; message: string }   // 400 / 409 → ProblemDetail.detail
  | { kind: "unavailable" };                // network error, 5xx, unreadable body
```

## Traceability

| Requirement | Where enforced |
|-------------|----------------|
| FR-002 / FR-004 | `SubscriptionUrlValidator` → `InvalidSubscriptionUrlException` → 400 |
| FR-005 | `normalizedUrl` uniqueness in `InMemorySubscriptionStore.addIfAbsent` → 409 |
| FR-007 | Insertion-ordered list in the store |
| FR-009 | `url` stored verbatim; React renders it as escaped text |
| FR-010 | Singleton store bean lives as long as the JVM process |
| FR-011 | API module maps network errors and 5xx to `unavailable`; the page shows a generic message |
| FR-012 | `isSubmitting` state + in-flight ref in the UI; atomic `addIfAbsent` on the server |
