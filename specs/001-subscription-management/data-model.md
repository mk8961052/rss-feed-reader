# Data Model: Feed Subscription Management (MVP)

**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md)

All data lives in memory in the backend for the lifetime of the API process (FR-010).
The frontend holds only a display copy fetched from the API.

## Entity: Subscription (backend domain type)

| Field | Type | Rules |
|-------|------|-------|
| `Id` | `Guid` | Assigned by the server on creation. Immutable. |
| `Url` | `string` | User input with surrounding whitespace trimmed; 1–2048 chars; absolute `http`/`https` URI (FR-002). Stored and displayed verbatim (FR-009). |
| `NormalizedUrl` | `string` | `Uri.AbsoluteUri` of `Url`: lowercase scheme and host, default port removed (research R5). Unique across the store (FR-005). **Never serialized** to clients. |
| `AddedAt` | `DateTimeOffset` | UTC, from the injected `TimeProvider` at creation. |

**Invariants**

- No two subscriptions share a `NormalizedUrl`.
- The store preserves insertion order. `GetAll()` returns oldest first (FR-007).
- Entities are immutable after creation (a C# `sealed record`). There is no update or delete
  in the MVP.

**Lifecycle**: `(input) → validated → added`. A rejected input never becomes an entity. There
are no other state transitions in the MVP. Removal is post-MVP.

## Value: URL validation result

`SubscriptionUrlValidator.Validate(string? input)` → one of:

| Outcome | Condition (checked in order) | User message (contract) |
|---------|------------------------------|-------------------------|
| `Empty` | input null/empty/whitespace | "Enter a feed address." |
| `TooLong` | trimmed length > 2048 | "The feed address must be 2048 characters or fewer." |
| `NotWebAddress` | not an absolute URI, or scheme not `http`/`https` | "Enter a web address starting with http:// or https://." |
| `Valid` | otherwise | carries `Url` (trimmed) and `NormalizedUrl` |

## Value: Store add result

`ISubscriptionStore.TryAdd(Subscription)` → `Added` or `Duplicate`. The duplicate check and
the insert happen atomically under one lock (research R6).

## Wire DTOs (API ↔ UI)

Defined in the API (`Contracts/`) and mirrored in the UI (`Models/`). The JSON is camelCase.
See [contracts/subscriptions-api.md](contracts/subscriptions-api.md).

| DTO | Fields |
|-----|--------|
| `AddSubscriptionRequest` | `url: string?` |
| `SubscriptionResponse` | `id: Guid`, `url: string`, `addedAt: DateTimeOffset` |

Mapping: `Subscription → SubscriptionResponse` excludes `NormalizedUrl` (Principle IV: domain
types are never serialized directly).

## Traceability

| Requirement | Where enforced |
|-------------|----------------|
| FR-002 / FR-004 | `SubscriptionUrlValidator` → 400 |
| FR-005 | `NormalizedUrl` uniqueness in `InMemorySubscriptionStore` → 409 |
| FR-007 | Insertion-ordered list in the store |
| FR-009 | `Url` stored verbatim; UI renders it as encoded text |
| FR-010 | Singleton store lives as long as the API process |
| FR-012 | UI disables submit while a request is pending; atomic `TryAdd` on the server |
