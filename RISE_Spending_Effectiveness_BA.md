# RISE Spending Effectiveness – Business Analysis & User Stories
**Versione:** 2.0 | **Data:** 2026-03-04 | **Autore:** Business Analyst Agent

---

## §1 Glossario

| Termine | Definizione |
|---|---|
| **RISE** | Piattaforma enterprise B2B per la gestione centralizzata della retribuzione variabile e delle performance di vendita. |
| **Business Unit (BU)** | Unità organizzativa aziendale raggruppata per area geografica o funzione; definisce il perimetro di visibilità dei Country Manager. |
| **Employee ID** | Identificativo univoco del dipendente all'interno del sistema; chiave primaria per il tracking delle compensazioni. |
| **Shop Code** | Codice univoco assegnato a ciascun punto vendita (negozio), utilizzato come chiave di aggregazione dei dati di vendita e compensazione. |
| **FTE (Full-Time Equivalent)** | Unità di misura della forza lavoro che esprime l'equivalente a tempo pieno di un dipendente nel mese di riferimento. |
| **Commission** | Compensazione variabile erogata al dipendente in funzione delle vendite o degli obiettivi raggiunti nel periodo. |
| **Quarterly Bonus** | Bonus trimestrale erogato al raggiungimento di target di performance definiti su base trimestrale. |
| **Annual Bonus** | Bonus annuale legato al raggiungimento di obiettivi definiti su scala annua (es. MBO). |
| **Extra Booster** | Compensazione aggiuntiva straordinaria, tipicamente legata a campagne promozionali o obiettivi specifici di breve periodo. |
| **Import Wizard** | Procedura guidata in tre fasi per l'importazione di dati da file esterni (Excel/CSV) verso il database centralizzato. |
| **Template di Importazione** | Configurazione riutilizzabile e versionabile che definisce il mapping tra le colonne del file sorgente e i campi del database. |
| **Mapping** | Associazione tra l'intestazione di una colonna nel file sorgente e il campo target del modello dati RISE. |
| **Logica Condizionale** | Regola di trasformazione applicata durante l'import (es. moltiplicazione ×100) per normalizzare i valori prima del caricamento. |
| **Chiave di Deduplicazione** | Combinazione di Employee ID + Shop Code + Mese + Anno utilizzata per rilevare record duplicati durante l'import. |
| **SKIP** | Strategia di gestione duplicati che ignora il record in ingresso se esiste già una corrispondenza sulla chiave di deduplicazione. |
| **OVERWRITE** | Strategia di gestione duplicati che sovrascrive il record esistente con il nuovo valore proveniente dal file sorgente. |
| **Audit Log** | Registro immutabile di ogni operazione di import, contenente utente, timestamp, nome file e riepilogo dei risultati. |
| **NFR** | Non-Functional Requirement; requisito che definisce qualità di sistema quali performance, sicurezza, scalabilità e disponibilità. |
| **Global Admin** | Ruolo con controllo totale su tutte le funzionalità e tutte le Business Unit della piattaforma. |
| **Country Manager** | Ruolo operativo con visibilità limitata alle sole Business Unit associate; gestisce upload di compensazioni e vendite. |
| **System Admin** | Ruolo amministrativo di sistema; definisce e mantiene parametri chiave quali BU, template e configurazioni. |

---

## §2 Assunzioni

| ID | Assunzione |
|---|---|
| A-01 | Il sistema gestisce dati esclusivamente su base mensile; non sono previste granularità infrasettimanali o giornaliere. |
| A-02 | Ogni dipendente è identificato da un Employee ID univoco e stabile nel tempo; cambi di ID non sono supportati senza migrazione manuale. |
| A-03 | Un dipendente può essere associato a più negozi in mesi diversi; la combinazione Employee ID + Shop Code + Mese + Anno è univoca. |
| A-04 | I file sorgente da importare sono esclusivamente in formato Excel (.xlsx, .xls) o CSV (.csv). |
| A-05 | Il mapping automatico si basa sul riconoscimento testuale delle intestazioni di colonna; in caso di ambiguità prevale il mapping manuale. |
| A-06 | I template di importazione sono specifici per paese e versionati; una modifica al template crea una nuova versione senza cancellare la precedente. |
| A-07 | La validazione dei duplicati avviene prima dell'inserimento nel database; l'utente deve scegliere la strategia SKIP o OVERWRITE prima dell'esecuzione. |
| A-08 | I campi obbligatori minimi per ogni record sono: Employee ID, Shop Code, Mese, Anno. |
| A-09 | I valori numerici nei file sorgente possono usare sia il punto che la virgola come separatore decimale; il sistema normalizza automaticamente. |
| A-10 | Le date nel file sorgente devono essere espresse nel formato ISO 8601 (YYYY-MM-DD) o come coppia Mese/Anno; formati non standard generano errore. |
| A-11 | Il sistema supporta almeno tre valute (EUR, USD, GBP); la gestione della conversione valutaria è out of scope per la versione 2.0. |
| A-12 | La disattivazione di un utente non comporta la cancellazione dei dati da lui importati; l'audit trail rimane integro. |
| A-13 | Il database PostgreSQL è l'unico sistema di persistenza; non sono previste integrazioni con database esterni nella versione corrente. |
| A-14 | La dashboard espone filtri per anno, paese, regione, negozio e dipendente; la combinazione dei filtri è cumulativa (AND logico). |
| A-15 | L'export dei dati produce file CSV o Excel con granularità dipendente/negozio/mese; non sono previsti export aggregati nella prima release. |
| A-16 | Il frontend Angular comunica con il backend Spring Boot esclusivamente tramite API REST protette da JWT. |
| A-17 | La piattaforma è multi-tenant a livello logico; l'isolamento dei dati per Business Unit è garantito dal modello di autorizzazione, non da schemi DB separati. |

---

## §3 Scope

### In Scope

1. Autenticazione e gestione degli utenti con tre ruoli distinti (Global Admin, Country Manager, System Admin).
2. Creazione, modifica, disattivazione e riattivazione degli account utente.
3. Definizione e manutenzione delle Business Unit (paesi, regioni, aree business).
4. Gestione anagrafica dipendenti: inserimento, aggiornamento, cancellazione logica.
5. Gestione anagrafica negozi/shop: creazione, associazione a paese/regione/area business.
6. Import Wizard – Fase 1: definizione template con mapping automatico e manuale delle colonne.
7. Import Wizard – Fase 1: supporto a logiche condizionali di trasformazione (es. ×100, arrotondamento).
8. Import Wizard – Fase 1: salvataggio e versionamento dei template per paese.
9. Import Wizard – Fase 2: upload file locale Excel o CSV e collegamento file compensazione + vendite.
10. Import Wizard – Fase 3: esecuzione dell'import con applicazione del mapping e del template selezionato.
11. Validazione dati in import: campi obbligatori, tipi numerici, coerenza date, deduplicazione su chiave composita.
12. Gestione strategia duplicati: selezione SKIP o OVERWRITE prima dell'esecuzione.
13. Generazione report post-import: righe importate, duplicati, errori, record mancanti.
14. Download del report di import in formato Excel o CSV.
15. Audit log completo di ogni operazione di import (utente, timestamp, file, riepilogo).
16. Dashboard interattiva con filtri per anno, paese, regione, negozio, dipendente.
17. Visualizzazione dati di compensazione mensile per dipendente (tutti i campi previsti).
18. Visualizzazione dati di vendita mensile per dipendente/negozio (tutti i campi previsti).
19. Export completo del database in CSV/Excel con granularità dipendente/negozio/mese.
20. Analisi cross-paese: confronto KPI di compensazione e vendita tra Business Unit diverse.
21. Controllo degli accessi basato su ruolo (RBAC) con visibilità limitata per Country Manager.
22. Supporto file compensazione e vendita separati (caricabili e collegabili nella Fase 2 dell'Import Wizard).

### Out of Scope

1. Conversione automatica delle valute tra paesi diversi.
2. Integrazione diretta con sistemi ERP o HCM di terze parti (es. SAP, Workday).
3. Calcolo automatico della retribuzione variabile o dei bonus (il sistema riceve dati già calcolati).
4. Gestione dei contratti di lavoro o delle buste paga complete.
5. Workflow di approvazione multi-livello per i dati importati.
6. Notifiche push o e-mail automatiche agli utenti finali.
7. Accesso mobile nativo (app iOS/Android); il sistema è responsivo ma non ottimizzato per mobile.
8. Archiviazione storica di versioni precedenti dei file sorgente caricati (stored file versioning).
9. Funzionalità di previsione o forecasting basate su algoritmi di machine learning.

---

## §4 Matrice Ruoli e Permessi

| Funzionalità | Global Admin | Country Manager | System Admin |
|---|:---:|:---:|:---:|
| Visualizzare tutte le Business Unit | ✅ | ❌ (solo BU associate) | ✅ |
| Creare/modificare Business Unit | ✅ | ❌ | ✅ |
| Creare utenti | ✅ | ❌ | ✅ |
| Modificare utenti | ✅ | ❌ | ✅ |
| Disattivare/riattivare utenti | ✅ | ❌ | ✅ |
| Gestire anagrafica dipendenti (CRUD) | ✅ | ✅ (solo BU associate) | ❌ |
| Gestire anagrafica negozi (CRUD) | ✅ | ❌ | ✅ |
| Creare template di importazione | ✅ | ✅ (solo BU associate) | ✅ |
| Modificare template di importazione | ✅ | ✅ (solo BU associate) | ✅ |
| Eliminare template di importazione | ✅ | ❌ | ✅ |
| Eseguire Import Wizard (tutte le fasi) | ✅ | ✅ (solo BU associate) | ❌ |
| Visualizzare report di import | ✅ | ✅ (solo propri import) | ✅ |
| Scaricare report di import | ✅ | ✅ (solo propri import) | ✅ |
| Visualizzare audit log | ✅ | ❌ | ✅ |
| Visualizzare dashboard completa | ✅ | ✅ (solo BU associate) | ✅ (solo vista) |
| Applicare filtri dashboard | ✅ | ✅ (limitati a BU associate) | ✅ |
| Esportare dati in CSV/Excel | ✅ | ✅ (solo BU associate) | ✅ |
| Definire parametri di sistema (config) | ❌ | ❌ | ✅ |
| Visualizzare dati di compensazione | ✅ | ✅ (solo BU associate) | ✅ (solo vista) |
| Visualizzare dati di vendita | ✅ | ✅ (solo BU associate) | ✅ (solo vista) |
| Gestire strategie duplicati (SKIP/OVERWRITE) | ✅ | ✅ | ❌ |
| Accedere alla sezione di amministrazione sistema | ✅ | ❌ | ✅ |

---

## §5 Epiche

| ID | Titolo | Descrizione breve | Stories associate |
|---|---|---|---|
| **E1** | Gestione Utenti e Accessi | Creazione, modifica, disattivazione degli utenti e controllo degli accessi basato su ruolo (RBAC). | US-01, US-02, US-03, US-04 |
| **E2** | Anagrafica Master Data | Manutenzione completa delle entità principali: dipendenti, negozi e Business Unit. | US-05, US-06, US-07, US-08 |
| **E3** | Import Wizard – Definizione Template | Prima fase del wizard: mapping colonne, logiche condizionali, salvataggio e versionamento template. | US-09, US-10, US-11 |
| **E4** | Import Wizard – Registrazione Fonte | Seconda fase del wizard: upload file Excel/CSV, collegamento file compensazione e vendite. | US-12, US-13 |
| **E5** | Import Wizard – Esecuzione e Validazione | Terza fase del wizard: applicazione mapping, validazione, gestione duplicati, generazione report. | US-14, US-15, US-16, US-17, US-18 |
| **E6** | Dashboard e Analisi | Dashboard interattiva con filtri multi-dimensionali e analisi cross-paese dei KPI. | US-19, US-20, US-21, US-22 |
| **E7** | Export e Portabilità Dati | Export completo del database in formato CSV/Excel con granularità configurabile. | US-23, US-24 |
| **E8** | Audit, Sicurezza e NFR | Audit log, gestione della sessione, logging degli errori e requisiti non funzionali trasversali. | US-25, US-26, US-27, US-28, US-29, US-30 |

---

## §6 User Stories

---

**[US-01] Accesso alla piattaforma tramite autenticazione sicura**
> Come utente della piattaforma, voglio poter accedere tramite credenziali (username/password) per garantire che solo gli utenti autorizzati possano operare sul sistema.

**Criteri di Accettazione:**
- **Given** un utente registrato con credenziali valide, **When** inserisce username e password corretti, **Then** riceve un token JWT e viene reindirizzato alla dashboard appropriata al suo ruolo.
- **Given** un utente che inserisce una password errata, **When** supera 5 tentativi consecutivi, **Then** l'account viene bloccato temporaneamente per 15 minuti e viene mostrato un messaggio informativo.
- **Given** un utente con account disattivato, **When** tenta il login, **Then** riceve un messaggio di errore specifico e non ottiene accesso.

**Stima:** S | **Priorità:** Must | **Epica:** E1 | **Dipendenze:** nessuna

---

**[US-02] Creazione di un nuovo utente**
> Come Global Admin o System Admin, voglio creare un nuovo account utente con ruolo specifico per abilitare nuovi operatori alla piattaforma.

**Criteri di Accettazione:**
- **Given** un Global Admin autenticato, **When** compila il form di creazione utente (nome, email, ruolo, BU associate) e conferma, **Then** l'utente viene creato, riceve un'email con link di attivazione e appare nella lista utenti.
- **Given** un tentativo di creazione con email già esistente, **When** si tenta il salvataggio, **Then** viene mostrato un errore di unicità e l'operazione viene bloccata.
- **Given** la creazione di un Country Manager, **When** non viene associata alcuna BU, **Then** il sistema mostra un avviso obbligatorio e impedisce il salvataggio.

**Stima:** M | **Priorità:** Must | **Epica:** E1 | **Dipendenze:** US-01

---

**[US-03] Disattivazione e riattivazione di un utente**
> Come Global Admin o System Admin, voglio poter disattivare o riattivare un account utente per gestire il ciclo di vita degli operatori senza perdere la loro storia operativa.

**Criteri di Accettazione:**
- **Given** un utente attivo, **When** il Global Admin ne esegue la disattivazione, **Then** l'utente non può più effettuare il login ma i suoi dati e audit log rimangono intatti.
- **Given** un utente disattivato, **When** il System Admin ne esegue la riattivazione, **Then** l'utente può tornare ad accedere con le stesse credenziali e permessi precedenti.
- **Given** un Global Admin che tenta di disattivare il proprio account, **When** conferma l'operazione, **Then** il sistema blocca l'azione con un messaggio di errore che impedisce l'auto-disattivazione.

**Stima:** S | **Priorità:** Must | **Epica:** E1 | **Dipendenze:** US-02

---

**[US-04] Modifica dei permessi e delle Business Unit associate a un utente**
> Come System Admin, voglio modificare le Business Unit associate a un Country Manager per aggiornare il suo perimetro di visibilità in seguito a riorganizzazioni aziendali.

**Criteri di Accettazione:**
- **Given** un Country Manager esistente, **When** il System Admin rimuove una BU dalla sua associazione, **Then** il Country Manager non vede più i dati di quella BU alla prossima sessione.
- **Given** un Country Manager esistente, **When** il System Admin aggiunge una nuova BU, **Then** il Country Manager vede immediatamente i dati della nuova BU senza dover effettuare un nuovo login.
- **Given** la modifica delle BU associate, **When** l'operazione viene salvata, **Then** viene registrata una voce nell'audit log con utente modificante, timestamp e dettaglio della variazione.

**Stima:** M | **Priorità:** Must | **Epica:** E1 | **Dipendenze:** US-02, US-03

---

**[US-05] Inserimento di un nuovo dipendente in anagrafica**
> Come Global Admin o Country Manager, voglio inserire un nuovo dipendente nel sistema per poter successivamente importare i suoi dati di compensazione e vendita.

**Criteri di Accettazione:**
- **Given** un Global Admin autenticato, **When** compila il form anagrafico (Employee ID, nome, cognome, BU, negozio iniziale) e salva, **Then** il dipendente appare nella lista con stato attivo.
- **Given** un Employee ID già presente nel sistema, **When** si tenta l'inserimento, **Then** il sistema blocca l'operazione e mostra un messaggio di duplicato.
- **Given** un Country Manager, **When** inserisce un dipendente, **Then** può associarlo solo a BU e negozi di sua competenza; i campi di altre BU sono disabilitati.

**Stima:** M | **Priorità:** Must | **Epica:** E2 | **Dipendenze:** US-01, US-07

---

**[US-06] Aggiornamento e cancellazione logica di un dipendente**
> Come Global Admin, voglio aggiornare i dati anagrafici di un dipendente o disattivarlo logicamente per mantenere l'integrità storica dei dati.

**Criteri di Accettazione:**
- **Given** un dipendente esistente, **When** viene aggiornato un campo anagrafico (es. nome), **Then** la modifica viene salvata e i dati storici di compensazione rimangono inalterati.
- **Given** la cancellazione logica di un dipendente, **When** viene confermata, **Then** il dipendente non appare nelle liste operative ma i suoi dati storici sono ancora accessibili in dashboard e export.
- **Given** un tentativo di cancellazione fisica, **When** il record ha dati di compensazione o vendita associati, **Then** il sistema impedisce la cancellazione e suggerisce la disattivazione logica.

**Stima:** M | **Priorità:** Must | **Epica:** E2 | **Dipendenze:** US-05

---

**[US-07] Gestione anagrafica negozi/shop**
> Come System Admin, voglio creare e mantenere l'anagrafica dei negozi con i relativi attributi geografici per garantire la corretta associazione dei dati di vendita.

**Criteri di Accettazione:**
- **Given** un System Admin autenticato, **When** crea un nuovo negozio con Shop Code univoco, paese, regione e area business, **Then** il negozio è immediatamente disponibile per l'associazione a dipendenti e import.
- **Given** uno Shop Code già esistente, **When** si tenta la creazione, **Then** il sistema blocca con errore di unicità.
- **Given** la modifica di un negozio esistente, **When** viene aggiornato il paese di appartenenza, **Then** tutti i dati storici associati a quel negozio vengono mantenuti ma filtrати correttamente nella dashboard.

**Stima:** M | **Priorità:** Must | **Epica:** E2 | **Dipendenze:** US-01

---

**[US-08] Gestione delle Business Unit**
> Come System Admin, voglio definire e mantenere la gerarchia delle Business Unit (paese → regione → area) per strutturare correttamente il perimetro di accesso e i filtri della dashboard.

**Criteri di Accettazione:**
- **Given** un System Admin, **When** crea una nuova BU con nome, paese e gerarchia, **Then** la BU è disponibile per l'associazione a utenti e negozi.
- **Given** l'eliminazione di una BU, **When** essa ha negozi o utenti associati, **Then** il sistema blocca la cancellazione e mostra le dipendenze.
- **Given** la modifica del nome di una BU, **When** viene salvata, **Then** tutti i riferimenti esistenti vengono aggiornati automaticamente.

**Stima:** M | **Priorità:** Must | **Epica:** E2 | **Dipendenze:** US-01

---

**[US-09] Creazione di un template di importazione con mapping automatico**
> Come Country Manager o Global Admin, voglio creare un template di importazione a partire dalle intestazioni del file sorgente con mapping automatico per ridurre il lavoro manuale di configurazione.

**Criteri di Accettazione:**
- **Given** un file Excel caricato con intestazioni riconoscibili, **When** si avvia la Fase 1 del wizard, **Then** il sistema propone automaticamente il mapping tra intestazioni e campi target con una percentuale di confidenza visibile.
- **Given** un mapping automatico proposto, **When** l'utente lo conferma senza modifiche, **Then** il template viene salvato con la configurazione rilevata automaticamente.
- **Given** intestazioni non riconoscibili, **When** il mapping automatico fallisce su alcune colonne, **Then** quelle colonne restano in stato "da mappare" e il wizard non consente di procedere finché non sono risolte.

**Stima:** L | **Priorità:** Must | **Epica:** E3 | **Dipendenze:** US-01, US-08

---

**[US-10] Mapping manuale e logiche condizionali nel template**
> Come Country Manager, voglio poter sovrascrivere il mapping automatico e definire logiche condizionali (es. ×100) per gestire formati non standard dei file sorgente del mio paese.

**Criteri di Accettazione:**
- **Given** un mapping automatico proposto, **When** l'utente modifica manualmente l'associazione di una colonna, **Then** la modifica sovrascrive il mapping automatico e viene evidenziata visivamente come override manuale.
- **Given** la definizione di una logica condizionale (es. moltiplica ×100), **When** viene applicata a un campo numerico, **Then** nel preview del template viene mostrato il valore trasformato accanto al valore originale.
- **Given** una logica condizionale non valida (es. formula con errore di sintassi), **When** si tenta il salvataggio, **Then** il sistema mostra un errore descrittivo e impedisce il salvataggio del template.

**Stima:** L | **Priorità:** Must | **Epica:** E3 | **Dipendenze:** US-09

---

**[US-11] Salvataggio e versionamento del template di importazione**
> Come Country Manager o System Admin, voglio salvare un template versionato e riutilizzabile per paese per evitare di riconfigurare il mapping a ogni import.

**Criteri di Accettazione:**
- **Given** un template completamente configurato, **When** viene salvato con nome e paese, **Then** appare nella lista dei template disponibili per quel paese con numero di versione 1.0.
- **Given** la modifica di un template esistente, **When** viene salvata, **Then** viene creata una nuova versione (es. 1.1) e la versione precedente rimane disponibile in sola lettura.
- **Given** la selezione di un template nella Fase 3, **When** viene applicato, **Then** il sistema usa sempre l'ultima versione disponibile salvo selezione esplicita di una versione precedente.

**Stima:** M | **Priorità:** Must | **Epica:** E3 | **Dipendenze:** US-09, US-10

---

**[US-12] Upload di file sorgente nella Registrazione Fonte**
> Come Country Manager, voglio caricare un file Excel o CSV dalla mia postazione locale per avviare il processo di import dei dati.

**Criteri di Accettazione:**
- **Given** la Fase 2 del wizard attiva, **When** l'utente seleziona un file .xlsx o .csv di dimensione inferiore a 50 MB, **Then** il file viene caricato, viene mostrata un'anteprima delle prime 10 righe e il sistema rileva automaticamente il delimitatore (CSV).
- **Given** un file di formato non supportato (es. .pdf), **When** viene selezionato, **Then** il sistema mostra un messaggio di errore prima del caricamento.
- **Given** un file superiore a 50 MB, **When** si tenta il caricamento, **Then** il sistema mostra un avviso con suggerimento di dividere il file.

**Stima:** M | **Priorità:** Must | **Epica:** E4 | **Dipendenze:** US-09, US-11

---

**[US-13] Collegamento di file compensazione e vendite separati**
> Come Country Manager, voglio collegare due file distinti – uno per la compensazione e uno per le vendite – nella stessa sessione di import per gestire i casi in cui le fonti dati sono separate.

**Criteri di Accettazione:**
- **Given** la Fase 2 del wizard con due slot di upload disponibili, **When** vengono caricati entrambi i file, **Then** il sistema li collega automaticamente sulla chiave comune (Employee ID + Shop Code + Mese + Anno).
- **Given** il collegamento fallisce per mancanza di chiave comune, **When** viene visualizzato il report di collegamento, **Then** i record non collegati sono evidenziati come "orfani" con possibilità di escluderli o procedere.
- **Given** la presenza di un solo file (solo compensazione o solo vendite), **When** si procede, **Then** i campi del file mancante vengono impostati come null senza bloccare l'import.

**Stima:** L | **Priorità:** Must | **Epica:** E4 | **Dipendenze:** US-12

---

**[US-14] Esecuzione dell'import con applicazione del mapping**
> Come Country Manager, voglio eseguire l'import dei dati applicando il template selezionato per caricare i dati nel database centralizzato.

**Criteri di Accettazione:**
- **Given** file caricati e template selezionato, **When** si avvia la Fase 3 dell'import, **Then** il sistema applica il mapping e le logiche condizionali definite nel template trasformando i dati prima dell'inserimento.
- **Given** l'esecuzione dell'import, **When** il processo è completato, **Then** viene mostrato un riepilogo con: N righe importate, N duplicati gestiti, N errori, N record mancanti.
- **Given** un import in corso, **When** l'utente tenta di navigare fuori dalla pagina, **Then** appare un dialog di conferma che avvisa della perdita del processo in corso.

**Stima:** L | **Priorità:** Must | **Epica:** E5 | **Dipendenze:** US-11, US-12, US-13

---

**[US-15] Validazione campi obbligatori e tipi di dato**
> Come sistema, voglio validare automaticamente i campi obbligatori e i tipi di dato prima dell'inserimento per garantire l'integrità del database.

**Criteri di Accettazione:**
- **Given** un record con Employee ID mancante, **When** viene processato in import, **Then** viene marcato come errore con descrizione "Employee ID obbligatorio mancante" e non viene inserito.
- **Given** un valore non numerico in un campo numerico (es. Commission), **When** viene processato, **Then** il record viene marcato come errore con descrizione del tipo atteso.
- **Given** una data non coerente (es. mese 13), **When** viene processata, **Then** il record viene marcato come errore di formato data.

**Stima:** M | **Priorità:** Must | **Epica:** E5 | **Dipendenze:** US-14

---

**[US-16] Gestione dei duplicati con strategia SKIP o OVERWRITE**
> Come Country Manager, voglio scegliere la strategia di gestione dei duplicati (SKIP o OVERWRITE) prima dell'esecuzione dell'import per avere controllo sui dati già presenti nel sistema.

**Criteri di Accettazione:**
- **Given** il rilevamento di duplicati nella Fase 3, **When** il sistema identifica record con chiave Employee ID + Shop Code + Mese + Anno già esistente, **Then** mostra un contatore dei duplicati e richiede la scelta della strategia prima di procedere.
- **Given** la strategia SKIP selezionata, **When** l'import viene eseguito, **Then** i record duplicati vengono ignorati e il loro conteggio appare nel report finale.
- **Given** la strategia OVERWRITE selezionata, **When** l'import viene eseguito, **Then** i record esistenti vengono aggiornati con i nuovi valori e il conteggio degli aggiornamenti appare nel report finale.

**Stima:** M | **Priorità:** Must | **Epica:** E5 | **Dipendenze:** US-14, US-15

---

**[US-17] Generazione e download del report post-import**
> Come Country Manager, voglio scaricare il report dettagliato dell'import in formato Excel o CSV per documentare e analizzare i risultati del caricamento.

**Criteri di Accettazione:**
- **Given** un import completato (con o senza errori), **When** si clicca su "Scarica Report", **Then** viene generato un file Excel con fogli separati per: importati, duplicati, errori, mancanti.
- **Given** il download del report, **When** viene aperto in Excel, **Then** ogni riga di errore include la colonna originale, il valore problematico e la descrizione dell'errore.
- **Given** un import completamente riuscito senza errori, **When** viene scaricato il report, **Then** il report contiene un foglio riepilogativo con il conteggio totale delle righe importate e il timestamp.

**Stima:** M | **Priorità:** Must | **Epica:** E5 | **Dipendenze:** US-14, US-15, US-16

---

**[US-18] Registrazione nell'audit log di ogni operazione di import**
> Come sistema, voglio registrare ogni operazione di import nell'audit log con tutti i metadati necessari per garantire tracciabilità e conformità.

**Criteri di Accettazione:**
- **Given** il completamento di un import, **When** viene registrata la voce di audit, **Then** contiene: utente esecutore, timestamp preciso, nome file caricato, template utilizzato, riepilogo (importati/duplicati/errori/mancanti).
- **Given** un import fallito con errore di sistema, **When** viene registrata la voce di audit, **Then** include lo stack error e lo stato FAILED.
- **Given** la visualizzazione dell'audit log da parte di un Global Admin, **When** filtra per utente e data, **Then** ottiene tutte le voci corrispondenti ordinate per timestamp decrescente.

**Stima:** M | **Priorità:** Must | **Epica:** E8 | **Dipendenze:** US-14

---

**[US-19] Dashboard con filtri multi-dimensionali**
> Come Global Admin, voglio accedere a una dashboard con filtri combinabili per anno, paese, regione, negozio e dipendente per analizzare i KPI a diversi livelli di granularità.

**Criteri di Accettazione:**
- **Given** la dashboard aperta, **When** vengono applicati filtri multipli (es. anno=2025, paese=Italia, regione=Nord), **Then** tutti i widget della dashboard si aggiornano mostrando solo i dati corrispondenti ai filtri selezionati.
- **Given** un Country Manager sulla dashboard, **When** accede, **Then** i filtri paese e regione sono pre-impostati sulle sue BU associate e non modificabili.
- **Given** la selezione di un dipendente specifico, **When** il filtro viene applicato, **Then** la dashboard mostra i dati mensili di quel dipendente su tutti i negozi per cui ha record nel periodo filtrato.

**Stima:** L | **Priorità:** Must | **Epica:** E6 | **Dipendenze:** US-01, US-05, US-07

---

**[US-20] Visualizzazione dati di compensazione mensile**
> Come Global Admin o Country Manager, voglio visualizzare i dati di compensazione mensile per dipendente con tutti i campi previsti per monitorare i costi del personale.

**Criteri di Accettazione:**
- **Given** una selezione dipendente + periodo, **When** si accede alla vista compensazione, **Then** viene mostrata una tabella con FTE, Commission, Quarterly Bonus, Annual Bonus, Extra Booster, Other Compensation Type per ogni mese del periodo.
- **Given** valori null in alcuni campi, **When** vengono visualizzati, **Then** appaiono come "-" senza generare errori di rendering.
- **Given** il click su una voce mensile, **When** si espande il dettaglio, **Then** vengono mostrati la fonte dell'import (file + timestamp) e il template utilizzato.

**Stima:** M | **Priorità:** Must | **Epica:** E6 | **Dipendenze:** US-19

---

**[US-21] Visualizzazione dati di vendita mensile**
> Come Global Admin o Country Manager, voglio visualizzare i dati di vendita mensile per dipendente/negozio per confrontare le performance rispetto ai target.

**Criteri di Accettazione:**
- **Given** una selezione negozio + periodo, **When** si accede alla vista vendite, **Then** viene mostrata una tabella con Total Sales, HA Sales, Monthly Target, Quarterly Target, Annual Target, Other Sales Type.
- **Given** la visualizzazione dei target vs actual, **When** un valore di vendita supera il target mensile, **Then** viene evidenziato in verde; se è inferiore all'80% del target, in rosso.
- **Given** filtri di periodo che coprono più trimestri, **When** vengono applicati, **Then** i quarterly bonus e target sono proiettati proporzionalmente al periodo selezionato.

**Stima:** M | **Priorità:** Must | **Epica:** E6 | **Dipendenze:** US-19

---

**[US-22] Analisi cross-paese dei KPI di compensazione e vendita**
> Come Global Admin, voglio eseguire analisi comparative tra paesi diversi per identificare differenze strutturali nei costi di compensazione e nelle performance di vendita.

**Criteri di Accettazione:**
- **Given** la selezione di più paesi nel filtro dashboard, **When** si accede alla vista comparativa, **Then** viene mostrato un grafico a barre affiancate con i KPI principali per ogni paese selezionato.
- **Given** la comparazione cross-paese, **When** si seleziona un KPI (es. Commission media), **Then** la vista mostra il valore assoluto e il delta percentuale rispetto alla media globale.
- **Given** un Country Manager, **When** tenta di accedere alla vista cross-paese di BU non associate, **Then** i dati di quelle BU sono oscurati e viene mostrato un messaggio di accesso limitato.

**Stima:** L | **Priorità:** Should | **Epica:** E6 | **Dipendenze:** US-19, US-20, US-21

---

**[US-23] Export completo del database in CSV/Excel**
> Come Global Admin, voglio esportare l'intero dataset (compensazioni + vendite) in formato CSV o Excel per elaborazioni analitiche esterne.

**Criteri di Accettazione:**
- **Given** la pagina export con filtri applicati, **When** si seleziona il formato CSV e si avvia l'export, **Then** viene generato un file con granularità dipendente/negozio/mese con tutti i campi di compensazione e vendita.
- **Given** un dataset di grandi dimensioni (>100.000 righe), **When** l'export viene avviato, **Then** il sistema mostra una barra di progresso e informa l'utente che il file sarà disponibile per il download al termine dell'elaborazione.
- **Given** la scelta del formato Excel, **When** il file viene generato, **Then** include fogli separati per compensazioni e vendite con intestazioni correttamente etichettate.

**Stima:** M | **Priorità:** Must | **Epica:** E7 | **Dipendenze:** US-19

---

**[US-24] Export filtrato per Business Unit e periodo**
> Come Country Manager, voglio esportare solo i dati della mia Business Unit per il periodo selezionato per condividerli con il management locale.

**Criteri di Accettazione:**
- **Given** un Country Manager con BU associate, **When** avvia un export, **Then** il file include solo i dati delle BU di sua competenza, indipendentemente dall'assenza di filtri espliciti.
- **Given** la selezione di un periodo specifico (es. Q1 2025), **When** si avvia l'export, **Then** il file include solo i record del trimestre selezionato.
- **Given** la generazione del file, **When** viene scaricato, **Then** il nome file include i filtri applicati (es. `export_IT_Q1_2025.xlsx`) per facilitarne l'identificazione.

**Stima:** M | **Priorità:** Should | **Epica:** E7 | **Dipendenze:** US-23

---

**[US-25] Visualizzazione dell'audit log completo**
> Come Global Admin o System Admin, voglio visualizzare l'audit log completo delle operazioni di import con possibilità di filtrare per utente, data e tipo di operazione.

**Criteri di Accettazione:**
- **Given** la pagina audit log, **When** vengono applicati filtri per utente e intervallo di date, **Then** vengono mostrate solo le voci corrispondenti ordinate per timestamp decrescente.
- **Given** la visualizzazione di una voce di audit, **When** si espande il dettaglio, **Then** vengono mostrati: file caricato, template usato, strategia duplicati, riepilogo (importati/errori/duplicati/mancanti).
- **Given** un audit log con molte voci, **When** si scorre la lista, **Then** la paginazione è attiva con 50 voci per pagina e navigazione numerata.

**Stima:** M | **Priorità:** Must | **Epica:** E8 | **Dipendenze:** US-18

---

**[US-26] Gestione della sessione e timeout automatico**
> Come sistema, voglio terminare automaticamente la sessione di un utente dopo 30 minuti di inattività per ridurre il rischio di accessi non autorizzati.

**Criteri di Accettazione:**
- **Given** un utente autenticato inattivo per 25 minuti, **When** rimangono 5 minuti alla scadenza, **Then** appare un dialog di avviso con countdown e opzione di proroga della sessione.
- **Given** la scadenza della sessione senza interazione, **When** l'utente tenta di eseguire un'azione, **Then** viene reindirizzato alla pagina di login con messaggio "Sessione scaduta".
- **Given** la proroga della sessione tramite dialog, **When** l'utente clicca "Rimani connesso", **Then** il timer viene resettato a 30 minuti.

**Stima:** S | **Priorità:** Must | **Epica:** E8 | **Dipendenze:** US-01

---

**[US-27] Logging degli errori applicativi e notifica al System Admin**
> Come System Admin, voglio ricevere un report degli errori critici applicativi per poter intervenire tempestivamente in caso di malfunzionamenti.

**Criteri di Accettazione:**
- **Given** un errore di sistema durante un import (es. DB connection failure), **When** si verifica, **Then** viene registrato in un log applicativo strutturato con severity=ERROR, timestamp, stack trace e contesto utente.
- **Given** la presenza di errori critici nelle ultime 24 ore, **When** il System Admin accede alla sezione di monitoraggio, **Then** vede una lista degli errori con frequenza e tipologia.
- **Given** un errore che causa il fallimento completo di un import, **When** viene loggato, **Then** l'import viene marcato come FAILED nell'audit log e l'utente riceve un messaggio di errore comprensibile.

**Stima:** M | **Priorità:** Should | **Epica:** E8 | **Dipendenze:** US-18, US-01

---

**[US-28] Ricerca e filtraggio rapido dell'anagrafica dipendenti**
> Come Global Admin o Country Manager, voglio cercare un dipendente nella lista anagrafica per nome, cognome o Employee ID per accedere rapidamente al suo profilo.

**Criteri di Accettazione:**
- **Given** la lista anagrafica dipendenti, **When** si inserisce una stringa di ricerca, **Then** i risultati vengono filtrati in tempo reale (debounce 300ms) mostrando i dipendenti corrispondenti.
- **Given** un Country Manager nella lista, **When** esegue una ricerca, **Then** vengono mostrati solo i dipendenti delle sue BU associate, anche nella ricerca.
- **Given** nessun risultato corrispondente alla ricerca, **When** la lista è vuota, **Then** viene mostrato un messaggio "Nessun dipendente trovato" con suggerimento di ampliare i criteri.

**Stima:** S | **Priorità:** Should | **Epica:** E2 | **Dipendenze:** US-05

---

**[US-29] Preview dei dati prima dell'esecuzione dell'import**
> Come Country Manager, voglio visualizzare un'anteprima dei dati trasformati prima di eseguire definitivamente l'import per verificare la correttezza del mapping applicato.

**Criteri di Accettazione:**
- **Given** file caricato e template selezionato, **When** si clicca su "Anteprima Import", **Then** viene mostrata una tabella con le prime 20 righe trasformate secondo il mapping, con evidenziazione delle celle che presentano avvisi (es. valore null, formato inatteso).
- **Given** il confronto anteprima, **When** i valori appaiono errati (es. importi troppo grandi per logica ×100), **Then** l'utente può tornare alla Fase 1 per correggere il template senza perdere il file già caricato.
- **Given** l'anteprima confermata, **When** si procede con l'import, **Then** il sistema esegue l'import sull'intero file (non solo sulle 20 righe di anteprima).

**Stima:** M | **Priorità:** Should | **Epica:** E5 | **Dipendenze:** US-12, US-11

---

**[US-30] Configurazione dei parametri di sistema da parte del System Admin**
> Come System Admin, voglio accedere a una sezione di configurazione per gestire i parametri chiave del sistema (es. dimensione massima file, timeout sessione, formato data default) senza richiedere deploy tecnici.

**Criteri di Accettazione:**
- **Given** la sezione configurazione sistema, **When** il System Admin modifica il parametro "dimensione massima file import", **Then** il nuovo limite viene applicato immediatamente per tutti gli utenti senza riavvio del servizio.
- **Given** la modifica di un parametro critico (es. timeout sessione), **When** viene salvata, **Then** viene registrata nell'audit log con il valore precedente, il nuovo valore, l'utente e il timestamp.
- **Given** un valore di configurazione non valido (es. timeout = 0), **When** si tenta il salvataggio, **Then** il sistema mostra un errore di validazione con il range di valori accettabili.

**Stima:** M | **Priorità:** Could | **Epica:** E8 | **Dipendenze:** US-01, US-08

---

## §7 NFR

| ID | Categoria | Descrizione | Metrica Misurabile |
|---|---|---|---|
| NFR-01 | Performance | Il tempo di risposta delle API REST per operazioni di lettura (dashboard, liste) deve essere accettabile in condizioni di carico normale. | P95 ≤ 800 ms con 100 utenti concorrenti |
| NFR-02 | Performance | Il processo di import per file fino a 10.000 righe deve completarsi entro un tempo ragionevole. | Completamento ≤ 60 secondi per 10.000 righe |
| NFR-03 | Performance | La generazione di export fino a 100.000 righe deve completarsi in background senza bloccare l'interfaccia utente. | Generazione ≤ 3 minuti per 100.000 righe |
| NFR-04 | Disponibilità | La piattaforma deve garantire alta disponibilità durante gli orari lavorativi dei paesi target. | Uptime ≥ 99,5% su base mensile (escluse finestre di manutenzione pianificate) |
| NFR-05 | Sicurezza | Tutte le comunicazioni tra client e server devono essere cifrate. | TLS 1.2 o superiore su tutti gli endpoint; nessuna trasmissione HTTP in chiaro |
| NFR-06 | Sicurezza | I token di autenticazione devono avere una scadenza definita e non devono essere memorizzati in localStorage. | JWT con expiry ≤ 1 ora; storage in HttpOnly cookie |
| NFR-07 | Scalabilità | L'architettura deve supportare la crescita del volume dati senza degradazione delle performance. | Database fino a 10 milioni di record senza regressione performance > 20% |
| NFR-08 | Usabilità | L'interfaccia utente deve essere utilizzabile senza formazione specifica da parte di nuovi utenti con competenze informatiche di base. | SUS score ≥ 75 su test con campione di 10 utenti target |
| NFR-09 | Manutenibilità | Il codice sorgente deve rispettare standard di qualità verificabili per facilitare la manutenzione evolutiva. | Code coverage unit test ≥ 80%; zero vulnerabilità critiche (OWASP Top 10) |
| NFR-10 | Conformità | Il trattamento dei dati personali dei dipendenti deve essere conforme alla normativa vigente in materia di protezione dei dati. | Conformità GDPR verificata da audit legale prima del go-live; pseudonimizzazione Employee ID disponibile |
| NFR-11 | Localizzazione | L'interfaccia deve supportare almeno le lingue previste dai paesi target senza interventi tecnici aggiuntivi. | Supporto i18n per almeno 3 lingue (IT, EN, ES) tramite file di configurazione |
| NFR-12 | Ripristino | In caso di failure dell'import, il sistema deve garantire che non rimangano dati parziali nel database. | Rollback transazionale al 100% in caso di errore durante l'import; zero inserimenti parziali |

---

## §8 Piano di Release

| Sprint | Obiettivo | User Stories incluse |
|---|---|---|
| **Sprint 0** | Setup infrastruttura, ambienti CI/CD, scaffolding Angular + Spring Boot + PostgreSQL, definizione contratti API, onboarding team | Nessuna US – attività tecniche |
| **R1 – S01** | Autenticazione e gestione utenti base | US-01, US-02 |
| **R1 – S02** | Gestione utenti avanzata e Business Unit | US-03, US-04, US-08 |
| **R1 – S03** | Anagrafica dipendenti e negozi | US-05, US-06, US-07 |
| **R1 – S04** | Import Wizard Fase 1 – Template base | US-09, US-10 |
| **R1 – S05** | Import Wizard Fase 1 – Versionamento template | US-11, US-28 |
| **R1 – S06** | Import Wizard Fase 2 – Upload e collegamento file | US-12, US-13 |
| **R2 – S07** | Import Wizard Fase 3 – Esecuzione e validazione | US-14, US-15, US-29 |
| **R2 – S08** | Import Wizard Fase 3 – Duplicati e report | US-16, US-17 |
| **R2 – S09** | Audit log e sicurezza sessione | US-18, US-26 |
| **R2 – S10** | Dashboard con filtri base | US-19, US-20 |
| **R2 – S11** | Dashboard vendite e analisi cross-paese | US-21, US-22 |
| **R3 – S12** | Export dati | US-23, US-24 |
| **R3 – S13** | Audit log avanzato e logging errori | US-25, US-27 |
| **R3 – S14** | Configurazione sistema e hardening | US-30, US-04 |
| **R3 – S15** | NFR, performance testing, security audit, UAT finale | NFR-01..NFR-12 – nessuna nuova US |

---

## §9 Matrice Dipendenze

| US | Dipende da | Componente Tecnico |
|---|---|---|
| US-01 | – | Spring Security, JWT Filter, Angular Auth Guard |
| US-02 | US-01 | User Service, User Repository, Angular User Form |
| US-03 | US-02 | User Service (soft-delete), Angular User Management |
| US-04 | US-02, US-03 | BU-User Association Service, Angular BU Selector |
| US-05 | US-01, US-07 | Employee Service, Employee Repository, Angular Employee Form |
| US-06 | US-05 | Employee Service (soft-delete), Cascade Check Service |
| US-07 | US-01 | Shop Service, Shop Repository, Angular Shop Form |
| US-08 | US-01 | BU Service, BU Repository, Angular BU Management |
| US-09 | US-01, US-08 | Template Service, Auto-Mapper Engine, Angular Wizard Step 1 |
| US-10 | US-09 | Conditional Logic Engine, Angular Manual Mapper |
| US-11 | US-09, US-10 | Template Versioning Service, Template Repository |
| US-12 | US-09, US-11 | File Upload Service, File Validator, Angular File Upload |
| US-13 | US-12 | File Linker Service, Key Matching Engine |
| US-14 | US-11, US-12, US-13 | Import Execution Engine, Mapping Applier, Angular Wizard Step 3 |
| US-15 | US-14 | Validation Service, Error Collector, Field Type Validator |
| US-16 | US-14, US-15 | Duplicate Detector, Strategy Handler (SKIP/OVERWRITE) |
| US-17 | US-14, US-15, US-16 | Report Generator Service, Excel/CSV Writer |
| US-18 | US-14 | Audit Log Service, Audit Log Repository |
| US-19 | US-01, US-05, US-07 | Dashboard Controller, Filter Engine, Angular Dashboard |
| US-20 | US-19 | Compensation Data Service, Angular Compensation View |
| US-21 | US-19 | Sales Data Service, Angular Sales View |
| US-22 | US-19, US-20, US-21 | Cross-Country Analyzer, Angular Comparison Charts |
| US-23 | US-19 | Export Service, Large File Generator, Background Job |
| US-24 | US-23 | Export BU Filter, Angular Export Panel |
| US-25 | US-18 | Audit Log Query Service, Angular Audit Log View |
| US-26 | US-01 | Session Manager, Inactivity Timer, Angular Session Dialog |
| US-27 | US-18, US-01 | Application Logger, Error Monitoring Service |
| US-28 | US-05 | Employee Search Service, Angular Search Bar |
| US-29 | US-12, US-11 | Preview Engine, Angular Preview Table |
| US-30 | US-01, US-08 | System Config Service, Config Repository, Angular Config Panel |

---

## §10 Rischi e Domande Aperte

### Rischi

| ID | Descrizione | Probabilità | Impatto | Strategia di Mitigazione |
|---|---|---|---|---|
| R-01 | Eterogeneità dei formati dei file sorgente tra paesi diversi può rendere il mapping automatico inaffidabile. | Alta | Alto | Investire nella Fase 1 del wizard con mapping manuale robusto; formare i Country Manager sulla corretta preparazione dei file. |
| R-02 | Volume di dati storici da migrare nel bootstrap iniziale potrebbe superare i limiti di upload del wizard. | Media | Alto | Prevedere una procedura di bulk import tecnico (script SQL/ETL) per la migrazione iniziale, separata dal wizard utente. |
| R-03 | Resistenza culturale dei Country Manager all'adozione della nuova piattaforma in sostituzione di processi Excel consolidati. | Alta | Medio | Piano di change management, training contestuale, supporto hypercare per i primi 3 mesi post go-live. |
| R-04 | Variazioni normative sulla privacy dei dati (es. GDPR locale) tra paesi potrebbero richiedere adattamenti al modello dati. | Bassa | Alto | Coinvolgere il DPO aziendale nella fase di design; prevedere pseudonimizzazione dell'Employee ID come opzione configurabile. |
| R-05 | Performance degradate in caso di import concorrenti da parte di più Country Manager sullo stesso database. | Media | Medio | Implementare queue asincrona per l'esecuzione degli import; limitare a N import concorrenti per tenant. |
| R-06 | Inconsistenza dei dati storici caricati da diversi operatori senza standard condivisi di codifica (es. Shop Code). | Alta | Alto | Definire e comunicare un data dictionary obbligatorio; validare Shop Code contro l'anagrafica prima dell'import. |
| R-07 | Dipendenza critica dal browser: funzionalità Angular potrebbero non comportarsi uniformemente su tutti i browser aziendali. | Bassa | Medio | Definire browser support matrix (Chrome, Edge, Firefox ultimi 2 major); test E2E automatizzati su tutti i browser supportati. |
| R-08 | Perdita di dati in caso di interruzione di connessione durante il process di upload del file. | Media | Medio | Implementare upload con chunking e resume; validare l'integrità del file ricevuto con hash MD5/SHA256. |
| R-09 | Scope creep: richieste di nuove funzionalità durante lo sviluppo potrebbero destabilizzare il piano di release. | Alta | Medio | Processo formale di change request con impact analysis obbligatoria e approvazione dello Steering Committee. |
| R-10 | Errori nei template di importazione versionati potrebbero propagarsi silenziosamente su import successivi. | Media | Alto | Implementare il meccanismo di preview (US-29) come step obbligatorio; notificare l'utente quando usa una versione non recente del template. |

### Domande Aperte

| ID | Domanda | Responsabile | Data Target Risposta |
|---|---|---|---|
| OQ-01 | È necessario supportare la conversione valutaria automatica nella versione 2.0 o è confermato out of scope? Esistono report cross-paese che richiedono normalizzazione valutaria? | Business Owner + CFO | 2026-03-18 |
| OQ-02 | Qual è la policy aziendale per la retention dei file sorgente caricati? I file devono essere conservati nel sistema o solo i dati estratti? | DPO + Legal | 2026-03-18 |
| OQ-03 | Il campo "Other Compensation Type" e "Other Sales Type" sono campi testuali liberi o devono essere selezionati da una lista predefinita configurabile? | Product Owner | 2026-03-11 |
| OQ-04 | Esiste un SLA formale per i tempi di import e per la disponibilità della piattaforma che deve essere riflesso nei contratti con i Country Manager? | IT Operations | 2026-03-25 |
| OQ-05 | È prevista una fase di migrazione dei dati storici pre-esistenti (Excel/CSV già compilati dai paesi)? Se sì, per quanti anni di storico e con quale procedura? | Program Manager + Data Migration Lead | 2026-03-25 |

---

## BA_AGENT_SIGNATURE_V2
This section must be present and must not be removed.

> **Documento prodotto da:** Business Analyst Agent – RISE Spending Effectiveness  
> **Versione documento:** 2.0  
> **Data produzione:** 2026-03-04  
> **Metodologia:** Agile BDD (Behaviour-Driven Development) con User Stories in formato Given/When/Then  
> **Stack di riferimento:** Angular (Frontend) | Spring Boot (Backend) | PostgreSQL (Database)  
> **Totale User Stories:** 30 (US-01 → US-30)  
> **Totale Epiche:** 8 (E1 → E8)  
> **Totale NFR:** 12 (NFR-01 → NFR-12)  
> **Totale Assunzioni:** 17 (A-01 → A-17)  
> **Totale Rischi:** 10 (R-01 → R-10)  
> **Totale Domande Aperte:** 5 (OQ-01 → OQ-05)  
> **Classificazione:** INTERNO – USO RISERVATO AI TEAM DI PROGETTO

---
*Fine documento – RISE Spending Effectiveness Business Analysis & User Stories v2.0*
