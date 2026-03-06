# QA Checklist R1-S01 (US-01 / US-02 / US-03)

## Scope
- R1-S01: autenticazione e gestione utenti (US-01/02/03)
- Ambiente target: locale/dev
- Ruoli coinvolti: Admin, Country Manager, utente disattivato

## Comandi esecuzione (PowerShell)
```powershell
docker compose up -d --build
$env:ADMIN_PASSWORD='Admin123!'
powershell -ExecutionPolicy Bypass -File .\scripts\qa-r1-s01-smoke.ps1 -BaseUrl http://localhost:8080 -AdminEmail admin@rise.local
docker compose down -v
```

## Build & Setup
- [ ] Repository aggiornato e dipendenze installate senza errori
- [ ] Build FE completata con esito positivo
- [ ] Build BE completata con esito positivo
- [ ] Variabili ambiente minime presenti per auth e API
- [ ] Applicazione avviata senza errori bloccanti nei log

## Migration & Data
- [ ] Migrazioni DB applicate con successo
- [ ] Seed dati dev disponibile (incluso `admin@rise.local`)
- [ ] Schema DB allineato alla release R1-S01
- [ ] Nessun errore di connessione DB all’avvio

## API Validation (US-01/02/03)
- [ ] `GET /api/health` risponde UP (200)
- [ ] `GET /api/version` risponde con metadati attesi (`application`, `version`)
- [ ] Login valido (`admin@rise.local` / `Admin123!`) restituisce token
- [ ] 5 tentativi login falliti causano lock account per 15 minuti
- [ ] Login di utente disattivato restituisce errore autorizzativo coerente
- [ ] Create user con payload valido restituisce successo (201/200)
- [ ] Create user con email duplicata restituisce conflitto/errore validazione (409/400)
- [ ] Create Country Manager senza `countryScope` restituisce errore validazione (400)
- [ ] Deactivate utente da admin eseguito con successo
- [ ] Reactivate utente da admin eseguito con successo
- [ ] Self-deactivate (utente su se stesso) bloccato con errore autorizzativo (403/400)

## UI Validation (Smoke funzionale)
- [ ] Form login: credenziali valide portano alla dashboard
- [ ] Form login: messaggio errore chiaro su credenziali invalide
- [ ] Form login: al lockout viene mostrato stato account bloccato
- [ ] Utente disattivato non può accedere via UI
- [ ] Pagina gestione utenti accessibile ad admin
- [ ] Create user via UI con dati validi funziona
- [ ] Validazione UI blocca country manager senza `countryScope`
- [ ] Flusso deactivate/reactivate aggiornato correttamente in UI
- [ ] Self-deactivate da UI non consentito e messaggio coerente

## Security & Authorization
- [ ] Endpoint gestione utenti protetti e non accessibili a ruoli non autorizzati
- [ ] Token auth richiesto sugli endpoint protetti
- [ ] Errori auth coerenti (401 non autenticato, 403 non autorizzato)
- [ ] Nessuna esposizione di stack trace o dettagli sensibili nei messaggi errore

## Smoke Regression Summary
- [ ] Smoke API base passato
- [ ] Smoke login passato
- [ ] Smoke gestione utenti passato
- [ ] Nessun blocker aperto per R1-S01
- [ ] Evidenze QA raccolte (log, screenshot, output script)

## Exit Criteria (DoD)
- [ ] Tutti i test critici US-01/02/03 PASS
- [ ] Nessun difetto Severità 1/2 aperto
- [ ] Report QA condiviso con Dev Coordinator / PO
- [ ] Go/No-Go QA esplicitato
