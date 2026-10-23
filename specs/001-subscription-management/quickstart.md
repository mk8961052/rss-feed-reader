# Quickstart & Validation: Feed Subscription Management (MVP)

This guide proves the feature works end-to-end. Implementation steps live in `tasks.md`.
API details: [contracts/subscriptions-api.md](contracts/subscriptions-api.md).

## Prerequisites

- **JDK 17+** (`java -version`). Maven doesn't need to be installed, because the backend uses
  `mvnw`.
- **Node.js 24+** (`node -v`) and npm.
- A modern browser with DevTools (F12).

Commands below use PowerShell on Windows (`.\mvnw.cmd`). On macOS/Linux, use `./mvnw`.

## 1. Backend gate

```powershell
cd backend
.\mvnw.cmd verify      # compile (-Xlint:all, no warnings), Spotless check, Enforcer, all tests
```

## 2. Frontend gates

```powershell
cd frontend
npm ci
npm run lint           # oxlint --deny-warnings
npm run format:check
npm run typecheck
npm test               # Vitest, run mode
npm run build
```

## 3. Template cleanup gate (must pass before any UI work)

```powershell
$p = 'count is','get started','hero.png','react.svg','vite.svg','icons.svg'
Get-ChildItem frontend/src -Recurse -File | Select-String -Pattern $p
Select-String -Path frontend/index.html -Pattern $p
```

Expected: **no output** (Select-String is case-insensitive). `frontend/src/assets/` and
`frontend/public/icons.svg` don't exist.

## 4. Run

In two terminals:

```powershell
cd backend;  .\mvnw.cmd spring-boot:run     # http://localhost:8080
cd frontend; npm run dev                    # http://localhost:5173
```

Run gate checks:

- [ ] `http://localhost:8080/api/subscriptions` in a browser returns `[]`
- [ ] `frontend/.env` contains `VITE_API_BASE_URL=http://localhost:8080/api/`
- [ ] Opening `http://localhost:5173` shows no CORS or connection errors in the DevTools console

## 5. Manual acceptance scenarios (open `http://localhost:5173`)

| # | Action | Expected | Spec ref |
|---|--------|----------|----------|
| 1 | Load the page with no data | "No subscriptions yet" | US2-1, FR-008 |
| 2 | Paste `https://devblogs.microsoft.com/dotnet/feed/`, click **Add** | Appears within 1 s, no reload, input cleared | US1-1, SC-002 |
| 3 | Type `https://example.com/feed`, press **Enter** | Added below the first entry | US1-2, US1-3 |
| 4 | Add `   https://example.org/rss   ` | Shown without the spaces | US1-4 |
| 5 | Click **Add** with an empty input | "Enter a feed address." and the list is unchanged | US3-1 |
| 6 | Add `javascript:alert(1)`, then `not a url` | "Enter a web address starting with http:// or https://.", text kept, no alert | US3-2 |
| 7 | Add `https://EXAMPLE.com/feed` | "This feed is already in your subscription list." | US3-3, FR-005 |
| 8 | Add `https://example.com/?q=<b>x</b>` | Shown literally with `<b>`, not bold | FR-009 |
| 9 | Refresh the browser (F5) | The same list is still shown | US2-3, FR-010 |
| 10 | Stop the backend, then try to add | "Could not add the subscription. Please try again." | US3-4, FR-011 |
| 11 | Restart the backend, reload | "No subscriptions yet" (expected MVP behavior) | Edge case |
| 12 | Double-click **Add** quickly on a new URL | Exactly one entry added | FR-012 |

## 6. API spot-checks (optional)

```powershell
$api = "http://localhost:8080/api/subscriptions"
Invoke-RestMethod $api -Method Post -ContentType application/json -Body '{"url":"https://example.net/feed"}'
Invoke-RestMethod $api                                   # list, oldest first
1..100 | % { Invoke-RestMethod $api -Method Post -ContentType application/json -Body "{`"url`":`"https://example.com/feed/$_`"}" } | Out-Null   # scale check (SC-004)
```

The MVP is **accepted** when sections 1–5 all pass (Constitution: Development Workflow &
Quality Gates).
