# Tech stack for RSS Feed Reader

Our RSS feed reader will use a **Spring Boot** REST API backend (Java) and a **React + Vite** single-page application frontend (TypeScript). This combination allows for rapid development of the MVP while supporting future production-ready enhancements.

## Why Spring Boot + React (Vite)?

Building an RSS feed reader with a **Spring Boot** backend and a **React + Vite** frontend offers several advantages:

1. **Quick Development**: Spring Boot's auto-configuration and starters give a working REST API in minutes; Vite gives an instant-start React dev server with hot reload.

2. **Separation of Concerns**: The backend handles data management and (in Extended-MVP) feed operations, while the frontend focuses on user interaction. They communicate only through a JSON REST API.

3. **Cross-Platform**: The JVM and Node.js both run on Windows, macOS, and Linux, so the application can be developed and run on any of them.

4. **Incremental Complexity**: Start with simple subscription management (MVP), then add feed fetching (Extended-MVP), then add persistence and advanced features.

5. **Future-Ready Architecture**: While the MVP is minimal (just subscription list management), this architecture supports adding:

   - Feed fetching and parsing (ROME — `com.rometools:rome`)
   - Database persistence (Spring Data JPA + H2 or PostgreSQL)
   - Background processing (`@Scheduled` tasks for polling)
   - Advanced features (read/unread, folders, etc.)

6. **Familiar Tooling**: Maven for the backend build and npm for the frontend build are mainstream, well-documented toolchains.

## Versions

- **Java**: 17 (LTS) — minimum supported by Spring Boot 4
- **Spring Boot**: 4.1.x (Spring Framework 7)
- **Build (backend)**: Maven 3.9+, via the Maven Wrapper (`mvnw`) committed to the repo
- **Node.js**: 24 (LTS) for the frontend toolchain
- **Frontend**: React 19, TypeScript, Vite

## Responsibilities

For the MVP (subscription management only):

**Backend** is responsible for:

- Exposing a REST API to add subscriptions
- Storing subscriptions in memory
- Returning the list of subscriptions

**Frontend** is responsible for:

- Subscription management UI (input field + add button)
- Displaying the list of subscriptions

For the Extended-MVP (add feed fetching):

**Backend** adds:

- Fetching and parsing RSS/Atom feeds when requested
- Returning feed items to the UI

**Frontend** adds:

- Manual refresh button
- Displaying items (title and link minimum)
- Basic error messages

## MVP-first implementation approach

To deliver the MVP quickly:

**MVP (subscription management only):**

- **Storage**: Use in-memory storage (a thread-safe collection inside a Spring bean). Subscriptions are lost when the app stops.
- **No feed operations**: No HTTP client, no parsing library, no feed fetching
- **Focus**: Basic UI and API communication (add subscription, get subscriptions list)

**Extended-MVP (add feed fetching):**

- **Parsing**: Add ROME (`com.rometools:rome`) for basic RSS/Atom parsing
- **HTTP client**: Use Spring's `RestClient` for fetching feeds
- **Refresh**: Manual only - no background polling or scheduling
- **Error handling**: Simple "failed to load" messages, no detailed diagnostics
- **Content display**: Plain text only (title + link), no HTML rendering needed

This incremental approach makes development extremely fast while keeping the architecture clean for future enhancements.

## Local development

### Project initialization

**Backend** — generate from [start.spring.io](https://start.spring.io) (Maven, Java 17, Spring Boot 4.1.x) with the **Spring Web** dependency only, into `backend/`.

**Frontend** — create with the Vite React + TypeScript template into `frontend/`:

```bash
npm create vite@latest frontend -- --template react-ts
```

### Frontend template cleanup

The Vite React template includes demonstration content that must be removed before MVP features are built.

**⚠️ CRITICAL: This cleanup must be completed in Phase 2 (Foundational) and VERIFIED before any UI feature implementation begins.**

**Required cleanup steps:**

1. **Remove template demo content**:
   - Replace the demo in `frontend/src/App.tsx` (counter, "Get started" hero, documentation/social links) with the MVP page component
   - Delete the template images: `frontend/src/assets/` (`hero.png`, `react.svg`, `vite.svg`) and `frontend/public/icons.svg`
   - Delete `frontend/src/App.css` and `frontend/README.md`, and reset `frontend/src/index.css` to minimal base styles
   - Update the `<title>` in `frontend/index.html` and replace the template favicon

2. **Verify cleanup completion** before proceeding with implementation:

   ```bash
   # Should return no matches
   grep -rniE "count is|get started|hero.png|react.svg|vite.svg|icons.svg" frontend/src frontend/index.html
   ```

3. **Test immediately** after cleanup:

   ```bash
   cd frontend
   npm run build
   npm run dev
   ```

   Open the frontend URL and confirm the browser console (F12) shows no errors (for example, missing image 404s).

**Why this matters:** Leftover template code and assets cause confusing UI, broken asset requests, and dead code that fails lint rules later. Cleaning up first keeps the project focused on business requirements.

### Port configuration

The backend API and frontend UI run on separate localhost ports. **Port consistency is critical** - the ports must be coordinated between these locations:

1. **Backend port** (defined in `backend/src/main/resources/application.yml`):

   - Default: `http://localhost:8080` (`server.port: 8080`)
   - This is where the API listens for requests

2. **Frontend port** (defined in `frontend/vite.config.ts`):

   - Default: `http://localhost:5173` (`server.port: 5173`, `strictPort: true`)
   - This is where the React app is served in development

3. **API base URL** (configured in `frontend/.env`):

   - Must match the backend port from step 1
   - Example: `VITE_API_BASE_URL=http://localhost:8080/api/`

4. **CORS policy** (configured in `backend/src/main/resources/application.yml`, applied by a `WebMvcConfigurer`):

   - Must allow the frontend origin from step 2
   - Example: `app.cors.allowed-origins: http://localhost:5173`

### Configuration best practices

- **Frontend**: Read the API URL from the Vite environment, don't hardcode:

  ```ts
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
  if (!apiBaseUrl) throw new Error("VITE_API_BASE_URL is not configured");
  ```

- **Backend CORS**: Allow only the actual frontend origin(s) from configuration

- **Testing setup**: Before testing, verify:

  1. Backend is running and accessible at the configured port
  2. `frontend/.env` points to the correct backend port
  3. CORS allows the frontend origin

**For MVP:** Test by adding subscription URLs and verifying they appear in the list.

**For Extended-MVP:** Test with a known-good feed like <https://devblogs.microsoft.com/dotnet/feed/>

## Future enhancements (post-MVP)

When ready to extend beyond the basic demonstration, this architecture supports:

- **Database persistence**: Add Spring Data JPA + H2 (file mode) or PostgreSQL for storing subscriptions and items between sessions
- **Background polling**: Use `@Scheduled` tasks to automatically refresh feeds on a schedule
- **HTML sanitization**: Add OWASP Java HTML Sanitizer (backend) and/or DOMPurify (frontend) to safely display rich content from feeds
- **Website-to-feed discovery**: Use jsoup to find feed URLs from website links
- **Better error handling**: Implement retry logic, timeouts, and detailed error messages
- **Testing**: Broaden JUnit 5 / Spring Boot Test coverage and add frontend component tests
- **Optimization**: Implement HTTP caching (ETag/Last-Modified), de-duplication, and performance improvements

## Summary

Spring Boot with a React + Vite frontend provides a straightforward path to building the RSS feed reader incrementally:

- **MVP**: Subscription management only (add + list) - extremely simple, no feed operations
- **Extended-MVP**: Add feed fetching and item display - still simple with in-memory storage and manual refresh
- **Future**: Add persistence, background processing, and advanced features

The architecture is intentionally minimal to enable fast development, while the technology choices support adding production-ready features later without requiring a complete rewrite.
