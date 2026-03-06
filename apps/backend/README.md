# Backend Sprint 0

## Requisiti

- Java 21
- Maven 3.9+
- PostgreSQL raggiungibile via env vars

## Avvio locale

Da root repo:

```bash
mvn -f apps/backend/pom.xml spring-boot:run
```

Variabili supportate:

- `DB_HOST` (default `localhost`)
- `DB_PORT` (default `5432`)
- `DB_NAME` (default `rise`)
- `DB_USER` (default `postgres`)
- `DB_PASSWORD` (default `postgres`)
- `SERVER_PORT` (default `8080`)

## Endpoint Sprint 0

- `GET /api/version`
- `GET /api/health`