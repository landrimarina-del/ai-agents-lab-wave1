# QA Sprint 0 Checklist (DoD)

## Obiettivo
Validare disponibilità base stack applicativo Sprint 0: DB PostgreSQL + endpoint tecnici `/api/health` e `/api/version`.

## Comandi esecuzione

1. Avvio servizi:

```bash
docker compose up -d --build
```

2. Esecuzione smoke test Sprint 0:

```bash
bash scripts/smoke-test.sh
```

Oppure su PowerShell (Windows):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1 -DbContainer rise-db -DbUser rise
```

Opzionale (override variabili):

```bash
BACKEND_URL=http://localhost:8080 MAX_RETRIES=40 SLEEP_SECONDS=2 DB_CONTAINER=<nome_container_db> bash scripts/smoke-test.sh
```

3. Teardown (opzionale):

```bash
docker compose down -v
```

## DoD Sprint 0 (Checklist)

- [ ] I container richiesti da `docker compose` risultano in stato `running`.
- [ ] Lo smoke test `scripts/smoke-test.sh` termina con exit code `0`.
- [ ] Check DB: raggiungibilità PostgreSQL validata via `pg_isready` nel container DB.
- [ ] Check Health: `GET /api/health` risponde e contiene `database = UP` e `status = UP`.
- [ ] Check Version: `GET /api/version` risponde e contiene chiavi `application` e `version`.
- [ ] In caso di errore in qualsiasi check, lo script termina con exit code diverso da `0`.

## Criteri PASS/FAIL

### PASS
- Tutti i check stampano `[PASS]`.
- Riepilogo finale con `FAIL: 0`.
- Codice di uscita script `0`.

### FAIL
- Almeno un check stampa `[FAIL]`.
- Riepilogo finale con `FAIL > 0`.
- Codice di uscita script diverso da `0`.

## Note

- Scope limitato a Sprint 0: non include test funzionali di feature applicative.
- Se necessario, rendere eseguibile lo script in ambiente Linux/WSL:

```bash
chmod +x scripts/smoke-test.sh
```