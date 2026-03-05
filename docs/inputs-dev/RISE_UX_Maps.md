# RISE Spending Effectiveness – UX Maps
**Versione:** 2.0 | **Data:** 2026-03-04 | **Autore:** UX Mapper Agent

---

## §1 Assunzioni UX

1. **Utenti autenticati via SSO aziendale** – Tutti gli utenti accedono tramite Single Sign-On integrato con l'identity provider aziendale; nessuna gestione password autonoma lato RISE.
2. **Sessione persistente con timeout** – La sessione rimane attiva per 8 ore consecutive; dopo 30 minuti di inattività viene mostrato un avviso con countdown prima del logout automatico.
3. **Lingua predefinita italiano, inglese disponibile** – L'interfaccia è disponibile in italiano e inglese; la lingua viene rilevata dalla preferenza del browser e può essere cambiata in qualsiasi momento dal profilo utente.
4. **Accesso basato su ruolo (RBAC)** – Ogni schermata e ogni azione sono controllate da permessi granulari associati al ruolo (Global Admin, Country Manager, System Admin); i contenuti non autorizzati sono nascosti, non solo disabilitati.
5. **Dati sensibili mascherati per default** – I valori di compensazione individuale (Commission, Bonus, ecc.) sono oscurati di default nella vista lista; l'utente deve cliccare esplicitamente "Mostra valori" per visualizzarli in chiaro, con relativo log di audit.
6. **Responsive ma ottimizzato per desktop** – La piattaforma è responsive, ma la UX principale è progettata per monitor da 1440 × 900 px o superiore; su mobile viene mostrata una vista semplificata in sola lettura.
7. **File di import supportati: CSV e XLSX** – Il sistema accetta esclusivamente file CSV (separatore ";" o ",") e XLSX fino a 50 MB e 500.000 righe per singolo upload.
8. **Feedback in tempo reale tramite SSE** – L'avanzamento dell'import è trasmesso via Server-Sent Events senza necessità di polling; la pagina di progress non richiede refresh manuale.
9. **Template riutilizzabili e versionati** – Ogni template di mapping è salvato con numero di versione incrementale; le versioni precedenti sono archiviate e ripristinabili.
10. **Validazione in due stadi** – Una validazione sintattica avviene lato client (before upload) e una validazione semantica completa avviene lato server durante l'esecuzione dell'import.
11. **Notifiche in-app e via email** – Gli eventi critici (import completato, errori bloccanti, scadenza template) generano notifiche in-app e, se configurato, email all'utente responsabile.
12. **Audit log immutabile** – Tutte le operazioni rilevanti (import, modifica utenti, esportazioni, accessi a dati sensibili) sono registrate in un audit log read-only consultabile dal System Admin.
13. **Internazionalizzazione dei formati numerici** – Il sistema supporta formati numerici con separatore decimale "," (italiano/tedesco) e "." (inglese); il formato viene impostato per ogni fonte dati nel template.
14. **Dashboard con aggiornamento giornaliero** – I dati della dashboard si aggiornano ogni notte tramite batch; è disponibile un pulsante "Aggiorna ora" per forzare il refresh manuale (solo Global Admin e Country Manager).
15. **Export dati soggetto ad approvazione** – L'export completo del database richiede un secondo fattore di autenticazione (conferma via email o OTP) per prevenire estrazione massiva non autorizzata.

---

## §2 Personas

---

### Persona 1 – Giulia Ferretti, Country Manager

| Attributo | Dettaglio |
|---|---|
| **Ruolo** | Country Manager Italia |
| **Ruolo di Sistema** | Country Manager |
| **Età** | 41 anni |
| **Background** | Laurea in Economia Aziendale, 12 anni in HR Operations e Compensation & Benefits. Ha gestito fogli Excel complessi prima dell'adozione di RISE. |
| **Obiettivo Primario** | Caricare mensilmente i dati di compensazione variabile per tutti i negozi italiani, verificare la correttezza dei dati e produrre report per la sede centrale. |
| **Frustrazioni** | Perdita di tempo nel riconciliare formati diversi provenienti da sistemi HR eterogenei; paura di caricare dati errati che impattino i bonus dei dipendenti; mancanza di visibilità sullo stato dell'import dopo l'avvio. |
| **Competenza Tecnica** | Media – usa abitualmente Excel, SAP HR e strumenti di BI; non è sviluppatrice ma è a proprio agio con interfacce strutturate. |
| **KPI Gestiti** | Commission, Quarterly Bonus, Annual Bonus, Total Sales, Monthly Target, FTE per negozio |
| **Citazione chiave** | *"Se carico un file sbagliato e nessuno me lo dice subito, scopro l'errore solo quando arrivano le lamentele dei negozi."* |

**Bisogni da RISE:** Giulia ha bisogno di un processo di upload guidato che la avvisi immediatamente in caso di colonne mancanti o valori anomali. Il template salvato deve permetterle di ripetere il caricamento mensile in pochi click senza dover rimappare le colonne ogni volta. La progress bar con dettaglio in tempo reale le dà la tranquillità di sapere esattamente a che punto si trova il processo. Il report errori scaricabile le consente di correggere i dati a monte e ricaricare in autonomia, senza dover coinvolgere il team tecnico.

---

### Persona 2 – Klaus Bauer, Global Admin

| Attributo | Dettaglio |
|---|---|
| **Ruolo** | VP Compensation & Performance EMEA |
| **Ruolo di Sistema** | Global Admin |
| **Età** | 47 anni |
| **Background** | MBA conseguito a Monaco, 20 anni in Controlling e Workforce Analytics in contesto multinazionale. Gestisce team in 14 paesi EMEA. |
| **Obiettivo Primario** | Monitorare l'efficacia della spesa in compensazione variabile a livello cross-paese, identificare outlier di performance e ottimizzare la distribuzione dei budget di incentivazione. |
| **Frustrazioni** | Difficoltà nel confrontare KPI tra paesi con strutture retributive diverse; troppo tempo speso a consolidare report PowerPoint inviati dai Country Manager; mancanza di un benchmark omogeneo. |
| **Competenza Tecnica** | Alta – usa Power BI, Tableau e strumenti di analisi avanzata; si aspetta filtri potenti, drill-down rapidi e possibilità di export dati per analisi personalizzate. |
| **KPI Gestiti** | Total Sales, HA Sales, Annual Bonus, Extra Booster, Annual Target, benchmark cross-paese, ROI spesa variabile |
| **Citazione chiave** | *"Ho bisogno di vedere in un'unica schermata quale paese sta sovraperformando e quale sta bruciando budget senza risultati."* |

**Bisogni da RISE:** Klaus ha bisogno di una dashboard che aggreghi i dati di tutti i paesi in tempo reale, con filtri a cascata per anno, paese, regione e negozio. I cluster di performance devono essere visualizzati con heatmap comparative. La possibilità di esportare l'intero dataset con un clic (previa autenticazione) gli consente di portare i dati nel suo strumento BI preferito senza dipendere dal team IT. La gestione dei template e degli utenti paese deve essere accessibile senza necessità di aprire ticket al System Admin.

---

### Persona 3 – Priya Nair, System Admin

| Attributo | Dettaglio |
|---|---|
| **Ruolo** | IT Platform Administrator |
| **Ruolo di Sistema** | System Admin |
| **Età** | 38 anni |
| **Background** | Laurea in Computer Science, 10 anni in platform engineering e system administration per applicazioni enterprise B2B. Certificata AWS e Spring Boot specialist. |
| **Obiettivo Primario** | Garantire la stabilità della piattaforma, gestire il provisioning degli utenti, monitorare i log di sistema e configurare i parametri globali dell'applicazione. |
| **Frustrazioni** | Richieste di accesso che arrivano via email senza un flusso formalizzato; difficoltà nel tracciare chi ha effettuato modifiche critiche alla configurazione; mancanza di visibilità sugli import falliti che non vengono segnalati dagli utenti. |
| **Competenza Tecnica** | Molto alta – accede direttamente al database PostgreSQL per query diagnostiche; conosce le API REST di Spring Boot; usa tool di monitoring come Grafana e Prometheus. |
| **KPI Gestiti** | Uptime piattaforma, numero import falliti, tempo medio di elaborazione, numero utenti attivi, volume dati importati |
| **Citazione chiave** | *"Se un import fallisce silenziosamente alle 2 di notte, voglio che il sistema me lo dica prima che lo scopra il Country Manager alle 9."* |

**Bisogni da RISE:** Priya ha bisogno di una sezione Amministrazione completa con gestione utenti (creazione, modifica ruolo, disattivazione), cronologia import con filtri avanzati, audit log esportabile e configurazione parametri di sistema (limiti file, timeout, formati numerici). Gli alert automatici su import falliti o anomalie di sistema le permettono di agire proattivamente. L'interfaccia deve esporre informazioni tecniche dettagliate (stack trace, ID elaborazione, record processati/falliti) nei contesti appropriati.

---

## §3 Journey Maps

---

### Journey 1 – Country Manager: Upload Mensile Dati Compensazione

| Fase | Azione Utente | Risposta Sistema | Touchpoint | Emozione | Pain Points | Opportunità |
|---|---|---|---|---|---|---|
| **1. Accesso** | Giulia apre RISE dal link nel portale aziendale e si autentica via SSO | SSO reindirizza alla dashboard RISE con notifica "Ultimo accesso: ieri 14:32" | Portale aziendale, browser desktop | Neutra, routine | Il link SSO a volte scade e richiede ri-autenticazione | Persistere la sessione più a lungo; banner "sessione in scadenza" anticipato |
| **2. Navigazione Import** | Clicca su "Import" nel menu laterale | Il sistema mostra la pagina Import con storico degli ultimi 5 import e pulsante "Nuovo Import" | Menu navigazione, pagina Import | Focalizzata | Nessun riepilogo dello stato dell'ultimo import mensile in evidenza | Widget "Ultimo Import" nella home dashboard con status e data |
| **3. Avvio Import Wizard** | Clicca "Nuovo Import" | Il Wizard si apre sul passo 1: "Step 1 – Definizione Template" con indicatore di progresso 1/3 | Import Wizard – Step 1 | Concentrata | Non è chiaro se deve creare un nuovo template o usarne uno esistente | Proposta automatica del template più recente compatibile con il mese corrente |
| **4. Selezione Template** | Seleziona il template "IT_Negozi_Mensile_v3" dall'elenco a tendina | Il sistema carica la configurazione del template e mostra il mapping pre-configurato con badge "Verificato" | Dropdown template, anteprima mapping | Sollevata | I template con nomi simili sono difficili da distinguere | Aggiungere data ultimo utilizzo e descrizione al dropdown template |
| **5. Upload File** | Trascina il file "IT_202502_Compensazione.xlsx" nell'area di drop | Il sistema esegue validazione sintattica lato client; mostra anteprima delle prime 5 righe con colonne rilevate | Drag-and-drop area, anteprima file | Attenta | File rifiutato se le colonne hanno nomi leggermente diversi rispetto al template | Proposta automatica di mapping fuzzy per colonne con nomi simili |
| **6. Verifica Mapping** | Controlla il mapping colonne e corregge "Commissioni" → "Commission" | Il sistema aggiorna il mapping in tempo reale e mostra un check verde per ogni campo obbligatorio mappato | Tabella mapping drag-and-drop | Leggermente frustrata | Deve correggere manualmente la stessa colonna ogni mese | Regola condizionale "se contiene 'Commissioni' → mappa su Commission" salvabile nel template |
| **7. Avanzamento Step 2** | Clicca "Avanti" per procedere al passo 2, Registrazione Fonte | Il sistema salva il template aggiornato e mostra la schermata di riepilogo fonte con nome file, dimensione, righe rilevate | Pulsante Avanti, Step 2 | Fiduciosa | Nessuna chiara indicazione di quante righe saranno importate | Mostrare conteggio righe valide/totali prima di procedere |
| **8. Avvio Import** | Clicca "Avvia Import" al passo 3 | Il sistema avvia l'elaborazione e mostra la progress bar SSE con percentuale, record elaborati e record falliti in aggiornamento real-time | Progress bar SSE, Step 3 | Tesa, poi rassicurata dalla barra che avanza | Paura che il browser si chiuda e perda il progresso | Elaborazione server-side indipendente dal browser; riprendibile dall'ID elaborazione |
| **9. Gestione Errori** | Riceve notifica "47 righe con errori" al termine | Il sistema mostra il report errori con lista righe fallite, campo coinvolto e motivo; pulsante "Scarica Report Errori (.xlsx)" | Report errori, pulsante download | Delusa ma informata | I messaggi di errore tecnici non sono comprensibili | Messaggi di errore in linguaggio naturale: "Il valore '—' nella colonna FTE non è numerico (riga 234)" |
| **10. Correzione e Ri-upload** | Scarica il report, corregge il file sorgente e avvia un nuovo import parziale | Il sistema permette di caricare solo le righe fallite correggendo l'import precedente senza sovrascrivere i dati già importati | Flusso re-import parziale | Operativa | Non è chiaro se il re-import sovrascrive o aggiunge | Label esplicita "Modalità correzione: aggiorna solo le righe fallite dell'import #ID" |
| **11. Completamento** | Riceve notifica in-app "Import completato: 15.432 record importati, 0 errori" | Il sistema aggiorna la dashboard con i nuovi dati e invia email di conferma con riepilogo | Notifica in-app, email | Soddisfatta e sollevata | Email di conferma spesso finisce nello spam | Personalizzare il mittente email con dominio aziendale verificato |

---

### Journey 2 – Global Admin: Analisi Benchmark Cross-Paese

| Fase | Azione Utente | Risposta Sistema | Touchpoint | Emozione | Pain Points | Opportunità |
|---|---|---|---|---|---|---|
| **1. Accesso Dashboard** | Klaus accede a RISE e atterra sulla Dashboard Overview | La dashboard mostra KPI globali aggregati (Total Sales, Commission Rate, Budget Variabile) con dati dell'ultimo aggiornamento notturno | Dashboard Overview | Focalizzato | I dati potrebbero essere di ieri sera; nessun timestamp visibile | Mostrare prominentemente "Dati aggiornati al: 2026-03-04 02:15" |
| **2. Selezione Anno** | Seleziona "2025" dal filtro Anno | Il sistema ricarica tutti i KPI e i grafici per l'anno 2025 con animazione di transizione | Filtro Anno (dropdown) | Neutro | Ogni cambio anno ricarica tutta la pagina | Aggiornamento reattivo senza full reload; mantenere scroll position |
| **3. Vista Multi-paese** | Lascia il filtro Paese su "Tutti" e scorre verso il widget Benchmark | Il sistema mostra la tabella benchmark con un'icona per paese, Total Sales, Commission Rate, Annual Bonus medio, Achievement Target% | Widget Benchmark, tabella cross-paese | Interessato | Troppi dati in griglia: difficile individuare outlier | Heatmap condizionale: celle rosse/gialle/verdi in base a threshold configurabili |
| **4. Drill-down Paese** | Clicca sulla riga "Italia" nella tabella benchmark | Il sistema apre il pannello laterale con dettaglio Italia: KPI per regione, trend mensile, top/bottom 5 negozi | Pannello laterale drill-down | Coinvolto | Il pannello laterale è troppo stretto per visualizzare i dati in modo leggibile | Panel espandibile a full-screen con pulsante "apri in nuova tab" |
| **5. Confronto Due Paesi** | Attiva la modalità "Confronto" e seleziona anche "Germania" | Il sistema mostra un grafico a barre side-by-side Italia vs Germania per ogni KPI con delta percentuale | Modalità confronto, grafico comparativo | Analitico | La selezione multipla non è immediata: nessun hint visivo | Checkbox "Confronta" accanto ad ogni riga della tabella benchmark |
| **6. Filtro per Cluster** | Seleziona il cluster "Negozi Premium > 500k€ vendite" dal filtro Cluster | Il sistema filtra entrambi i paesi al sottoinsieme di negozi Premium e ricalcola tutti i KPI | Filtro Cluster (multi-select) | Soddisfatto dalla granularità | La definizione dei cluster non è documentata nell'interfaccia | Tooltip su ogni cluster con criterio di inclusione e numero di negozi |
| **7. Analisi Trend** | Clicca sul grafico trend "Commission Rate % ultimi 12 mesi" | Il sistema espande il grafico con zoom mensile, linea target, annotazioni automatiche sugli scostamenti > 10% | Grafico trend espanso | Curioso | Le annotazioni automatiche sono troppo dense in periodi volatili | Possibilità di mostrare/nascondere le annotazioni; filtro "solo scostamenti > X%" |
| **8. Export Snapshot** | Clicca "Esporta Vista Corrente" per salvare il confronto Italia-Germania filtrato per negozi Premium | Il sistema genera un PDF/XLSX con i grafici e i dati filtrati correnti, con timestamp e filtri applicati nel footer | Pulsante Export Vista, dialog conferma | Produttivo | L'export non include i filtri attivi nel documento generato | Aggiungere sezione "Filtri applicati" come prima pagina del report esportato |
| **9. Export Database Completo** | Clicca "Export Completo DB" dalla toolbar superiore | Il sistema mostra un dialog di conferma con MFA: "Inserisci il codice OTP inviato alla tua email" | Dialog MFA, input OTP | Leggermente rallentato | Il codice OTP arriva dopo 30–60 secondi; scade in 5 minuti | Aggiungere "Reinvia OTP" con countdown; supportare app authenticator come alternativa |
| **10. Ricezione File Export** | Inserisce il codice OTP correttamente | Il sistema genera il file export in background e notifica in-app "Export pronto" con link di download valido 24 ore | Notifica in-app, link download | Soddisfatto | Il file è molto grande (> 200 MB): download lento | Opzione "Comprimi in ZIP" e "Notifica via email quando pronto" per file grandi |
| **11. Condivisione Link** | Vuole condividere la vista corrente con un collega | Il sistema genera un link condivisibile con i filtri correnti incorporati, valido 7 giorni per utenti autenticati | Pulsante "Condividi Vista", clipboard | Collaborativo | Il collega deve avere un account RISE per visualizzare il link | Aggiungere opzione "Condividi come PDF statico" senza necessità di login |

---

## §4 Sitemap

```
RISE Spending Effectiveness
│
├── 🏠 Dashboard
│   ├── Overview (KPI globali + trend)
│   ├── Benchmark Cross-Paese
│   │   ├── Vista Tabella
│   │   ├── Vista Heatmap
│   │   └── Confronto (modalità 2 paesi)
│   ├── Drill-down Negozio
│   │   ├── KPI Negozio
│   │   ├── Trend Mensile
│   │   └── Dettaglio Dipendenti
│   └── Filtri
│       ├── Anno
│       ├── Paese
│       ├── Regione
│       ├── Negozio
│       ├── Dipendenti
│       └── Cluster
│
├── 📥 Import
│   ├── Nuovo Import (Wizard 3 passi)
│   │   ├── Step 1 – Definizione Template
│   │   │   ├── Selezione / Creazione Template
│   │   │   ├── Mapping Colonne (drag-and-drop)
│   │   │   ├── Proposta Automatica Mapping
│   │   │   ├── Regole Condizionali
│   │   │   └── Salvataggio Template
│   │   ├── Step 2 – Registrazione Fonte
│   │   │   ├── Upload File (CSV / XLSX)
│   │   │   ├── Anteprima Dati
│   │   │   └── Validazione Sintattica
│   │   └── Step 3 – Esecuzione Import
│   │       ├── Progress Bar (SSE)
│   │       ├── Log In Tempo Reale
│   │       └── Report Errori (download)
│   ├── Cronologia Import
│   │   ├── Lista Import (filtri: stato / data / utente)
│   │   ├── Dettaglio Import
│   │   └── Re-import Parziale (correzione)
│   └── Gestione Template
│       ├── Lista Template
│       ├── Crea Nuovo Template
│       ├── Modifica Template
│       ├── Versioni Template
│       └── Elimina Template
│
├── 📊 Master Data
│   ├── Dipendenti
│   │   ├── Lista Dipendenti
│   │   └── Dettaglio Dipendente
│   ├── Negozi
│   │   ├── Lista Negozi
│   │   └── Dettaglio Negozio
│   └── Struttura Organizzativa
│       ├── Paesi
│       ├── Regioni
│       └── Cluster
│
├── 📤 Export
│   ├── Export Vista Corrente (PDF / XLSX)
│   ├── Export Completo DB (autenticazione MFA)
│   └── Storico Export
│
└── ⚙️ Amministrazione
    ├── Gestione Utenti
    │   ├── Lista Utenti
    │   ├── Crea Utente
    │   ├── Modifica Utente / Ruolo
    │   └── Disattiva Utente
    ├── Configurazione Sistema
    │   ├── Parametri Globali
    │   │   ├── Formati Numerici per Paese
    │   │   ├── Limiti File Upload
    │   │   └── Timeout Sessione
    │   ├── Notifiche & Alert
    │   └── Integrazioni SSO
    ├── Audit Log
    │   ├── Log Accessi
    │   ├── Log Import
    │   ├── Log Export
    │   └── Log Modifiche Configurazione
    └── Monitor Sistema
        ├── Stato Servizi
        ├── Code Elaborazione
        └── Metriche Performance
```

---

## §5 User Flows

---

### Flow 1 – Happy Path Import con Template Salvato

```
[START: Utente autenticato come Country Manager]
         │
         ▼
[Dashboard] → clicca "Import" nel menu laterale
         │
         ▼
[Pagina Import] → clicca "Nuovo Import"
         │
         ▼
[Wizard Step 1 – Definizione Template]
  → Mostra dropdown "Seleziona Template"
         │
         ▼
[Seleziona template "IT_Negozi_Mensile_v3"]
  → Sistema carica mapping pre-configurato
  → Tutti i campi obbligatori mostrano check ✓
         │
         ▼
[Clicca "Avanti"] → sistema valida template OK
         │
         ▼
[Wizard Step 2 – Registrazione Fonte]
  → Trascina file "IT_202502.xlsx" nell'area drop
  → Validazione sintattica: 0 errori
  → Anteprima prime 5 righe mostrate
         │
         ▼
[Clicca "Avanti"] → sistema registra fonte OK
         │
         ▼
[Wizard Step 3 – Esecuzione Import]
  → Progress bar SSE avanza 0% → 100%
  → Log: "15.432 record elaborati, 0 errori"
         │
         ▼
[Import completato] → notifica in-app + email inviata
         │
         ▼
[Dashboard aggiornata con nuovi dati]
         │
         ▼
[END: Import mensile completato con successo]
```

---

### Flow 1E – Percorso Errori Validazione

```
[START: Wizard Step 3 – Esecuzione Import avviata]
         │
         ▼
[Progress bar SSE avanza]
  → Sistema rileva 47 righe con valori non validi
         │
         ▼
[Import completato con ERRORI PARZIALI]
  → Banner arancione: "Import parziale: 15.385 OK, 47 falliti"
         │
         ▼
[Clicca "Vedi Report Errori"]
  → Modal con tabella: Riga | Campo | Valore | Motivo
  → Es.: Riga 234 | FTE | "—" | "Valore non numerico"
         │
         ▼
[Clicca "Scarica Report Errori (.xlsx)"]
  → Download file con sole righe fallite
         │
         ▼
[Utente corregge le 47 righe nel file originale]
         │
         ▼
[Torna a Import → "Correggi Import #1042"]
  → Sistema mostra modalità Re-import Parziale
  → Upload solo delle righe corrette
         │
         ▼
[Ri-esecuzione import parziale]
  → Progress bar: 47 record elaborati, 0 errori
         │
         ▼
[Notifica: "Correzione completata – Import #1042 ora al 100%"]
         │
         ▼
[END: Import corretto e completato]
```

---

### Flow 2 – Creazione e Salvataggio Nuovo Template

```
[START: Wizard Step 1 – nessun template esistente compatibile]
         │
         ▼
[Clicca "Crea Nuovo Template"]
  → Form: Nome Template, Descrizione, Paese, Tipo Dato
         │
         ▼
[Compila i metadati del template]
  → Nome: "DE_Negozi_Mensile_v1"
  → Paese: Germania | Tipo: Compensazione Mensile
         │
         ▼
[Upload file campione per rilevamento automatico]
  → Sistema analizza intestazioni colonne
  → Propone mapping automatico per i campi riconosciuti
         │
         ▼
[Revisione mapping proposto]
  ┌─────────────────────────────────┐
  │ Mappato automaticamente (12/16) │
  │ Da mappare manualmente (4/16)   │
  └─────────────────────────────────┘
         │
         ▼
[Drag-and-drop per completare il mapping manuale]
  → Trascina "Extra_Boni" → "Extra Booster"
  → Trascina "Jahresziel" → "Annual Target"
  → Trascina "Quartalsziel" → "Quarterly Target"
  → Trascina "HA_Umsatz" → "HA Sales"
         │
         ▼
[Aggiunge regola condizionale]
  → "Se 'Extra_Boni' = 0 → imposta Extra Booster = null"
         │
         ▼
[Clicca "Salva Template"]
  → Sistema salva Template v1 con tutte le configurazioni
  → Toast: "Template 'DE_Negozi_Mensile_v1' salvato con successo"
         │
         ▼
[Template disponibile nel dropdown per i prossimi import]
         │
         ▼
[END: Nuovo template creato e salvato]
```

---

### Flow 3 – Dashboard Drill-down Cross-Paese

```
[START: Global Admin sulla Dashboard Overview]
         │
         ▼
[Seleziona Anno: 2025 dal filtro Anno]
  → KPI globali si aggiornano
         │
         ▼
[Scorre al widget "Benchmark Cross-Paese"]
  → Tabella con 14 paesi EMEA
         │
         ▼
[Attiva toggle "Confronto"]
  → Checkbox compaiono accanto ad ogni riga paese
         │
         ▼
[Seleziona Italia + Germania]
  → Grafico comparativo side-by-side appare
  → Delta % calcolato per ogni KPI
         │
         ▼
[Applica filtro Cluster: "Negozi Premium"]
  → Entrambi i paesi filtrati al sottoinsieme
  → KPI ricalcolati per il cluster selezionato
         │
         ▼
[Clicca su barra "Annual Bonus" Germania]
  → Drill-down: regioni tedesche con Annual Bonus medio
         │
         ▼
[Clicca su "Baviera"]
  → Lista negozi in Baviera con Annual Bonus, Total Sales, Achievement%
         │
         ▼
[Clicca "Esporta Vista Corrente"]
  → PDF generato con: filtri attivi + grafici + tabella dati
         │
         ▼
[END: Analisi benchmark completata ed esportata]
```

---

### Flow 4 – Provisioning Nuovo Utente

```
[START: System Admin in Amministrazione → Gestione Utenti]
         │
         ▼
[Clicca "Crea Nuovo Utente"]
  → Form di provisioning si apre
         │
         ▼
[Compila dati utente]
  → Email: m.rossi@company.com
  → Nome: Marco Rossi
  → Ruolo: Country Manager
  → Paese assegnato: Spagna
         │
         ▼
[Sistema valida email]
  → Verifica che email non sia già registrata
  → Check OK
         │
         ▼
[Seleziona permessi aggiuntivi (opzionale)]
  → Toggle "Può esportare dati" → ON
  → Toggle "Accesso audit log" → OFF
         │
         ▼
[Clicca "Crea Utente"]
  → Sistema crea account e invia email di benvenuto
  → con link di primo accesso SSO (valido 72 ore)
         │
         ▼
[Utente appare nella lista con status "Invito inviato"]
  → Una volta che l'utente accede per la prima volta
  → Status cambia in "Attivo"
         │
         ▼
[System Admin può monitorare l'attivazione]
  → Filtro "In attesa di attivazione" nella lista utenti
         │
         ▼
[END: Nuovo Country Manager provisionato e notificato]
```

---

## §6 Wireframe ASCII

---

### WF-01 – Step 1: Upload e Selezione Tipo Dato

```
┌─────────────────────────────────────────────────────────────────────┐
│  RISE Spending Effectiveness                    👤 Giulia F.  [IT] │
├─────────────────────────────────────────────────────────────────────┤
│  🏠 Dashboard  📥 Import  📊 Master Data  📤 Export  ⚙️ Admin       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  IMPORT WIZARD                                                      │
│  ╔══════════════════════════════════════════════════════════════╗   │
│  ║  ● Step 1: Template    ○ Step 2: Fonte    ○ Step 3: Import  ║   │
│  ╚══════════════════════════════════════════════════════════════╝   │
│                                                                     │
│  SELEZIONA O CREA TEMPLATE                                          │
│  ┌──────────────────────────────────────────────┐  ┌────────────┐  │
│  │ 📋 IT_Negozi_Mensile_v3  ▼  (usato il 3/2/26)│  │ + Crea     │  │
│  └──────────────────────────────────────────────┘  └────────────┘  │
│                                                                     │
│  TIPO DI DATO                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  ◉ Compensazione Mensile    ○ Vendite Mensili               │   │
│  │  ○ Target (Periodo)         ○ Combinato                     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  UPLOAD FILE                                                        │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                                                             │   │
│  │        📁  Trascina qui il tuo file CSV o XLSX              │   │
│  │           oppure  [ Sfoglia file... ]                       │   │
│  │                                                             │   │
│  │        Formati supportati: .csv  .xlsx  |  Max: 50 MB       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │  ℹ️  ANTEPRIMA (prime 5 righe) — disponibile dopo upload  │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                     │
│                                         [ Annulla ]  [ Avanti → ]  │
└─────────────────────────────────────────────────────────────────────┘
```

---

### WF-02 – Step 2: Mapping Colonne

```
┌─────────────────────────────────────────────────────────────────────┐
│  RISE Spending Effectiveness                    👤 Giulia F.  [IT] │
├─────────────────────────────────────────────────────────────────────┤
│  IMPORT WIZARD                                                      │
│  ╔══════════════════════════════════════════════════════════════╗   │
│  ║  ✓ Step 1: Template   ● Step 2: Fonte    ○ Step 3: Import  ║   │
│  ╚══════════════════════════════════════════════════════════════╝   │
│                                                                     │
│  FILE: IT_202502_Compensazione.xlsx  |  15.479 righe rilevate       │
│                                                                     │
│  MAPPING COLONNE  [🔄 Proposta Automatica]  [💾 Salva Template]     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Campo RISE (obbligatorio)    │  Colonna File        │ Stato │  │
│  ├───────────────────────────────┼──────────────────────┼───────┤  │
│  │  Employee ID              (*)  │  EMP_ID             │  ✓   │  │
│  │  Year                     (*)  │  ANNO               │  ✓   │  │
│  │  Month                    (*)  │  MESE               │  ✓   │  │
│  │  Shop Code                (*)  │  COD_NEGOZIO        │  ✓   │  │
│  │  FTE                      (*)  │  ╔══════════════╗   │  ✓   │  │
│  │                                │  ║  FTE_EQUIV   ║   │      │  │
│  │  Commission               (*)  │  ║═══(drag)═════║   │  ✓   │  │
│  │                                │  ║  COMMISSIONI ║   │      │  │
│  │  Quarterly Bonus          (*)  │  ╚══════════════╝   │  ✓   │  │
│  │  Annual Bonus             (*)  │  BONUS_ANNUALE      │  ✓   │  │
│  │  Extra Booster                 │  EXTRA_BONI         │  ✓   │  │
│  │  Other Compensation Type       │  ALTRO_COMP         │  ✓   │  │
│  │  Total Sales              (*)  │  FATTURATO_TOT      │  ✓   │  │
│  │  HA Sales                      │  VENDITE_HA         │  ✓   │  │
│  │  Monthly Target           (*)  │  TARGET_MENSILE     │  ✓   │  │
│  │  Quarterly Target         (*)  │  TARGET_TRIM        │  ✓   │  │
│  │  Annual Target            (*)  │  TARGET_ANNO        │  ✓   │  │
│  │  Other Sales Type              │  ALTRO_VENDITE      │  ⚠️   │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ✅ 15 campi mappati   ⚠️ 1 campo opzionale non mappato             │
│                                                                     │
│  REGOLE CONDIZIONALI  [+ Aggiungi Regola]                           │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  1. Se Extra_Boni = 0 → Extra Booster = null           [ ✕ ]│   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│                                      [ ← Indietro ]  [ Avanti → ]  │
└─────────────────────────────────────────────────────────────────────┘
```

---

### WF-03 – Step 3: Esecuzione e Progress Bar

```
┌─────────────────────────────────────────────────────────────────────┐
│  RISE Spending Effectiveness                    👤 Giulia F.  [IT] │
├─────────────────────────────────────────────────────────────────────┤
│  IMPORT WIZARD                                                      │
│  ╔══════════════════════════════════════════════════════════════╗   │
│  ║  ✓ Step 1: Template   ✓ Step 2: Fonte   ● Step 3: Import   ║   │
│  ╚══════════════════════════════════════════════════════════════╝   │
│                                                                     │
│  RIEPILOGO IMPORT                                                   │
│  ┌────────────────────┬───────────────────────────────────────┐    │
│  │ Template           │ IT_Negozi_Mensile_v3                  │    │
│  │ File               │ IT_202502_Compensazione.xlsx          │    │
│  │ Righe da importare │ 15.479                                │    │
│  │ Campi mappati      │ 15 / 16                               │    │
│  └────────────────────┴───────────────────────────────────────┘    │
│                                                                     │
│  [ ▶ Avvia Import ]                                                 │
│                                                                     │
│  ───────────────────────────── ELABORAZIONE IN CORSO ──────────── │
│                                                                     │
│  ████████████████████████▒▒▒▒▒▒▒▒▒▒▒▒▒▒  62%                      │
│                                                                     │
│  ✅ Record elaborati:   9.597 / 15.479                              │
│  ⚠️  Record con errori:  12                                          │
│  ⏱  Tempo stimato:      ~ 45 secondi                               │
│                                                                     │
│  LOG IN TEMPO REALE                                                 │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  [14:23:01] Elaborazione batch 1/16... OK                  │   │
│  │  [14:23:04] Elaborazione batch 2/16... OK                  │   │
│  │  [14:23:07] Elaborazione batch 3/16... WARN: 3 righe skip  │   │
│  │  [14:23:10] Elaborazione batch 4/16... OK                  │   │
│  │  [14:23:13] Elaborazione batch 5/16... OK            ▼     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ⚠️  L'elaborazione continua anche se chiudi questa finestra.       │
│      Puoi ritrovare lo stato nella Cronologia Import.               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

### WF-04 – Dashboard Overview con KPI Cards

```
┌─────────────────────────────────────────────────────────────────────┐
│  RISE Spending Effectiveness                   👤 Klaus B.  [EMEA] │
├─────────────────────────────────────────────────────────────────────┤
│  🏠 Dashboard  📥 Import  📊 Master Data  📤 Export  ⚙️ Admin       │
├──────────────────────────────────┬──────────────────────────────────┤
│  FILTRI                          │  Anno: [2025 ▼]  Paese: [Tutti▼] │
│  Regione: [Tutte ▼]  Negozio: [Tutti ▼]  Cluster: [Tutti ▼]        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  KPI CARDS        Dati aggiornati al: 2026-03-04 02:15  [🔄 Ora]   │
│                                                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐ │
│  │ TOTAL SALES │  │ COMMISSION  │  │ANNUAL BONUS │  │   FTE     │ │
│  │             │  │   RATE      │  │   MEDIO     │  │  TOTALE   │ │
│  │  €1.24B     │  │   4.8%      │  │  €3.420     │  │  12.847   │ │
│  │  ▲ +3.2%    │  │  ▼ -0.3%   │  │  ▲ +1.1%    │  │  ─ 0.0%  │ │
│  │  vs 2024    │  │  vs 2024    │  │  vs 2024    │  │  vs 2024  │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘ │
│                                                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐ │
│  │  HA SALES   │  │  QUARTERLY  │  │   EXTRA     │  │  TARGET   │ │
│  │             │  │   BONUS     │  │  BOOSTER    │  │  ACHIEV.  │ │
│  │   €184M     │  │  €1.890     │  │   €420      │  │  94.2%   │ │
│  │  ▲ +7.1%    │  │  ▲ +2.4%   │  │  ▼ -5.2%   │  │  ▲ +1.8% │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘ │
│                                                                     │
│  TREND TOTAL SALES – Ultimi 12 mesi                                 │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  120M │     ╭───╮                                           │   │
│  │  110M │  ╭──╯   ╰──╮        ╭───╮                          │   │
│  │  100M │──╯          ╰───────╯   ╰──────                    │   │
│  │   90M │                                                     │   │
│  │       └──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──                │   │
│  │         M A M G L A S O N D G F                            │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

### WF-05 – Dashboard Benchmark Cross-Paese

```
┌─────────────────────────────────────────────────────────────────────┐
│  RISE Spending Effectiveness                   👤 Klaus B.  [EMEA] │
├─────────────────────────────────────────────────────────────────────┤
│  Dashboard → Benchmark Cross-Paese                                  │
│  Anno: [2025]  Cluster: [Negozi Premium ▼]  [🔲 Mostra Confronto]  │
│                                                                     │
│  BENCHMARK PAESI EMEA                                               │
│  ┌────┬────────────┬────────────┬───────────┬──────────┬─────────┐ │
│  │ 🏳 │   Paese    │ Tot.Sales  │ Comm.Rate │ Ann.Bonus│ Achiev% │ │
│  ├────┼────────────┼────────────┼───────────┼──────────┼─────────┤ │
│  │ ☑  │ 🇮🇹 Italia  │ €312M      │  4.9%  🟢 │  €3.650  │  96%  🟢│ │
│  │ ☑  │ 🇩🇪 Germania│ €287M      │  4.7%  🟡 │  €3.810  │  91%  🟡│ │
│  │ ☐  │ 🇫🇷 Francia │ €241M      │  5.1%  🔴 │  €3.200  │  88%  🔴│ │
│  │ ☐  │ 🇪🇸 Spagna  │ €198M      │  4.6%  🟢 │  €2.990  │  93%  🟢│ │
│  │ ☐  │ 🇵🇱 Polonia │ €134M      │  4.3%  🟡 │  €2.100  │  87%  🔴│ │
│  └────┴────────────┴────────────┴───────────┴──────────┴─────────┘ │
│  🟢 > 93%  🟡 88–93%  🔴 < 88%  (threshold configurabili)          │
│                                                                     │
│  CONFRONTO: 🇮🇹 Italia vs 🇩🇪 Germania  [Cluster: Negozi Premium]    │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │        Total Sales    Comm.Rate  Annual Bonus  Achievement  │   │
│  │  ITA  ████████████     ████████    ████████      ██████████ │   │
│  │  DEU  ███████████      ███████     █████████     ████████   │   │
│  │       €312M/€287M     4.9/4.7%  €3.650/€3.810  96%/91%    │   │
│  │                       Δ +0.2pp             Δ +5pp           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│         [ 📤 Esporta Vista ]  [ 🔗 Condividi Link ]                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

### WF-06 – Gestione Utenti: Lista e Form

```
┌─────────────────────────────────────────────────────────────────────┐
│  RISE Spending Effectiveness                  👤 Priya N.  [Admin] │
├─────────────────────────────────────────────────────────────────────┤
│  Amministrazione → Gestione Utenti                                  │
│                                              [ + Crea Nuovo Utente ]│
│  Ricerca: [ 🔍 Cerca per nome o email... ]  Ruolo: [Tutti ▼]        │
│                                                                     │
│  ┌─────┬──────────────────────┬──────────────────┬────────────┬───┐ │
│  │  #  │  Nome                │  Email           │  Ruolo     │   │ │
│  ├─────┼──────────────────────┼──────────────────┼────────────┼───┤ │
│  │  1  │ Giulia Ferretti      │ g.f@company.com  │ Ctry Mgr  │ ✏️ │ │
│  │  2  │ Klaus Bauer          │ k.b@company.com  │ Global Adm│ ✏️ │ │
│  │  3  │ Priya Nair           │ p.n@company.com  │ Sys Admin │ ✏️ │ │
│  │  4  │ Marco Rossi          │ m.r@company.com  │ Ctry Mgr  │ ✏️ │ │
│  │  5  │ Ana García           │ a.g@company.com  │ Ctry Mgr  │ ✏️ │ │
│  └─────┴──────────────────────┴──────────────────┴────────────┴───┘ │
│  Totale: 47 utenti  |  Attivi: 44  |  In attesa: 2  |  Sospesi: 1  │
│                                                                     │
│  ════════════════ FORM: CREA / MODIFICA UTENTE ═════════════════   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Nome *          [ Marco                    ]               │   │
│  │  Cognome *       [ Rossi                    ]               │   │
│  │  Email *         [ m.rossi@company.com      ]               │   │
│  │  Ruolo *         [ Country Manager        ▼ ]               │   │
│  │  Paese assegnato [ Spagna                 ▼ ]               │   │
│  │                                                             │   │
│  │  PERMESSI AGGIUNTIVI                                        │   │
│  │  [✓] Può esportare dati                                     │   │
│  │  [ ] Accesso Audit Log                                      │   │
│  │  [ ] Gestione Template (tutti i paesi)                      │   │
│  │                                                             │   │
│  │              [ Annulla ]  [ 💾 Salva Utente ]               │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

### WF-07 – Cronologia Import

```
┌─────────────────────────────────────────────────────────────────────┐
│  RISE Spending Effectiveness                  👤 Priya N.  [Admin] │
├─────────────────────────────────────────────────────────────────────┤
│  Import → Cronologia Import                                         │
│                                                                     │
│  FILTRI  Data: [01/01/2025 ▼] → [04/03/2026 ▼]  Stato: [Tutti ▼]  │
│          Utente: [Tutti ▼]  Paese: [Tutti ▼]   [ 🔍 Applica ]      │
│                                                                     │
│  ┌──────┬────────────┬─────────────────────────┬───────┬──────────┐ │
│  │  ID  │   Data     │  File                   │ Stato │  Azioni  │ │
│  ├──────┼────────────┼─────────────────────────┼───────┼──────────┤ │
│  │ 1048 │ 04/03/2026 │ IT_202502_Comp.xlsx      │ ✅ OK │ 👁 📥   │ │
│  │ 1047 │ 03/03/2026 │ DE_202502_Comp.xlsx      │ ✅ OK │ 👁 📥   │ │
│  │ 1046 │ 02/03/2026 │ FR_202502_Comp.xlsx      │ ⚠️ PRZ│ 👁 📥 🔄│ │
│  │ 1045 │ 28/02/2026 │ ES_202501_Comp.xlsx      │ ✅ OK │ 👁 📥   │ │
│  │ 1044 │ 27/02/2026 │ IT_202501_Comp.xlsx      │ ❌ ERR│ 👁 📥 🔄│ │
│  └──────┴────────────┴─────────────────────────┴───────┴──────────┘ │
│  ✅ = Completato  ⚠️ PRZ = Parziale  ❌ ERR = Fallito               │
│  👁 = Dettaglio  📥 = Scarica Report  🔄 = Correggi                 │
│                                                                     │
│  DETTAGLIO IMPORT #1046  (cliccato)                                 │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Utente: Marie Dupont  |  Template: FR_Negozi_Mensile_v2    │   │
│  │  Righe totali: 8.234   |  Importate: 8.179  |  Fallite: 55  │   │
│  │  Durata: 2m 14s        |  ID Elaborazione: proc-8f2a        │   │
│  │                                                             │   │
│  │  [ 📥 Scarica Report Errori ]   [ 🔄 Avvia Correzione ]     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## §7 Analytics Events

| ID | Nome Evento | Trigger | Proprietà |
|---|---|---|---|
| EVT-001 | `import_wizard_started` | L'utente clicca "Nuovo Import" | `user_id`, `user_role`, `country`, `timestamp` |
| EVT-002 | `template_selected` | L'utente seleziona un template esistente nel dropdown | `template_id`, `template_name`, `template_version`, `user_id` |
| EVT-003 | `template_created` | L'utente salva un nuovo template | `template_id`, `template_name`, `country`, `mapped_fields_count`, `user_id` |
| EVT-004 | `file_uploaded` | Il file viene caricato e supera la validazione sintattica | `file_name`, `file_size_mb`, `file_type`, `row_count`, `template_id`, `user_id` |
| EVT-005 | `file_upload_failed` | Il file fallisce la validazione client-side | `file_name`, `file_size_mb`, `error_type`, `error_message`, `user_id` |
| EVT-006 | `column_mapping_auto_proposed` | Il sistema propone il mapping automatico | `template_id`, `auto_mapped_count`, `unmatched_count`, `user_id` |
| EVT-007 | `column_mapping_manual_updated` | L'utente modifica manualmente un mapping | `field_name_rise`, `column_name_file`, `action` (drag/select), `user_id` |
| EVT-008 | `conditional_rule_added` | L'utente aggiunge una regola condizionale al template | `template_id`, `rule_condition`, `rule_action`, `user_id` |
| EVT-009 | `import_execution_started` | L'utente clicca "Avvia Import" al passo 3 | `import_id`, `template_id`, `file_name`, `row_count`, `user_id` |
| EVT-010 | `import_execution_completed` | L'import termina con successo (0 errori) | `import_id`, `row_count_success`, `duration_seconds`, `country`, `user_id` |
| EVT-011 | `import_execution_partial` | L'import termina con errori parziali | `import_id`, `row_count_success`, `row_count_failed`, `duration_seconds`, `user_id` |
| EVT-012 | `import_execution_failed` | L'import fallisce completamente | `import_id`, `error_type`, `error_message`, `duration_seconds`, `user_id` |
| EVT-013 | `error_report_downloaded` | L'utente scarica il report errori | `import_id`, `error_count`, `user_id`, `timestamp` |
| EVT-014 | `dashboard_filter_applied` | L'utente applica uno o più filtri alla dashboard | `filter_year`, `filter_country`, `filter_region`, `filter_cluster`, `user_id` |
| EVT-015 | `dashboard_drilldown_opened` | L'utente clicca su un paese / regione / negozio per il drill-down | `entity_type` (paese/regione/negozio), `entity_id`, `entity_name`, `user_id` |
| EVT-016 | `benchmark_comparison_activated` | L'utente attiva la modalità confronto e seleziona due paesi | `country_a`, `country_b`, `cluster`, `year`, `user_id` |
| EVT-017 | `export_view_triggered` | L'utente clicca "Esporta Vista Corrente" | `export_format` (PDF/XLSX), `filters_active`, `user_id`, `timestamp` |
| EVT-018 | `export_db_completed` | L'export completo DB è generato con successo dopo MFA | `export_id`, `file_size_mb`, `row_count`, `user_id`, `timestamp` |
| EVT-019 | `user_provisioned` | Un nuovo utente viene creato dal System Admin | `new_user_id`, `new_user_role`, `country_assigned`, `created_by`, `timestamp` |
| EVT-020 | `sensitive_data_revealed` | L'utente clicca "Mostra valori" per dati di compensazione | `entity_type`, `entity_id`, `user_id`, `timestamp`, `audit_log_id` |

---

<!-- UX_AGENT_SIGNATURE_V1 -->
This section must be present and must not be removed.
