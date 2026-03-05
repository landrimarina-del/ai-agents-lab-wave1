# RISE Spending Effectiveness – Business Analysis & User Stories

**Versione:** 2.0
**Data:** 2026-03-04
**Autore:** Business Analyst Agent
**Stato:** Draft – Review Richiesta

---

## §1 Glossario

| # | Termine | Definizione |
|---|---------|-------------|
| 1 | **RISE** | Piattaforma B2B enterprise per la gestione della retribuzione variabile e dell'efficacia della spesa del personale di vendita, multi-paese. |
| 2 | **FTE** | Full-Time Equivalent – unità di misura che esprime il carico di lavoro di un dipendente a tempo pieno; campo compensazione centrale nella piattaforma. |
| 3 | **KPI** | Key Performance Indicator – indicatore quantitativo usato per misurare l'efficacia della spesa in retribuzione variabile e il raggiungimento degli obiettivi di vendita. |
| 4 | **Cluster** | Classificazione a 5 livelli (Top / Medium High / Medium / Low / Worst) assegnata ai dipendenti o negozi in base alle performance relative rispetto al benchmark. |
| 5 | **Import Wizard** | Procedura guidata in 3 fasi (Template Definition → Source Registration → Manual Import Execution) per l'importazione di dati di compensazione e vendite. |
| 6 | **Template** | Configurazione per-paese e versionata che definisce il mapping tra colonne sorgente e campi RISE, incluse regole di trasformazione condizionale. |
| 7 | **column_mappings** | Campo JSONB del template che memorizza la corrispondenza tra intestazioni del file sorgente e campi target del modello dati RISE. |
| 8 | **transformation_rules** | Campo JSONB del template contenente le regole condizionali applicate ai valori delle colonne durante il processo di import (es. calcoli derivati, lookup, normalizzazioni). |
| 9 | **oracle_hcm_id** | Identificatore univoco del dipendente proveniente dal sistema Oracle HCM; campo obbligatorio e chiave di riconciliazione con i sistemi HR aziendali. |
| 10 | **Chiave di Deduplicazione** | Combinazione di campi `employee_id + shop_id + record_month + record_year` che identifica univocamente un record; utilizzata per rilevare duplicati in fase di import. |
| 11 | **SKIP** | Strategia di gestione dei duplicati: il record in ingresso viene ignorato e il dato esistente in database viene mantenuto inalterato. |
| 12 | **OVERWRITE** | Strategia di gestione dei duplicati: il record in ingresso sovrascrive il dato esistente in database, tracciando l'operazione nello schema rise_audit. |
| 13 | **Compensation Efficiency** | KPI calcolato come rapporto tra la retribuzione variabile totale erogata e il fatturato generato; misura il costo percentuale delle incentivazioni rispetto ai ricavi. |
| 14 | **rise_core** | Schema PostgreSQL principale contenente tutte le tabelle operative (dipendenti, negozi, paesi, template, import, compensazioni, vendite). |
| 15 | **rise_audit** | Schema PostgreSQL immutabile dedicato alla tracciabilità: registra ogni operazione di scrittura con timestamp, utente, tipo operazione e snapshot dei dati. |
| 16 | **Variable Pay%** | KPI che esprime la percentuale della retribuzione variabile rispetto alla retribuzione totale del dipendente. |
| 17 | **Target Achievement%** | KPI che misura la percentuale di raggiungimento del target di vendita (mensile, trimestrale o annuale) da parte del dipendente o del negozio. |
| 18 | **OIDC/PKCE** | OpenID Connect con Proof Key for Code Exchange – flusso di autenticazione sicuro per Single Page Application, standard adottato da RISE per la gestione delle identità. |
| 19 | **Country Manager** | Ruolo applicativo con visibilità e operatività limitate ai dati del proprio paese di competenza. |
| 20 | **Global Admin** | Ruolo applicativo con accesso completo a tutti i dati, configurazioni e funzionalità della piattaforma, senza restrizioni geografiche. |

---

## §2 Assunzioni

| ID | Assunzione | Impatto se Errata |
|----|-----------|-------------------|
| A-01 | Il sistema Oracle HCM è la fonte di verità per gli identificatori dipendente (`oracle_hcm_id`); RISE non gestisce l'anagrafica principale. | Disallineamento anagrafico, duplicati non rilevabili. |
| A-02 | I file di import supportati sono esclusivamente XLSX, CSV e TSV; altri formati non saranno accettati nella Release 1. | Rifiuto di file validi da sistemi legacy che producono altri formati. |
| A-03 | La dimensione massima del file sorgente è 50 MB e il numero massimo di righe è 50.000 per singola operazione di import. | Necessità di suddivisione manuale dei file o reject di import legittimi. |
| A-04 | L'autenticazione è delegata a un Identity Provider esterno compatibile OIDC; RISE non gestisce credenziali utente. | Blocco totale dell'accesso in caso di indisponibilità del provider esterno. |
| A-05 | I template sono per-paese e versionati; la modifica di un template non retroagisce sugli import già eseguiti con la versione precedente. | Inconsistenza storica se si tenta di ri-processare import con nuovo template. |
| A-06 | Il database PostgreSQL 15+ è gestito con RLS (Row-Level Security) per isolare i dati per paese a livello infrastrutturale. | Potenziale accesso non autorizzato a dati di altri paesi in caso di bug applicativo. |
| A-07 | Il frontend Angular 17 comunica con il backend Spring Boot 3.x esclusivamente tramite API REST JSON; non esistono chiamate dirette al database dal client. | Uso di query dirette non tracciate nello schema rise_audit. |
| A-08 | La lingua dell'interfaccia utente è l'italiano per il tenant principale; il sistema supporta l'internazionalizzazione (i18n) per estensioni future. | Impossibilità di onboardare utenti in paesi non italofoni senza refactoring. |
| A-09 | Il calcolo dei KPI (Compensation Efficiency, Variable Pay%, Cost-to-Revenue%, Target Achievement%) avviene server-side tramite query ottimizzate; il frontend riceve solo i valori già aggregati. | Performance degradate se il calcolo venisse spostato client-side. |
| A-10 | Il cluster (Top/Medium High/Medium/Low/Worst) è calcolato con algoritmo percentile configurabile; i confini predefiniti sono: Top >P90, Med-High P70-P90, Medium P40-P70, Low P20-P40, Worst <P20. | Classificazioni non rappresentative se i percentili non riflettono la distribuzione reale del business. |
| A-11 | Lo schema rise_audit è in sola scrittura per l'applicazione; nessun aggiornamento o cancellazione è consentito sulle tabelle di audit. | Violazione della compliance se le audit trail fossero modificabili. |
| A-12 | Il benchmark cross-paese è disponibile solo per il ruolo Global Admin; Country Manager vede solo il proprio paese. | Data breach se i dati di benchmark di altri paesi fossero esposti al Country Manager. |
| A-13 | La conformità WCAG 2.1 AA si applica a tutte le pagine pubbliche e autenticate del frontend; le eccezioni devono essere documentate e approvate. | Rischio di non conformità normativa e impossibilità di uso da parte di utenti con disabilità. |
| A-14 | Il sistema opera in modalità multi-tenant logica (stesso database, segregazione tramite country_id); non è previsto multi-tenant fisico nella Release 1. | Isolamento insufficiente per clienti con requisiti di data residency stretti. |
| A-15 | Gli import manuali sono l'unica modalità di ingestione dati prevista nella Release 1; l'integrazione automatica via API o SFTP è OUT scope. | Il processo di import rimane manuale e soggetto a errori umani. |
| A-16 | La reportistica degli errori di import è disponibile in formato JSON scaricabile e visualizzabile inline nella UI; non è prevista notifica email automatica nella Release 1. | Gli utenti devono monitorare attivamente la UI per rilevare import falliti. |
| A-17 | Il ciclo retributivo di riferimento è annuale con granularità mensile; i dati sono sempre associati a `record_year` e `record_month`, senza sub-mensile. | Impossibilità di analisi infra-mensili o bisettimanali senza modifiche al modello dati. |

---

## §3 Scope

### IN Scope

1. Autenticazione e autorizzazione tramite OIDC/PKCE con emissione e validazione JWT.
2. Gestione dei ruoli applicativi: Global Admin, Country Manager, System Admin.
3. Anagrafica Dipendenti (CRUD) con campo `oracle_hcm_id` obbligatorio.
4. Anagrafica Negozi (CRUD) con associazione a paese.
5. Anagrafica Paesi (CRUD) gestita da Global Admin e System Admin.
6. Creazione, versionamento e gestione dei Template Import per-paese.
7. Definizione del mapping colonne (`column_mappings` JSONB) tramite interfaccia drag-and-drop.
8. Definizione delle regole di trasformazione condizionale (`transformation_rules` JSONB).
9. Upload di file sorgente XLSX/CSV/TSV (max 50 MB, 50k righe) – Fase 2 Import Wizard.
10. Esecuzione manuale dell'import con validazione sintattica e semantica dei dati.
11. Gestione duplicati con scelta strategia SKIP o OVERWRITE per singola esecuzione.
12. Report errori di import scaricabile in formato JSON con dettaglio riga/colonna.
13. Import di dati di tipo COMPENSATION, SALES e BOTH.
14. Dashboard KPI con indicatori: Compensation Efficiency, Variable Pay%, Cost-to-Revenue%, Target Achievement%.
15. Classificazione Cluster a 5 livelli con algoritmo percentile configurabile.
16. Trend YoY (Year-over-Year) e MoM (Month-over-Month) sui KPI principali.
17. Benchmark cross-paese (solo Global Admin).
18. Export dei dati in formato XLSX/CSV da dashboard e viste dati.
19. Audit trail immutabile su schema rise_audit per ogni operazione di scrittura.
20. Gestione utenti (creazione, modifica, disattivazione) da System Admin.
21. Conformità WCAG 2.1 AA per tutte le schermate applicative.

### OUT Scope

1. Integrazione automatica con Oracle HCM via API o schedulazione automatica.
2. Import automatico tramite SFTP, S3 o altri protocolli di trasferimento file.
3. Gestione delle credenziali utente e Identity Provider interno (delegata a IdP esterno).
4. Motore di payroll o calcolo della busta paga (RISE è di sola analisi e visualizzazione).
5. Notifiche email o push per eventi di sistema (import completato, errori, alert KPI).
6. Mobile application nativa (iOS/Android); il frontend è responsive ma non è una app mobile dedicata.
7. Integrazione con sistemi di BI esterni (Power BI, Tableau, Looker) nella Release 1.
8. Multi-tenant fisico con database separati per cliente.
9. Analisi predittiva o machine learning sui dati di vendita e compensazione.

---

## §4 Matrice Ruoli e Permessi

| Funzionalità | Global Admin | Country Manager | System Admin |
|---|:---:|:---:|:---:|
| Login / Logout | ✓ | ✓ | ✓ |
| Cambio password / gestione sessione | ✓ | ✓ | ✓ |
| Visualizzare lista paesi | ✓ | Solo proprio paese | ✓ |
| Creare / modificare / eliminare paese | ✓ | ✗ | ✓ |
| Visualizzare lista dipendenti | ✓ | Solo proprio paese | ✓ |
| Creare / modificare dipendente | ✓ | Solo proprio paese | ✓ |
| Eliminare dipendente | ✓ | ✗ | ✓ |
| Visualizzare lista negozi | ✓ | Solo proprio paese | ✓ |
| Creare / modificare / eliminare negozio | ✓ | Solo proprio paese | ✓ |
| Creare / modificare template import | ✓ | Solo proprio paese | ✓ |
| Visualizzare template import | ✓ | Solo proprio paese | ✓ |
| Eliminare versione template | ✓ | ✗ | ✓ |
| Eseguire upload file sorgente | ✓ | Solo proprio paese | ✗ |
| Eseguire import (Fase 3 Wizard) | ✓ | Solo proprio paese | ✗ |
| Scegliere strategia SKIP/OVERWRITE | ✓ | Solo proprio paese | ✗ |
| Visualizzare report errori import | ✓ | Solo proprio paese | ✓ |
| Scaricare report errori import (JSON) | ✓ | Solo proprio paese | ✓ |
| Visualizzare KPI Dashboard | ✓ | Solo proprio paese | ✓ |
| Visualizzare benchmark cross-paese | ✓ | ✗ | ✗ |
| Esportare dati (XLSX/CSV) | ✓ | Solo proprio paese | ✓ |
| Gestire utenti (crea/modifica/disattiva) | ✓ | ✗ | ✓ |
| Assegnare ruoli utente | ✓ | ✗ | ✓ |
| Visualizzare audit trail | ✓ | ✗ | ✓ |
| Configurare soglie cluster | ✓ | ✗ | ✓ |

---

## §5 Epiche

### E1 – Autenticazione e Sicurezza
Gestisce l'intero ciclo di vita delle sessioni utente tramite flusso OIDC/PKCE con emissione e validazione JWT. Include la protezione delle API tramite token scope, la revoca sessione, il refresh token e la gestione degli errori di autenticazione. Garantisce che ogni chiamata al backend sia autenticata e autorizzata in base al ruolo.

### E2 – Master Data (Dipendenti, Negozi, Paesi)
Fornisce le funzionalità CRUD per le entità anagrafiche fondamentali: dipendenti (con `oracle_hcm_id` obbligatorio), negozi (con codice shop) e paesi. Gestisce la segregazione dei dati per ruolo e la sincronizzazione con sistemi HCM esterni tramite identificatori stabili. È il fondamento su cui si appoggiano import, KPI e dashboard.

### E3 – Gestione Template Import
Permette la creazione, il versionamento e la gestione dei template di import per-paese. Consente di definire il mapping drag-and-drop delle colonne (`column_mappings`) e le regole di trasformazione condizionale (`transformation_rules`). Supporta la clonazione di template tra versioni e il ripristino di versioni precedenti.

### E4 – Import Wizard – Upload e Mapping
Implementa le fasi 1 e 2 dell'Import Wizard: selezione/associazione del template, upload del file sorgente (XLSX/CSV/TSV ≤50MB/50k righe) e anteprima del mapping colonne. Include validazione del formato file, parsing dell'intestazione e visualizzazione interattiva della corrispondenza colonne sorgente → campi RISE.

### E5 – Import Wizard – Esecuzione e Validazione
Implementa la fase 3 dell'Import Wizard: esecuzione dell'import con validazione sintattica (tipi, obbligatorietà) e semantica (chiave di deduplicazione, referenze). Gestisce la scelta della strategia SKIP/OVERWRITE per i duplicati, la persistenza dei record validi e la generazione del report errori scaricabile in JSON.

### E6 – KPI Dashboard e Analytics
Offre la dashboard principale con i KPI di efficacia della spesa: Compensation Efficiency, Variable Pay%, Cost-to-Revenue%, Target Achievement%. Visualizza i cluster a 5 livelli, i trend YoY/MoM e permette il filtro per paese, negozio, periodo e dipendente. Tutti i calcoli sono server-side su dati importati.

### E7 – Benchmark e Analisi Comparativa
Fornisce ai Global Admin la visione cross-paese dei KPI con comparazione benchmark. Include la visualizzazione di heatmap per paese e cluster, scatter plot performance/costo e drill-down comparativi. La funzionalità è completamente occultata ai ruoli Country Manager e System Admin.

### E8 – Export, Admin e Configurazione
Raggruppa le funzionalità di supporto operativo: export dati in XLSX/CSV, gestione utenti (crea/modifica/disattiva/assegna ruolo), consultazione audit trail immutabile dallo schema rise_audit, configurazione delle soglie cluster e delle impostazioni applicative globali. Include anche il modulo di System Admin per la salute del sistema.

---

## §6 User Stories

### US-01 – Login tramite OIDC/PKCE
**Epica:** E1 | **Priorità:** Must | **Stima:** M | **Dipendenze:** –
**Come** utente della piattaforma RISE **voglio** autenticarmi tramite il flusso OIDC/PKCE con il mio Identity Provider aziendale **per** accedere alle funzionalità in base al mio ruolo senza gestire credenziali separate.
**Criteri di Accettazione:**
- **Dato** che l'utente non è autenticato **quando** accede a qualsiasi URL protetto **allora** viene rediretto alla pagina di login dell'IdP esterno.
- **Dato** che l'IdP ha autenticato con successo l'utente **quando** il callback PKCE viene ricevuto **allora** viene emesso un JWT firmato con claim ruolo e scadenza 1h, e l'utente viene rediretto alla home.
- **Dato** che il JWT è scaduto **quando** l'utente effettua una richiesta API **allora** il sistema tenta il refresh token silenzioso e, in caso di fallimento, reindirizza al login.

---

### US-02 – Logout e revoca sessione
**Epica:** E1 | **Priorità:** Must | **Stima:** S | **Dipendenze:** US-01
**Come** utente autenticato **voglio** effettuare il logout in modo sicuro **per** garantire che la mia sessione non sia riutilizzabile da altri.
**Criteri di Accettazione:**
- **Dato** che l'utente clicca "Esci" **quando** la richiesta di logout viene inviata **allora** il JWT viene invalidato server-side e il refresh token viene revocato sull'IdP.
- **Dato** che la sessione è stata terminata **quando** l'utente tenta di navigare a una pagina protetta **allora** viene rediretto alla pagina di login senza accedere ai dati.
- **Dato** che la sessione scade per timeout (30 minuti di inattività) **quando** l'utente tenta un'azione **allora** appare un modal di avviso 2 minuti prima e il logout avviene automaticamente allo scadere.

---

### US-03 – Gestione anagrafica dipendenti
**Epica:** E2 | **Priorità:** Must | **Stima:** L | **Dipendenze:** US-01
**Come** Global Admin o Country Manager **voglio** creare, visualizzare, modificare e disattivare i dipendenti **per** mantenere aggiornato il master data da cui dipendono import e KPI.
**Criteri di Accettazione:**
- **Dato** che il Global Admin compila il form dipendente con `oracle_hcm_id`, nome, cognome e paese **quando** salva **allora** il record viene persistito in rise_core con audit trail in rise_audit.
- **Dato** che il Country Manager tenta di visualizzare dipendenti di un altro paese **quando** la lista viene caricata **allora** il sistema restituisce solo i dipendenti del proprio paese (filtro RLS).
- **Dato** che un `oracle_hcm_id` duplicato viene inserito **quando** il form viene salvato **allora** appare un messaggio di errore "Dipendente già esistente" e il record non viene creato.

---

### US-04 – Gestione anagrafica negozi
**Epica:** E2 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-01
**Come** Global Admin o Country Manager **voglio** gestire il catalogo dei negozi associati al mio paese **per** garantire che i dati di import e KPI siano correttamente attribuiti alla struttura di vendita.
**Criteri di Accettazione:**
- **Dato** che l'utente crea un negozio con `shop_code`, nome e `country_id` **quando** salva **allora** il negozio appare nella lista filtrata per paese e l'operazione è tracciata in rise_audit.
- **Dato** che un `shop_code` già esistente per lo stesso paese viene inserito **quando** si salva **allora** il sistema mostra un errore di unicità e blocca la creazione.
- **Dato** che un Country Manager tenta di creare un negozio in un paese diverso dal proprio **quando** invia il form **allora** il backend risponde 403 Forbidden e l'operazione non viene eseguita.

---

### US-05 – Creazione template import
**Epica:** E3 | **Priorità:** Must | **Stima:** L | **Dipendenze:** US-01, US-04
**Come** Global Admin o Country Manager **voglio** creare un nuovo template di import per il mio paese, selezionando il tipo (COMPENSATION, SALES, BOTH) **per** definire come i file sorgente verranno mappati ai campi RISE.
**Criteri di Accettazione:**
- **Dato** che l'utente crea un template con nome, paese, tipo e versione 1.0 **quando** salva **allora** il template è disponibile nella lista con stato DRAFT e i campi `column_mappings` e `transformation_rules` sono inizializzati come oggetti JSONB vuoti.
- **Dato** che un template con lo stesso nome e paese esiste già **quando** si tenta la creazione **allora** il sistema suggerisce automaticamente la creazione di una nuova versione (es. 1.1) invece di duplicare.
- **Dato** che il template è in stato PUBLISHED **quando** si tenta di modificare il `column_mappings` **allora** il sistema crea automaticamente un draft della versione successiva senza alterare la versione published.

---

### US-06 – Mapping colonne drag-and-drop
**Epica:** E3 | **Priorità:** Must | **Stima:** L | **Dipendenze:** US-05
**Come** Global Admin o Country Manager **voglio** mappare le colonne del file sorgente ai campi RISE tramite interfaccia drag-and-drop **per** configurare il template senza scrivere codice o JSONB manualmente.
**Criteri di Accettazione:**
- **Dato** che il template è in stato DRAFT **quando** l'utente trascina una colonna sorgente su un campo RISE target **allora** il `column_mappings` JSONB viene aggiornato in tempo reale e visualizzato in anteprima.
- **Dato** che un campo obbligatorio RISE (es. `employee_id`) non è mappato **quando** l'utente tenta di pubblicare il template **allora** il sistema mostra un errore "Campi obbligatori non mappati: [lista]" e blocca la pubblicazione.
- **Dato** che l'utente carica un file campione CSV **quando** l'intestazione viene parsed **allora** le colonne sorgente appaiono come elementi trascinabili con nome originale della colonna e tipo inferito.

---

### US-07 – Definizione regole di trasformazione condizionale
**Epica:** E3 | **Priorità:** Should | **Stima:** L | **Dipendenze:** US-06
**Come** Global Admin o Country Manager **voglio** definire regole di trasformazione condizionale (`transformation_rules`) nel template **per** normalizzare o calcolare valori derivati durante l'import senza modificare i file sorgente.
**Criteri di Accettazione:**
- **Dato** che l'utente aggiunge una regola con condizione `IF colonna_A > 0 THEN colonna_B = colonna_A * 0.1` **quando** salva **allora** la regola è persistita nel JSONB `transformation_rules` con struttura type/condition/target/expression.
- **Dato** che una regola ha un'espressione non valida **quando** si tenta il salvataggio **allora** il validatore sintattico lato frontend mostra l'errore inline prima dell'invio al backend.
- **Dato** che esistono più regole **quando** vengono eseguite in fase di import **allora** vengono applicate nell'ordine di priorità configurato (drag to reorder).

---

### US-08 – Versionamento e pubblicazione template
**Epica:** E3 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-05, US-06
**Come** System Admin **voglio** gestire il ciclo di vita dei template (DRAFT → PUBLISHED → DEPRECATED) **per** garantire che gli import usino sempre la versione approvata e che le versioni storiche siano preservate.
**Criteri di Accettazione:**
- **Dato** che un template è in stato DRAFT **quando** il System Admin lo pubblica **allora** lo stato diventa PUBLISHED, la versione precedente PUBLISHED viene deprecata automaticamente e l'operazione è tracciata in rise_audit.
- **Dato** che un template PUBLISHED è in uso da un import in corso **quando** si tenta la deprecazione **allora** il sistema blocca l'operazione con messaggio "Template in uso: impossibile deprecare".
- **Dato** che si richiede l'elenco dei template per un paese **quando** la lista viene caricata **allora** sono visibili tutte le versioni con stato, data pubblicazione e autore.

---

### US-09 – Upload file sorgente (Fase 2 Import Wizard)
**Epica:** E4 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-05, US-08
**Come** Global Admin o Country Manager **voglio** caricare un file XLSX/CSV/TSV nella Fase 2 dell'Import Wizard **per** registrare la sorgente da importare associata al template selezionato.
**Criteri di Accettazione:**
- **Dato** che l'utente carica un file XLSX di 30 MB con 20.000 righe **quando** il caricamento è completato **allora** il sistema conferma il successo, mostra l'anteprima delle prime 10 righe e il conteggio totale.
- **Dato** che il file supera 50 MB o 50.000 righe **quando** viene selezionato **allora** il client blocca il caricamento con messaggio "File non conforme: dimensione massima 50 MB / 50.000 righe".
- **Dato** che il formato del file non è XLSX/CSV/TSV **quando** viene trascinato nella drop-zone **allora** il sistema rifiuta il file con messaggio "Formato non supportato. Formati accettati: XLSX, CSV, TSV".

---

### US-10 – Anteprima e validazione mapping (Fase 2)
**Epica:** E4 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-09
**Come** Global Admin o Country Manager **voglio** visualizzare l'anteprima del mapping colonne tra il file caricato e il template selezionato **per** verificare la correttezza prima di procedere all'esecuzione.
**Criteri di Accettazione:**
- **Dato** che il file è caricato e il template è selezionato **quando** viene generata l'anteprima **allora** ogni colonna sorgente mostra il campo RISE target mappato, il tipo atteso e un campione dei primi 5 valori.
- **Dato** che una colonna obbligatoria nel template non è presente nel file sorgente **quando** l'anteprima viene calcolata **allora** la colonna appare evidenziata in rosso con messaggio "Colonna obbligatoria assente nel file sorgente".
- **Dato** che il mapping è valido **quando** l'utente clicca "Procedi all'esecuzione" **allora** lo stato della Source Registration diventa READY e l'utente accede alla Fase 3.

---

### US-11 – Esecuzione import e validazione (Fase 3)
**Epica:** E5 | **Priorità:** Must | **Stima:** L | **Dipendenze:** US-10
**Come** Global Admin o Country Manager **voglio** eseguire l'import in Fase 3 del wizard con validazione completa **per** assicurarmi che solo dati conformi vengano persistiti nel database.
**Criteri di Accettazione:**
- **Dato** che l'utente avvia l'esecuzione **quando** il processo parte **allora** appare una progress bar con aggiornamenti ogni 500 righe processate e stima del tempo rimanente.
- **Dato** che una riga fallisce la validazione sintattica (es. valore non numerico in campo numerico) **quando** il record viene processato **allora** viene aggiunto al report errori con dettaglio: numero riga, nome colonna, valore ricevuto, motivo errore.
- **Dato** che l'import è completato **quando** si visualizza il riepilogo **allora** appaiono: righe totali, righe importate con successo, righe con errore, righe saltate (SKIP), righe sovrascritte (OVERWRITE).

---

### US-12 – Gestione duplicati SKIP/OVERWRITE
**Epica:** E5 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-11
**Come** Global Admin o Country Manager **voglio** scegliere la strategia di gestione duplicati (SKIP o OVERWRITE) prima di eseguire l'import **per** controllare esplicitamente cosa accade ai dati esistenti con la stessa chiave di deduplicazione.
**Criteri di Accettazione:**
- **Dato** che l'utente seleziona SKIP e il file contiene 10 righe duplicate **quando** l'import viene eseguito **allora** le 10 righe vengono saltate, i dati esistenti non vengono modificati e il report mostra "10 righe saltate (SKIP)".
- **Dato** che l'utente seleziona OVERWRITE e il file contiene 5 righe duplicate **quando** l'import viene eseguito **allora** i 5 record esistenti vengono aggiornati, l'operazione è tracciata in rise_audit con snapshot pre/post e il report mostra "5 righe sovrascritte (OVERWRITE)".
- **Dato** che l'utente non seleziona alcuna strategia **quando** tenta di avviare l'import **allora** il pulsante "Esegui Import" è disabilitato e appare il messaggio "Seleziona la strategia per i duplicati".

---

### US-13 – Report errori import scaricabile
**Epica:** E5 | **Priorità:** Must | **Stima:** S | **Dipendenze:** US-11
**Come** Global Admin o Country Manager **voglio** scaricare il report degli errori di import in formato JSON **per** analizzare e correggere i dati sorgente al di fuori della piattaforma.
**Criteri di Accettazione:**
- **Dato** che un import è completato con errori **quando** l'utente clicca "Scarica Report Errori" **allora** viene scaricato un file JSON con array di oggetti `{row, column, value, error_code, error_message}`.
- **Dato** che l'import non ha prodotto errori **quando** l'utente clicca "Scarica Report Errori" **allora** il file JSON scaricato contiene un array vuoto e il messaggio `"status": "SUCCESS_NO_ERRORS"`.
- **Dato** che il report è disponibile **quando** viene visualizzato inline nella UI **allora** i primi 50 errori sono mostrati in una tabella paginata con possibilità di filtrare per colonna o codice errore.

---

### US-14 – Dashboard KPI principale
**Epica:** E6 | **Priorità:** Must | **Stima:** L | **Dipendenze:** US-11
**Come** Global Admin o Country Manager **voglio** accedere a una dashboard con i KPI di efficacia della spesa **per** monitorare in tempo reale la performance delle retribuzioni variabili rispetto alle vendite.
**Criteri di Accettazione:**
- **Dato** che l'utente accede alla dashboard **quando** la pagina si carica **allora** vengono visualizzati Compensation Efficiency, Variable Pay%, Cost-to-Revenue% e Target Achievement% aggregati per il periodo e il paese correnti entro 3 secondi.
- **Dato** che il Country Manager accede alla dashboard **quando** i dati vengono caricati **allora** sono visibili solo i KPI del suo paese; qualsiasi richiesta di dati di altri paesi restituisce 403.
- **Dato** che non ci sono dati per il periodo selezionato **quando** la dashboard viene caricata **allora** appare un messaggio "Nessun dato disponibile per il periodo selezionato" invece di valori a zero.

---

### US-15 – Filtri temporali e geografici dashboard
**Epica:** E6 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-14
**Come** utente della dashboard **voglio** filtrare i KPI per anno, mese, paese (Global Admin), negozio e dipendente **per** analizzare performance a diversi livelli di granularità.
**Criteri di Accettazione:**
- **Dato** che l'utente seleziona Anno = 2025 e Mese = Q3 **quando** i filtri vengono applicati **allora** tutti i KPI e i cluster si aggiornano mostrando dati esclusivamente relativi a luglio-settembre 2025.
- **Dato** che il Global Admin seleziona "Tutti i paesi" **quando** i dati vengono aggregati **allora** i KPI mostrano la media aggregata cross-paese e ogni KPI indica il numero di paesi inclusi.
- **Dato** che l'utente seleziona un singolo negozio **quando** il filtro viene applicato **allora** i KPI mostrano esclusivamente i dati del negozio selezionato con drill-down fino al singolo dipendente.

---

### US-16 – Classificazione Cluster a 5 livelli
**Epica:** E6 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-14
**Come** Global Admin o Country Manager **voglio** visualizzare la distribuzione dei dipendenti nei 5 cluster di performance **per** identificare rapidamente i top performer e i casi critici su cui intervenire.
**Criteri di Accettazione:**
- **Dato** che i dati sono disponibili per il periodo e il paese selezionati **quando** il cluster viene calcolato **allora** ogni dipendente è classificato in: Top (>P90), Medium High (P70-P90), Medium (P40-P70), Low (P20-P40), Worst (<P20) in base al Target Achievement%.
- **Dato** che l'utente clicca su un cluster **quando** la vista dettaglio viene aperta **allora** appare la lista dei dipendenti in quel cluster con nome, negozio, Target Achievement% e KPI principali.
- **Dato** che le soglie cluster vengono modificate da System Admin **quando** la dashboard viene ricaricata **allora** il ricalcolo avviene automaticamente con le nuove soglie e appare un badge "Ricalcolato con soglie aggiornate".

---

### US-17 – Trend YoY e MoM
**Epica:** E6 | **Priorità:** Should | **Stima:** M | **Dipendenze:** US-14
**Come** Global Admin o Country Manager **voglio** visualizzare il trend YoY (anno su anno) e MoM (mese su mese) dei KPI **per** capire se le performance stanno migliorando o peggiorando rispetto ai periodi precedenti.
**Criteri di Accettazione:**
- **Dato** che sono disponibili dati per almeno due periodi **quando** il grafico trend viene renderizzato **allora** il sistema mostra una linea chart con variazione percentuale e freccia direzionale (↑ verde / ↓ rosso) per ogni KPI.
- **Dato** che i dati per il periodo precedente non sono disponibili **quando** il trend viene calcolato **allora** il sistema mostra "N/D" invece di un valore delta e spiega il motivo nella tooltip.
- **Dato** che l'utente passa dalla vista MoM a YoY **quando** la selezione viene cambiata **allora** il grafico si aggiorna senza ricaricare la pagina, mostrando il nuovo intervallo temporale nei label dell'asse X.

---

### US-18 – Benchmark cross-paese (Global Admin)
**Epica:** E7 | **Priorità:** Should | **Stima:** L | **Dipendenze:** US-14
**Come** Global Admin **voglio** confrontare i KPI tra tutti i paesi su un'unica vista benchmark **per** identificare i paesi best-in-class e quelli che richiedono interventi strutturali di efficientamento.
**Criteri di Accettazione:**
- **Dato** che il Global Admin accede alla sezione Benchmark **quando** la pagina viene caricata **allora** appare una heatmap con i paesi sulle righe e i KPI sulle colonne, colorata da verde (top) a rosso (worst).
- **Dato** che un Country Manager tenta di accedere alla URL della sezione Benchmark **quando** la richiesta viene processata **allora** il sistema risponde 403 Forbidden e reindirizza alla propria dashboard paese.
- **Dato** che il Global Admin clicca su un paese nella heatmap **quando** viene aperto il dettaglio **allora** appare il profilo completo del paese con tutti i KPI, distribuzione cluster e trend degli ultimi 12 mesi.

---

### US-19 – Scatter plot performance/costo
**Epica:** E7 | **Priorità:** Could | **Stima:** M | **Dipendenze:** US-18
**Come** Global Admin **voglio** visualizzare uno scatter plot che mette in relazione Target Achievement% e Compensation Efficiency per ogni paese **per** identificare i quadranti di alta performance/basso costo e bassa performance/alto costo.
**Criteri di Accettazione:**
- **Dato** che ci sono dati per almeno 3 paesi **quando** lo scatter plot viene renderizzato **allora** ogni punto rappresenta un paese, con Target Achievement% sull'asse X e Compensation Efficiency sull'asse Y, con tooltip che mostra nome paese e valori.
- **Dato** che l'utente passa il mouse su un punto **quando** il tooltip appare **allora** vengono mostrati: nome paese, Target Achievement%, Compensation Efficiency, cluster label e numero di dipendenti.
- **Dato** che viene selezionato un quadrante **quando** i paesi nel quadrante vengono filtrati **allora** la lista sottostante mostra solo i paesi nel quadrante selezionato con drill-down disponibile.

---

### US-20 – Export dati XLSX/CSV
**Epica:** E8 | **Priorità:** Must | **Stima:** S | **Dipendenze:** US-14
**Come** Global Admin o Country Manager **voglio** esportare i dati visualizzati nella dashboard in formato XLSX o CSV **per** analizzarli in strumenti di analisi esterni o condividerli con il management.
**Criteri di Accettazione:**
- **Dato** che l'utente clicca "Esporta" con filtri attivi **quando** il file viene generato **allora** il file contiene solo i dati conformi ai filtri applicati, con le stesse colonne visibili nella vista corrente.
- **Dato** che il set di dati da esportare supera 10.000 righe **quando** viene avviata l'esportazione **allora** il sistema genera il file in background, mostra una notifica UI "Export in preparazione" e fornisce un link per il download al termine.
- **Dato** che il Country Manager esegue un export **quando** il file viene generato **allora** contiene esclusivamente dati del proprio paese; nessun dato di altri paesi è incluso anche se i filtri UI sono stati manipolati.

---

### US-21 – Gestione utenti (System Admin)
**Epica:** E8 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-01
**Come** System Admin **voglio** creare, modificare e disattivare account utente e assegnare i ruoli **per** gestire il ciclo di vita delle identità applicative senza dipendere dall'IdP esterno per la configurazione interna.
**Criteri di Accettazione:**
- **Dato** che il System Admin crea un utente con email, nome e ruolo **quando** il form viene salvato **allora** l'utente viene creato in stato ACTIVE, viene tracciato in rise_audit e l'utente riceve un link di attivazione all'email fornita.
- **Dato** che un utente viene disattivato **quando** l'operazione viene confermata **allora** tutte le sessioni attive dell'utente vengono revocate, lo stato cambia a INACTIVE e l'utente non può più effettuare il login.
- **Dato** che il ruolo di un utente viene modificato da Country Manager a Global Admin **quando** il cambio viene salvato **allora** il nuovo ruolo è effettivo al prossimo login e le sessioni attive vengono invalidate per forzare il re-login.

---

### US-22 – Visualizzazione audit trail
**Epica:** E8 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-01
**Come** Global Admin o System Admin **voglio** consultare l'audit trail immutabile delle operazioni eseguite sulla piattaforma **per** garantire la tracciabilità, supportare gli audit di compliance e investigare anomalie.
**Criteri di Accettazione:**
- **Dato** che il Global Admin accede alla sezione Audit Trail **quando** i log vengono caricati **allora** sono visibili: timestamp, utente, tipo operazione, entità interessata, paese e snapshot JSON pre/post operazione.
- **Dato** che si tenta di eliminare o modificare un record in rise_audit tramite API **quando** la richiesta viene processata **allora** il sistema risponde 405 Method Not Allowed; lo schema rise_audit è configurato con trigger BEFORE DELETE/UPDATE che lanciano eccezione.
- **Dato** che l'utente filtra per utente specifico e intervallo date **quando** i risultati vengono mostrati **allora** la lista è paginata (50 record per pagina) e ordinata per timestamp decrescente.

---

### US-23 – Configurazione soglie cluster
**Epica:** E8 | **Priorità:** Should | **Stima:** S | **Dipendenze:** US-16
**Come** System Admin **voglio** configurare le soglie percentile dei cluster (Top/Med-High/Medium/Low/Worst) **per** adattare la classificazione alla distribuzione reale delle performance aziendali.
**Criteri di Accettazione:**
- **Dato** che il System Admin modifica la soglia Top da P90 a P85 **quando** salva la configurazione **allora** la nuova soglia è persistita con timestamp e utente, e la dashboard mostra un banner "Soglie aggiornate – Dati ricalcolati".
- **Dato** che le soglie inserite creano un gap o una sovrapposizione (es. Top P70, Med-High P80) **quando** si tenta il salvataggio **allora** il validatore mostra "Le soglie devono essere ordinate in modo crescente senza sovrapposizioni".
- **Dato** che le soglie vengono modificate **quando** vengono visualizzati i report storici **allora** è possibile selezionare "Usa soglie storiche" o "Usa soglie correnti" per ciascun report.

---

### US-24 – Import di tipo BOTH (COMPENSATION + SALES)
**Epica:** E5 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-11, US-12
**Come** Global Admin o Country Manager **voglio** eseguire un import di tipo BOTH che carichi contemporaneamente dati di compensazione e vendite **per** ridurre il numero di operazioni di import e garantire la coerenza temporale dei due dataset.
**Criteri di Accettazione:**
- **Dato** che il template è di tipo BOTH e il file contiene colonne di compensazione e vendita **quando** l'import viene eseguito **allora** i dati vengono persistiti sia nelle tabelle di compensazione che in quelle di vendita, con la stessa chiave di deduplicazione.
- **Dato** che i dati di compensazione sono validi ma quelli di vendita contengono errori in alcune righe **quando** l'import è completato **allora** le righe valide per entrambi i tipi vengono importate; le righe con errore vengono riportate nel report con indicazione del tipo (COMPENSATION/SALES) e del campo.
- **Dato** che l'import BOTH è completato **quando** si visualizza il riepilogo **allora** sono mostrate separatamente le statistiche per COMPENSATION e SALES (righe ok, errori, SKIP, OVERWRITE).

---

### US-25 – Validazione semantica chiave di deduplicazione
**Epica:** E5 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-03, US-04, US-11
**Come** sistema RISE **voglio** validare la chiave di deduplicazione `employee_id + shop_id + record_month + record_year` durante l'import **per** prevenire inconsistenze nei dati e garantire una sola riga per combinazione chiave per import.
**Criteri di Accettazione:**
- **Dato** che due righe nel file sorgente hanno la stessa chiave di deduplicazione **quando** il file viene processato **allora** la seconda occorrenza viene segnalata nel report errori con codice `INTRA_FILE_DUPLICATE` e solo la prima viene processata.
- **Dato** che il `shop_id` nel file non esiste nell'anagrafica negozi **quando** la validazione semantica viene eseguita **allora** la riga viene marcata come errore con codice `SHOP_NOT_FOUND` e non viene importata.
- **Dato** che il `employee_id` nel file non corrisponde ad alcun `oracle_hcm_id` in anagrafica **quando** la validazione viene eseguita **allora** la riga viene marcata con codice `EMPLOYEE_NOT_FOUND` e non viene importata.

---

### US-26 – Accessibilità WCAG 2.1 AA
**Epica:** E1 | **Priorità:** Must | **Stima:** L | **Dipendenze:** US-01
**Come** utente con disabilità visiva o motoria **voglio** utilizzare la piattaforma RISE con tecnologie assistive (screen reader, navigazione da tastiera) **per** accedere alle stesse funzionalità degli altri utenti senza barriere.
**Criteri di Accettazione:**
- **Dato** che l'utente naviga con tastiera (Tab/Shift+Tab/Enter/Space) **quando** interagisce con form, modal e tabelle **allora** tutti gli elementi interattivi sono raggiungibili con indicatore focus visibile (outline ≥ 3px contrasto ≥ 3:1).
- **Dato** che l'utente utilizza uno screen reader (NVDA/VoiceOver) **quando** accede alla dashboard KPI **allora** tutti i valori numerici, i grafici e le tabelle hanno alternative testuali (`aria-label`, `aria-describedby`, ruoli ARIA semantici).
- **Dato** che un errore di form viene mostrato **quando** lo screen reader legge il campo **allora** il messaggio di errore è associato tramite `aria-describedby` al campo corrispondente e letto automaticamente al focus.

---

### US-27 – Clonazione template tra paesi (Global Admin)
**Epica:** E3 | **Priorità:** Could | **Stima:** M | **Dipendenze:** US-05
**Come** Global Admin **voglio** clonare un template da un paese sorgente a un paese destinazione **per** accelerare la configurazione di nuovi paesi con strutture dati simili senza ripartire da zero.
**Criteri di Accettazione:**
- **Dato** che il Global Admin seleziona un template PUBLISHED e sceglie il paese destinazione **quando** avvia la clonazione **allora** viene creata una copia del template con stato DRAFT, country_id del paese destinazione e versione 1.0.
- **Dato** che il template clonato contiene riferimenti a entità specifiche del paese sorgente (es. shop_code) **quando** il clone viene creato **allora** una lista di warning mostra i riferimenti da aggiornare prima della pubblicazione.
- **Dato** che il template clonato viene pubblicato **quando** l'operazione è confermata **allora** è disponibile per import nel paese destinazione e l'operazione è tracciata in rise_audit con source_template_id.

---

### US-28 – Calcolo e visualizzazione Cost-to-Revenue%
**Epica:** E6 | **Priorità:** Must | **Stima:** M | **Dipendenze:** US-11, US-14
**Come** Global Admin o Country Manager **voglio** visualizzare il KPI Cost-to-Revenue% che misura il rapporto tra costi totali di compensazione variabile e il fatturato (Total Sales) **per** monitorare la sostenibilità finanziaria del modello incentivante.
**Criteri di Accettazione:**
- **Dato** che sono presenti sia dati COMPENSATION che SALES per il periodo selezionato **quando** il KPI viene calcolato **allora** Cost-to-Revenue% = (FTE + Commission + Quarterly Bonus + Annual Bonus + Extra Booster + Other Compensation) / Total Sales × 100, arrotondato a 2 decimali.
- **Dato** che Total Sales è 0 o non disponibile per il periodo **quando** il KPI viene calcolato **allora** il valore è visualizzato come "N/D" con tooltip "Dati vendite non disponibili per il calcolo".
- **Dato** che il Cost-to-Revenue% supera una soglia configurabile (default 15%) **quando** viene visualizzato nella dashboard **allora** il valore appare in rosso con icona di attenzione.

---

### US-29 – Storico import per paese
**Epica:** E5 | **Priorità:** Should | **Stima:** M | **Dipendenze:** US-11
**Come** Global Admin o Country Manager **voglio** consultare lo storico di tutti gli import eseguiti per il mio paese **per** verificare quali dati sono stati caricati, da chi, quando e con quale esito.
**Criteri di Accettazione:**
- **Dato** che l'utente accede alla sezione Storico Import **quando** la lista viene caricata **allora** sono visualizzati: data/ora esecuzione, utente, nome file, template usato, tipo import, righe totali/ok/errori/skip/overwrite, stato (SUCCESS/PARTIAL/FAILED).
- **Dato** che l'utente clicca su un import storico **quando** il dettaglio viene aperto **allora** appare il report errori originale con possibilità di ridescaricare il JSON.
- **Dato** che il Country Manager accede allo storico **quando** la lista viene filtrata **allora** sono visibili solo gli import eseguiti per il proprio paese; gli import di altri paesi non appaiono.

---

### US-30 – Notifica inline risultato import
**Epica:** E5 | **Priorità:** Should | **Stima:** S | **Dipendenze:** US-11
**Come** utente che ha avviato un import **voglio** ricevere una notifica inline nella UI al completamento del processo **per** sapere immediatamente l'esito senza dover navigare allo storico import.
**Criteri di Accettazione:**
- **Dato** che un import è completato con successo **quando** il risultato è disponibile **allora** appare un toast/banner verde "Import completato: 1.250 righe importate con successo" che rimane visibile per 10 secondi.
- **Dato** che un import è completato con errori parziali **quando** il risultato è disponibile **allora** appare un toast arancione "Import completato con avvisi: 1.200 righe ok, 50 errori – Visualizza Report" con link alla sezione errori.
- **Dato** che un import fallisce completamente **quando** il risultato è disponibile **allora** appare un banner rosso persistente "Import fallito: 0 righe importate – Scarica Report Errori" con link al download diretto del JSON.

---

## §7 NFR

| ID | Categoria | Requisito | Metrica Misurabile |
|----|-----------|-----------|-------------------|
| NFR-01 | Performance | Le API di lettura KPI devono rispondere entro il tempo limite anche sotto carico. | P95 ≤ 3s con 200 utenti concorrenti, misurato con JMeter. |
| NFR-02 | Performance | Il caricamento iniziale del frontend (First Contentful Paint) deve essere rapido. | FCP ≤ 2s su connessione 10 Mbps, bundle JS/CSS ≤ 500 KB gzipped. |
| NFR-03 | Scalabilità | Il backend deve gestire picchi di carico senza degradazione. | Throughput ≥ 500 request/sec senza errori 5xx, scalabilità orizzontale con ≥ 3 pod. |
| NFR-04 | Scalabilità | Il processo di import deve gestire file di grandi dimensioni. | Processamento di 50.000 righe in ≤ 120 secondi su infrastruttura standard. |
| NFR-05 | Sicurezza | Tutte le comunicazioni devono essere cifrate. | 100% traffico su TLS 1.3; certificati rinnovati automaticamente; HSTS abilitato. |
| NFR-06 | Sicurezza | Il JWT deve avere durata limitata e scope minimale. | Scadenza access_token ≤ 1h; refresh_token ≤ 24h; scope limitati al ruolo. |
| NFR-07 | Disponibilità | Il sistema deve garantire alta disponibilità. | SLA ≥ 99.5% mensile; RTO ≤ 4h; RPO ≤ 1h; finestra manutenzione ≤ 4h/mese. |
| NFR-08 | Accessibilità | L'interfaccia deve rispettare gli standard di accessibilità. | Conformità WCAG 2.1 AA verificata con axe-core; 0 violazioni critiche o serie in produzione. |
| NFR-09 | Manutenibilità | La codebase deve essere coperta da test automatici. | Coverage unit test ≥ 80% backend; ≥ 75% frontend; pipeline CI bloccata sotto soglia. |
| NFR-10 | Audit & Compliance | Ogni operazione di scrittura deve essere tracciata in modo immutabile. | 100% delle INSERT/UPDATE/DELETE su rise_core tracciate in rise_audit; latenza scrittura audit ≤ 100ms. |
| NFR-11 | Usabilità | L'Import Wizard deve essere completabile senza formazione specifica. | SUS Score ≥ 75 su campione 10 utenti; tasso di completamento Wizard ≥ 85% al primo tentativo. |
| NFR-12 | Osservabilità | Il sistema deve esporre metriche e log strutturati per il monitoraggio operativo. | Esposizione metriche Prometheus; log JSON strutturati su ELK/Loki; alert su P95 latency e error rate ≥ 1%. |

---

## §8 Piano di Release

### Sprint 0 – Setup e Fondamenta
**Obiettivo:** Predisporre l'infrastruttura di sviluppo, CI/CD, ambienti e schemi DB.
**Attività principali:**
- Provisioning ambienti (DEV, TEST, PROD)
- Setup repository Git, pipeline CI/CD (GitHub Actions / GitLab CI)
- Creazione schemi PostgreSQL `rise_core` e `rise_audit` con RLS e trigger audit
- Bootstrap progetto Angular 17 e Spring Boot 3.x con struttura moduli
- Configurazione OIDC/PKCE con IdP sandbox
- Definizione contratti API OpenAPI 3.0

**Criteri di uscita:** Ambiente DEV operativo, pipeline verde, schemi DB versionati con Liquibase, mock login funzionante.

---

### Release 1 – Core Platform

#### Sprint S01 – Autenticazione e Master Data Base
**Obiettivo:** Login/Logout funzionante con ruoli, CRUD Paesi e Dipendenti.
**US assegnate:** US-01, US-02, US-03
**Criteri di uscita:** Login/logout end-to-end funzionante con 3 ruoli; CRUD dipendenti con validazione `oracle_hcm_id`; audit trail verificato.

#### Sprint S02 – Master Data Negozi e Segregazione
**Obiettivo:** CRUD Negozi, applicazione RLS PostgreSQL, test segregazione dati.
**US assegnate:** US-04, US-26 (infrastruttura a11y)
**Criteri di uscita:** Negozi gestiti con scoping Country Manager; 0 violazioni axe-core critiche sulle pagine realizzate.

#### Sprint S03 – Template Import (Creazione e Versionamento)
**Obiettivo:** Creazione template, versionamento e ciclo di vita.
**US assegnate:** US-05, US-08
**Criteri di uscita:** Template CRUD con stati DRAFT/PUBLISHED/DEPRECATED; versioning verificato; US-05 test autonomi verdi.

#### Sprint S04 – Template Mapping e Regole
**Obiettivo:** Interfaccia drag-and-drop column_mappings e transformation_rules.
**US assegnate:** US-06, US-07, US-27
**Criteri di uscita:** Mapping colonne salvato correttamente come JSONB; regole condizionali validate syntax-level; clonazione template funzionante.

#### Sprint S05 – Import Wizard Fasi 1-2
**Obiettivo:** Upload file e anteprima mapping.
**US assegnate:** US-09, US-10
**Criteri di uscita:** Upload XLSX/CSV/TSV con validazione dimensione/formato; anteprima mapping con highlight colonne mancanti; test file edge-case.

#### Sprint S06 – Import Wizard Fase 3 e Deduplicazione
**Obiettivo:** Esecuzione import, validazione, SKIP/OVERWRITE, report errori.
**US assegnate:** US-11, US-12, US-13, US-24, US-25
**Criteri di uscita:** Import end-to-end completato; report JSON scaricabile; SKIP/OVERWRITE verificato con test; chiave dedup applicata; import BOTH funzionante.

---

### Release 2 – KPI & Analytics

#### Sprint S07 – Dashboard KPI Base
**Obiettivo:** Dashboard con i 4 KPI principali e filtri temporali.
**US assegnate:** US-14, US-15, US-28
**Criteri di uscita:** KPI calcolati server-side entro NFR-01; filtri anno/mese/paese funzionanti; Cost-to-Revenue% con soglia alert.

#### Sprint S08 – Classificazione Cluster
**Obiettivo:** Calcolo e visualizzazione cluster con drill-down.
**US assegnate:** US-16, US-23
**Criteri di uscita:** Cluster calcolati correttamente con percentili configurabili; configurazione soglie da System Admin; drill-down lista dipendenti.

#### Sprint S09 – Trend e Storico Import
**Obiettivo:** Trend YoY/MoM e storico import.
**US assegnate:** US-17, US-29, US-30
**Criteri di uscita:** Grafici trend con variazione % e frecce; storico import paginato; notifiche inline completamento.

#### Sprint S10 – Benchmark Cross-Paese
**Obiettivo:** Vista benchmark Global Admin con heatmap.
**US assegnate:** US-18
**Criteri di uscita:** Heatmap cross-paese visualizzata solo da Global Admin; 403 verificato per County Manager; drill-down paese.

#### Sprint S11 – Scatter Plot e Export
**Obiettivo:** Scatter plot performance/costo e export dati.
**US assegnate:** US-19, US-20
**Criteri di uscita:** Scatter plot renderizzato con Recharts/D3; export XLSX/CSV con dati filtrati; test di segregazione dati nell'export.

---

### Release 3 – Admin, Compliance & Polish

#### Sprint S12 – Gestione Utenti e Audit Trail
**Obiettivo:** CRUD utenti, assegnazione ruoli, visualizzazione audit trail.
**US assegnate:** US-21, US-22
**Criteri di uscita:** Gestione utenti completa con invalidazione sessioni; audit trail read-only verificato con test trigger DB; paginazione e filtri.

#### Sprint S13 – NFR Performance e Sicurezza
**Obiettivo:** Ottimizzazioni performance, test di carico, hardening sicurezza.
**US assegnate:** NFR-01..NFR-06 (verifica e remediation)
**Criteri di uscita:** P95 ≤ 3s verificato con JMeter; TLS 1.3 abilitato; JWT scopes validati; bundle frontend ≤ 500 KB.

#### Sprint S14 – Accessibilità Completa
**Obiettivo:** Verifica e remediation WCAG 2.1 AA su tutte le schermate.
**US assegnate:** US-26 (completamento), NFR-08
**Criteri di uscita:** 0 violazioni axe-core serie/critiche; test con screen reader documentati; score SUS ≥ 75.

#### Sprint S15 – Stabilizzazione e Go-Live
**Obiettivo:** Bug fixing, documentazione, test UAT, deployment produzione.
**Attività:** UAT con utenti reali, documentazione utente, runbook operativo, monitoraggio Prometheus/ELK attivato, go/no-go checklist.
**Criteri di uscita:** 0 bug critici aperti; SLA NFR-07 verificato in staging; approvazione Go-Live da Product Owner.

---

## §9 Matrice Dipendenze

| User Story | Dipende da (US) | Componente Tecnico |
|---|---|---|
| US-01 | – | Spring Security OIDC, Angular Auth Guard, IdP esterno |
| US-02 | US-01 | Token Revocation Endpoint, Session Manager, IdP Logout |
| US-03 | US-01 | EmployeeController, EmployeeRepository, rise_core.employees, RLS Policy |
| US-04 | US-01 | ShopController, ShopRepository, rise_core.shops, RLS Policy |
| US-05 | US-01, US-04 | TemplateController, rise_core.templates, JSONB column_mappings |
| US-06 | US-05 | Angular Drag-Drop (CDK), TemplateMappingService, JSONB updater |
| US-07 | US-06 | RuleEngineService, transformation_rules JSONB validator |
| US-08 | US-05, US-06 | TemplateLifecycleService, rise_audit trigger, VersioningLogic |
| US-09 | US-05, US-08 | FileUploadController, MultipartFileValidator, S3/LocalStorage |
| US-10 | US-09 | MappingPreviewService, ColumnInferenceEngine, Angular Preview Component |
| US-11 | US-10 | ImportExecutionService, ValidationPipeline, BatchProcessor, rise_core.import_jobs |
| US-12 | US-11 | DeduplicationService, SKIP/OVERWRITE Strategy Pattern, rise_audit |
| US-13 | US-11 | ErrorReportService, JSON Serializer, Angular Download Directive |
| US-14 | US-11 | KpiCalculationService, PostgreSQL Aggregation Views, Angular Dashboard Module |
| US-15 | US-14 | FilterService, Angular QueryParams, API Filter DTOs |
| US-16 | US-14 | ClusterCalculationService, Percentile Algorithm, ClusterConfigRepository |
| US-17 | US-14 | TrendCalculationService, TimeSeriesQuery, Angular Chart (Recharts/NgRx) |
| US-18 | US-14 | BenchmarkService, Cross-Country Aggregation, Angular Heatmap Component |
| US-19 | US-18 | ScatterPlotService, Angular D3 Integration, BenchmarkDataDTO |
| US-20 | US-14 | ExportService, Apache POI (XLSX), CSVWriter, Angular Export Trigger |
| US-21 | US-01 | UserManagementController, UserRepository, RoleAssignmentService, Session Invalidation |
| US-22 | US-01 | AuditTrailController, rise_audit schema (read-only), Pagination & Filter |
| US-23 | US-16 | ClusterConfigController, ClusterConfigRepository, Dashboard Recalc Trigger |
| US-24 | US-11, US-12 | ImportTypeBothProcessor, Dual-Target Persistence, SplitValidationPipeline |
| US-25 | US-03, US-04, US-11 | DeduplicationKeyValidator, EmployeeLookupService, ShopLookupService |
| US-26 | US-01 | Angular CDK A11y, ARIA Directives, axe-core CI integration |
| US-27 | US-05 | TemplateCloneService, CountryResolutionService, Warning Generator |
| US-28 | US-11, US-14 | CostToRevenueCalculator, KpiAlertThresholdService, Dashboard Alert Component |
| US-29 | US-11 | ImportHistoryController, ImportJobRepository, RLS-scoped query |
| US-30 | US-11 | ImportNotificationService, Angular Toast/Banner Component, WebSocket or Polling |

---

## §10 Rischi e Domande Aperte

### Rischi

| ID | Rischio | Probabilità | Impatto | Mitigazione |
|----|---------|:-----------:|:-------:|-------------|
| R-01 | Indisponibilità o cambio dell'Identity Provider esterno blocca l'accesso all'intera piattaforma. | Media | Critico | Definire SLA con il provider IdP; implementare modalità di emergenza con account break-glass; test di failover mensili. |
| R-02 | Disallineamento tra `oracle_hcm_id` in RISE e Oracle HCM produce duplicati silenti non rilevabili dalla chiave di dedup. | Alta | Alto | Sincronizzazione periodica manuale dell'anagrafica; report di discrepanza HCM↔RISE; validazione `oracle_hcm_id` obbligatoria in import. |
| R-03 | File sorgente con encoding non standard (es. Windows-1252, UTF-16) causa parsing errato senza errori espliciti. | Media | Medio | Rilevamento automatico encoding con `Chardet`/`ICU4J`; normalizzazione a UTF-8 in pipeline; warning UI in caso di encoding non UTF-8. |
| R-04 | Le query di aggregazione KPI cross-paese degradano a causa della crescita del volume dati nel tempo. | Media | Alto | Materialized Views PostgreSQL schedulate; indici parziali su (country_id, record_year, record_month); query explain-plan review a ogni sprint. |
| R-05 | Modifica del template PUBLISHED mentre un import è in esecuzione causa inconsistenza nei dati. | Bassa | Alto | Lock ottimistico sul template durante import; stato LOCKED durante esecuzione; retry con template corretto dopo unlock. |
| R-06 | I transformation_rules JSONB contengono espressioni con injection o side-effect non previsti. | Bassa | Critico | Sandbox di esecuzione regole con whitelist operatori; validazione sintattica e semantica server-side; test fuzz delle regole in CI. |
| R-07 | I dati di compensazione di paesi diversi sono accessibili lateralmente a causa di bug RLS. | Bassa | Critico | Test automatici di segregazione dati per ogni sprint; penetration test prima del go-live; audit RLS policy in code review. |
| R-08 | L'export di grandi dataset (>100k righe) causa OOM sul backend. | Media | Medio | Streaming export con Apache POI SXSSF; background job per export grandi; limite dimensione export configurabile. |
| R-09 | Il processo di import a 50k righe supera il timeout HTTP (default 30s) del gateway/load balancer. | Media | Alto | Import asincrono con job ID e polling stato; timeout gateway configurato a ≥180s; progress WebSocket. |
| R-10 | Adozione insufficiente da parte degli utenti per complessità dell'Import Wizard a 3 fasi. | Media | Medio | User testing con utenti reali durante Sprint S14 UAT; SUS Score target ≥75; materiale formativo in-app (tooltip contestuali, guided tour). |

### Domande Aperte

| ID | Domanda | Owner |
|----|---------|-------|
| OQ-01 | Qual è il meccanismo di sincronizzazione previsto tra Oracle HCM e l'anagrafica dipendenti di RISE? È prevista un'integrazione automatica in una release futura o resterà sempre manuale? | Product Owner / Enterprise Architect |
| OQ-02 | Le soglie di allerta per i KPI (es. Cost-to-Revenue% > 15%) sono configurabili per paese o globali? Chi ha l'autorità di modificarle in produzione? | Product Owner / Business Sponsor |
| OQ-03 | Il modello di licenza è per utente nominale o concorrente? Questo impatta il dimensionamento del pool di sessioni JWT e la politica di revoca. | Product Owner / Legal |
| OQ-04 | Esiste un requisito di data residency per paese (es. dati di Francia e Germania non possono uscire dall'UE)? Questo impatta l'architettura da multi-tenant logico a fisico. | CTO / Data Protection Officer |
| OQ-05 | Il calcolo di HA Sales (componente del campo SALES) segue una logica di business specifica per paese o è una colonna diretta del file sorgente? Se derivata, qual è la formula? | Business Analyst / Country Manager referente |

---

## BA_AGENT_SIGNATURE_V2

```
╔══════════════════════════════════════════════════════════════════╗
║            B U S I N E S S   A N A L Y S T   A G E N T          ║
║                     S I G N A T U R E   V 2                      ║
╠══════════════════════════════════════════════════════════════════╣
║  Document  : RISE Spending Effectiveness – BA & User Stories     ║
║  Version   : 2.0                                                 ║
║  Generated : 2026-03-04                                          ║
║  Agent     : BA_AGENT_V2 / GitHub Copilot (Claude Sonnet 4.6)   ║
║  Standards : WCAG 2.1 AA | OIDC/PKCE | PostgreSQL 15+           ║
║              Angular 17 | Spring Boot 3.x                        ║
║  Coverage  : §1 Glossario (20 termini)                          ║
║              §2 Assunzioni (A-01 → A-17)                        ║
║              §3 Scope (21 IN / 9 OUT)                           ║
║              §4 Matrice Ruoli (24 funzionalità × 3 ruoli)       ║
║              §5 Epiche (E1 → E8)                                ║
║              §6 User Stories (US-01 → US-30)                    ║
║              §7 NFR (NFR-01 → NFR-12)                           ║
║              §8 Piano Release (S0 + R1:S01-S06 + R2:S07-S11     ║
║                                             + R3:S12-S15)        ║
║              §9 Matrice Dipendenze (30 righe)                   ║
║              §10 Rischi (R-01→R-10) + OQ (OQ-01→OQ-05)         ║
║                                                                  ║
║  This section must be present and must not be removed.           ║
╚══════════════════════════════════════════════════════════════════╝
```