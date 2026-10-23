# Quickstart & Validation: Feed Subscription Management (MVP)

This guide proves the feature works end-to-end. Implementation steps live in `tasks.md`.
API details: [contracts/subscriptions-api.md](contracts/subscriptions-api.md).

## Prerequisites

- **.NET 10 SDK** (`dotnet --list-sdks` must show `10.0.x`). The repo's `global.json` pins it.
  At the time of planning, only SDK 6.0.400 was installed. Install .NET 10 first.
- A modern browser with DevTools (F12).

## 1. Build, format and test gates

```powershell
dotnet build RSSFeedReader.slnx            # expect: 0 warnings, 0 errors
dotnet format RSSFeedReader.slnx --verify-no-changes
dotnet test RSSFeedReader.slnx             # expect: all tests pass
```

## 2. Template cleanup gate (must pass before any UI work)

```powershell
Get-ChildItem frontend/RSSFeedReader.UI/Pages -Filter *.razor | Select-Object Name
```

Expected: only `NotFound.razor` and `Subscriptions.razor`. `Home`, `Counter` and `Weather`
are gone. Exactly one file contains `@page "/"`:

```powershell
Select-String -Path frontend/RSSFeedReader.UI/Pages/*.razor -Pattern '@page "/"'
```

## 3. Run

In two terminals:

```powershell
dotnet run --project backend/RSSFeedReader.Api      # listens on http://localhost:5151
dotnet run --project frontend/RSSFeedReader.UI      # serves http://localhost:5213
```

Run gate checks:

- [ ] `http://localhost:5151/api/subscriptions` in a browser returns `[]`
- [ ] `frontend/RSSFeedReader.UI/wwwroot/appsettings.json` has `"ApiBaseUrl": "http://localhost:5151/api/"`
- [ ] Opening `http://localhost:5213` shows no CORS or connection errors in the DevTools console

## 4. Manual acceptance scenarios (open `http://localhost:5213`)

| # | Action | Expected | Spec ref |
|---|--------|----------|----------|
| 1 | Load the page with no data | "No subscriptions yet" message | US2-1, FR-008 |
| 2 | Paste `https://devblogs.microsoft.com/dotnet/feed/`, click **Add** | Appears in the list within 1 s, no reload, input cleared | US1-1, SC-002 |
| 3 | Type `https://example.com/feed`, press **Enter** | Added below the first entry | US1-2, US1-3 |
| 4 | Add `   https://example.org/rss   ` | Shown without the spaces | US1-4 |
| 5 | Click **Add** with an empty input | "Enter a feed address." and the list is unchanged | US3-1 |
| 6 | Add `javascript:alert(1)`, then `not a url` | "Enter a web address starting with http:// or https://.", text kept, no alert | US3-2 |
| 7 | Add `https://EXAMPLE.com/feed` | "This feed is already in your subscription list." | US3-3, FR-005 |
| 8 | Add `https://example.com/?q=<b>x</b>` | Shown literally with `<b>`, not bold | FR-009 |
| 9 | Refresh the browser (F5) | The same list is still shown | US2-3, FR-010 |
| 10 | Stop the backend, then try to add | "Could not add the subscription. Please try again." | US3-4, FR-011 |
| 11 | Restart the backend, reload | The list is empty (expected MVP behavior) | Edge case |
| 12 | Double-click **Add** quickly on a new URL | Exactly one entry added | FR-012 |

## 5. API spot-checks (optional)

```powershell
$api = "http://localhost:5151/api/subscriptions"
Invoke-RestMethod $api -Method Post -ContentType application/json -Body '{"url":"https://example.net/feed"}'
Invoke-RestMethod $api                               # list, oldest first
try { Invoke-RestMethod $api -Method Post -ContentType application/json -Body '{"url":"ftp://x"}' } catch { $_.Exception.Response.StatusCode }   # BadRequest
```

The MVP is **accepted** when sections 1–4 all pass (Constitution: Development Workflow &
Quality Gates, items 1–5).
