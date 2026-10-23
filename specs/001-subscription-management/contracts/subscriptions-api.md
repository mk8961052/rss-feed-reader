# Contract: Subscriptions API (MVP)

**Base URL**: `http://localhost:5151/api/` (frontend config key `ApiBaseUrl`)
**Format**: JSON, UTF-8, camelCase property names
**Auth**: none (single local user)
**CORS**: allowed origins `http://localhost:5213` and `https://localhost:7025`; methods
`GET`, `POST`; header `Content-Type`; no credentials.

---

## GET /api/subscriptions

Returns all subscriptions, oldest first.

**200 OK**

```json
[
  {
    "id": "6f1c2b0e-3a4d-4c55-9a8e-2f7d1b0c9e11",
    "url": "https://devblogs.microsoft.com/dotnet/feed/",
    "addedAt": "2026-10-23T09:15:02.123+00:00"
  }
]
```

An empty store returns `[]` (never `null`).

---

## POST /api/subscriptions

Adds one subscription.

**Request**

```json
{ "url": "  https://devblogs.microsoft.com/dotnet/feed/  " }
```

**201 Created**: `Location: /api/subscriptions/{id}`. The body is a single
`SubscriptionResponse` with `url` trimmed:

```json
{
  "id": "6f1c2b0e-3a4d-4c55-9a8e-2f7d1b0c9e11",
  "url": "https://devblogs.microsoft.com/dotnet/feed/",
  "addedAt": "2026-10-23T09:15:02.123+00:00"
}
```

> The `Location` header is informational. `GET /api/subscriptions/{id}` is **not** part of the
> MVP.

**400 Bad Request**: validation failure (`application/problem+json`):

```json
{
  "type": "https://tools.ietf.org/html/rfc9110#section-15.5.1",
  "title": "One or more validation errors occurred.",
  "status": 400,
  "errors": { "url": [ "Enter a web address starting with http:// or https://." ] }
}
```

| Input | `errors.url[0]` |
|-------|-----------------|
| missing, `""`, `"   "`, or a body without `url` | `Enter a feed address.` |
| trimmed length > 2048 | `The feed address must be 2048 characters or fewer.` |
| `not a url`, `ftp://example.com/feed`, `javascript:alert(1)`, `/relative/path` | `Enter a web address starting with http:// or https://.` |

A malformed JSON body also returns 400 (framework default problem details, no exception text).

**409 Conflict**: the normalized URL already exists (`application/problem+json`):

```json
{
  "type": "https://tools.ietf.org/html/rfc9110#section-15.5.10",
  "title": "Already subscribed",
  "status": 409,
  "detail": "This feed is already in your subscription list."
}
```

Example: with `https://example.com/feed` stored, posting `https://EXAMPLE.com/feed` → 409.
Posting `https://example.com/Feed` → 201 (the path is case-sensitive).

**500**: unexpected error. Generic problem details only, with no exception message or stack
trace (Constitution Principle II).

---

## UI behavior contract (Subscriptions page, route `/`)

| API outcome | UI behavior |
|-------------|-------------|
| 201 | Clear the input, reload the list via `GET`, show no error |
| 400 | Show `errors.url[0]`, keep the input text, leave the list unchanged |
| 409 | Show `detail`, keep the input text, leave the list unchanged |
| Network failure / 5xx | Show "Could not add the subscription. Please try again.", keep the input |
| GET fails on page load | Show "Could not load subscriptions." in the list area |

While a POST is pending, the **Add** button is disabled.
