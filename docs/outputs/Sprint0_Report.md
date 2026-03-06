# Sprint 0 Report

## Scope eseguito (target: SPRINT_0)
Implementato esclusivamente lo scope fisso Sprint 0:
- Scaffolding FE/BE
- Setup Postgres in Docker
- Setup Flyway migration (`V1__init.sql`)
- Endpoint backend `/api/health` e `/api/version`
- Frontend smoke page con chiamata `/api/health`
- Dockerfile FE/BE + `docker-compose.yml`
- Smoke test QA script

## File creati
- `apps/backend/pom.xml`
- `apps/backend/src/main/java/com/rise/backend/BackendApplication.java`
- `apps/backend/src/main/java/com/rise/backend/api/SystemController.java`
- `apps/backend/src/main/java/com/rise/backend/api/VersionResponse.java`
- `apps/backend/src/main/java/com/rise/backend/api/HealthResponse.java`
- `apps/backend/src/main/java/com/rise/backend/service/HealthService.java`
- `apps/backend/src/main/resources/application.yml`
- `apps/backend/Dockerfile`
- `apps/backend/.dockerignore`
- `apps/backend/README.md`
- `apps/frontend/package.json`
- `apps/frontend/angular.json`
- `apps/frontend/tsconfig.json`
- `apps/frontend/tsconfig.app.json`
- `apps/frontend/tsconfig.spec.json`
- `apps/frontend/src/index.html`
- `apps/frontend/src/main.ts`
- `apps/frontend/src/styles.css`
- `apps/frontend/src/app/app.component.ts`
- `apps/frontend/src/app/app.component.html`
- `apps/frontend/Dockerfile`
- `apps/frontend/nginx.conf`
- `apps/frontend/.dockerignore`
- `apps/frontend/README.md`
- `infra/db/migrations/V1__init.sql`
- `docker-compose.yml`
- `infra/docker/README.md`
- `scripts/smoke-test.sh`
- `scripts/QA_Sprint0_Checklist.md`

## Comandi run
```bash
docker compose up -d --build
DB_CONTAINER=rise-db DB_USER=rise bash scripts/smoke-test.sh
docker compose down -v
```

```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1 -DbContainer rise-db -DbUser rise
docker compose down -v
```

## Verifica DoD (Sprint 0)
- Docker compose presente: **PASS**
- Flyway migration presente (`infra/db/migrations/V1__init.sql`): **PASS**
- Endpoint `/api/health` implementato con check DB (`SELECT 1`): **PASS**
- Endpoint `/api/version` implementato: **PASS**
- Frontend smoke page che chiama `/api/health`: **PASS**
- Script smoke QA presente: **PASS**
- Esecuzione `docker compose up -d --build`: **PASS** (confermata: servizi avviati)
- Verifica `/api/health` runtime (`status` + `database`): **PASS** (confermata)
- Esecuzione smoke test QA completo (`scripts/smoke-test.sh`): **PENDING** (resta solo validazione chiavi `/api/version` nello stesso run)

## Stato finale
**PARTIAL / QUASI CHIUSO**

Stato attuale: stack Docker operativo e check health DB/API confermato; resta solo il run completo dello smoke script QA per chiusura definitiva.

## Azione richiesta per chiusura
Eseguire localmente il solo smoke test QA. Se passa, lo stato DoD runtime diventa **PASS** e Sprint 0 è chiudibile.
