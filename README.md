# RSS Feed Reader (MVP)

A proof-of-concept RSS/Atom feed reader for a single local user. The MVP lets you add a feed
subscription by URL and see your list of subscriptions. Subscriptions are kept in memory, so
they're lost when the backend stops. Feed fetching arrives in the Extended-MVP.

- `backend/`: Spring Boot 4.1 REST API (Java 17, Maven Wrapper) on `http://localhost:8080`
- `frontend/`: React 19 + Vite SPA on `http://localhost:5173`

## Prerequisites

- JDK 17 or newer
- Node.js 24 or newer

## Run

```bash
# Terminal 1
cd backend
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run

# Terminal 2
cd frontend
npm ci
npm run dev
```

Then open <http://localhost:5173>.

## Quality gates

```bash
cd backend && ./mvnw verify                       # compile (-Xlint, no warnings), Spotless, Enforcer, tests
cd frontend && npm run lint && npm run format:check && npm run typecheck && npm test && npm run build
```

For the full validation checklist, see
[specs/001-subscription-management/quickstart.md](specs/001-subscription-management/quickstart.md).
Project rules are in [.specify/memory/constitution.md](.specify/memory/constitution.md).
