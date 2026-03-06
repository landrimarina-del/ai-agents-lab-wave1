# Frontend Sprint 0

Frontend Angular 17 minimale con smoke page che chiama `/api/health`.

## Avvio locale

```bash
cd apps/frontend
npm install
npm start
```

## Build Docker

```bash
docker build -t rise-frontend-sprint0 apps/frontend
docker run --rm -p 8081:80 rise-frontend-sprint0
```

L'immagine usa nginx per servire la SPA e fare proxy `/api` verso `backend:8080`.
