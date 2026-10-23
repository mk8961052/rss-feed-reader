# Contract: Subscriptions API (MVP)

**Base URL**: `http://localhost:8080/api/` (frontend env var `VITE_API_BASE_URL`)
**Format**: JSON, UTF-8, camelCase properties. Errors are RFC 9457 `application/problem+json`
(Spring omits the default `"type": "about:blank"` member).
**Auth**: none (single local user)
**CORS** (on `/api/**`): allowed origin `http://localhost:5173`; methods `GET`, `POST`; header
`Content-Type`; no credentials.

---

## GET /api/subscriptions

Returns all subscriptions, oldest first.

**200 OK**

```json
[
  {
    "id": "6f1c2b0e-3a4d-4c55-9a8e-2f7d1b0c9e11",
    "url": "https://devblogs.microsoft.com/dotnet/feed/",
    "addedAt": "2026-10-24T09:15:02.123Z"
  }
]
```

An empty store returns `[]` (never `null`). Items never include `normalizedUrl`.

---

## POST /api/subscriptions

**Request** (`Content-Type: application/json`)

```json
{ "url": "  https://devblogs.microsoft.com/dotnet/feed/  " }
```

**201 Created**: `Location: /api/subscriptions/{id}`. The body is one subscription object as
above, with `url` trimmed. The `Location` header is informational; `GET /api/subscriptions/{id}`
isn't part of the MVP.

**400 Bad Request**: invalid address

```json
{
  "title": "Invalid feed address",
  "status": 400,
  "detail": "Enter a web address starting with http:// or https://.",
  "instance": "/api/subscriptions"
}
```

| Input | `detail` |
|-------|----------|
| `{}`, `{"url":null}`, `{"url":""}`, `{"url":"   "}` | `Enter a feed address.` |
| trimmed length > 2048 | `The feed address must be 2048 characters or fewer.` |
| `not a url`, `ftp://example.com/feed`, `javascript:alert(1)`, `/relative/path`, `mailto:a@b.c` | `Enter a web address starting with http:// or https://.` |

Accepted (201), with no feed-validity check: `https://example.com` and
`https://example.com/?q=<b>x</b>`.

**400 Bad Request**: malformed or missing JSON body. This is a generic problem detail from the
framework. The body MUST NOT contain exception class names, stack frames or file paths.

**409 Conflict**: the normalized address already exists

```json
{
  "title": "Already subscribed",
  "status": 409,
  "detail": "This feed is already in your subscription list.",
  "instance": "/api/subscriptions"
}
```

Example: with `https://example.com/feed` stored, `https://EXAMPLE.com/feed` and
`https://example.com:443/feed` → 409. `https://example.com/Feed` → 201 (the path is
case-sensitive).

**500**: unexpected error. Title "Unexpected error", detail "An unexpected error occurred.".
Nothing else is included.

---

## UI behavior contract (single page at `/`)

| API outcome | UI behavior |
|-------------|-------------|
| 201 | Clear the input, re-fetch the list via `GET`, clear any error |
| 400 / 409 | Show `detail` in an alert, keep the input text, leave the list unchanged |
| 400/409 without a readable `detail` | Show "The subscription could not be added." |
| Network failure / 5xx | Show "Could not add the subscription. Please try again.", keep the input |
| GET fails on page load | Show "Could not load subscriptions." in the list area |
| GET returns `[]` | Show "No subscriptions yet" |

While a POST is pending, the **Add** button is disabled and further submits are ignored.
