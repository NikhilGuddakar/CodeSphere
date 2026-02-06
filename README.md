# CodeSphere IDE

A web-based coding workspace with projects, files, a VS Code-like editor, and an execution panel.

**Tech stack**
- Frontend: React + Vite + CodeMirror 6
- Backend: Spring Boot (JWT auth)
- Database: MySQL

## Quick Start (Docker)
```
docker compose up --build
```

Then open:
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`

## Local Development

### Backend
```
cd Backend
./mvnw spring-boot:run
```

Environment variables (optional):
- `DB_HOST` (default: `localhost`)
- `DB_PORT` (default: `3306`)
- `DB_NAME` (default: `codesphere`)
- `DB_USER` (default: `root`)
- `DB_PASSWORD` (default: `Root@1234`)
- `SHOW_SQL` (default: `false`)
- `CODESPHERE_WORKSPACE` (default: `~/codesphere_workspace`)
- `JWT_SECRET` (default: built-in dev secret)
- `JWT_EXPIRATION_MS` (default: `3600000`)

Production profile (Render/Postgres):
- Set `SPRING_PROFILES_ACTIVE=prod`
- Uses PostgreSQL via `application-prod.properties`

### Frontend
```
cd frontend
npm install
npm run dev
```

Frontend config:
- `VITE_API_BASE_URL` (default: `http://localhost:8080`)

Sample env files:
- `Backend/.env.example`
- `frontend/.env.example`

## Tests

Backend:
```
cd Backend
./mvnw test
```

Frontend:
```
cd frontend
npm test
```

## Features
- JWT authentication (login/register)
- Project and file management
- Code editor with syntax highlighting
- Run Java, Python, JavaScript (Node), C, C++, Go, and C# (when runtimes are installed)
- HTML/CSS/JS preview in the frontend
- Per-project run configuration (main file)
- Search/replace, tabs, command palette

## Notes
- Java and Python execution are supported right now.
- Workspace files are isolated per user under `CODESPHERE_WORKSPACE`.
