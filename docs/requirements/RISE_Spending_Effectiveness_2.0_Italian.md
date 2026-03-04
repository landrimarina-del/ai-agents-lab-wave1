**RISE Spending Effectiveness -- Requisiti Funzionali per Applicazione
Enterprise**

**2. Panoramica del Sistema**

**2.1 Obiettivo**

L'applicazione **RISE Spending Effectiveness** mira a colmare un gap
critico nella strategia di compensazione globale: la frammentazione e
l'incoerenza dei dati di retribuzione variabile tra Paesi e regioni.\
In molti casi, le informazioni sulle vendite e sulla retribuzione
variabile sono archiviate in formati diversi, sparse tra varie fonti
come SharePoint, OneDrive o file locali, rendendo quasi impossibile
un'analisi trasversale tra Paesi.

Questa applicazione fornisce un approccio centralizzato e strutturato
per:

- Consolidare i dati di performance e compensazione a livello mensile e
  per dipendente, considerando anche i casi in cui un dipendente lavori
  in più negozi durante l'anno.

- Permettere un tracciamento coerente dei risultati di vendita anche
  quando non sono inclusi nei file di compensazione, utilizzando
  esportazioni CSV per colmare i gap.

- Consentire mappature specifiche per Paese e il riuso dei template di
  importazione, riducendo l'elaborazione manuale e migliorando
  l'affidabilità dei dati nel tempo.

L\'applicazione RISE Spending Effectiveness mira a superare la
frammentazione dei dati sulle retribuzioni variabili a livello globale,
centralizzando e standardizzando le informazioni provenienti da fonti
eterogenee per fornire una visione completa, affidabile e utile per le
decisioni strategiche aziendali. L\'applicazione implementata il
caricamento di file XLS o CSV contenenti dati di compensazione e
vendite.

Di seguito è riportato un riassunto delle funzionalità UX previste:

- Gestione dello schema dati

- Caricamento delle informazioni su retribuzione e vendita

- Audit dei caricamenti

- Cruscotto delle informazioni caricate su retribuzioni e vendite

**Configurazioni Ruoli e personaggi**

> **Amministrazione Aziendale:**
>
> Controllo totale di tutte le funzionalità previste nella prima ondata
> e di tutte le Business Units
>
> **Operatore del paese:**
>
> Gestione del caricamento di informazioni su retribuzioni e vendite;
> visibilità delle funzionalità di upload FE e audit dei dati relativi
> solo alle BU(e) associate.
>
> **Amministrazione:**
>
> ruolo di amministratore di sistema responsabile della definizione,
> manutenzione e gestione della configurazione dei parametri chiave del
> sistema come l\'elenco delle unità di business

**2.2 Entità Core**

Per raggiungere i suoi obiettivi, l'applicazione si basa su alcune
entità chiave:

- **Utenti**: Censiti in RISE e gestiti tramite funzionalità di insert,
  update e identificato in modo univoco. Deve essere associato ad uno
  dei 3 ruoli previsti può essere disattivato

- **Dipendente**: Censiti in RISE e gestiti tramite funzionalità di
  insert,update e delete e identificato in modo univoco. Può essere
  associato a più negozi in mesi diversi. Il sistema deve tracciare la
  vendita e la compensazione mensile per ogni shop, anche in caso di
  trasferimento tra shop.

- **Retribuzione del dipendente**: Censiti in RISE e gestiti tramite
  funzionalità di insert,update e delete. Contiene il salario mensile
  del dipendente

- **Dipendente attivo/inattivo**: stato del dipendente nel mese
  considerato.

- **Negozio (Shop)**: legato a un ID univoco, include metadati come
  Paese, regione e area di business. Lo stesso negozio può comparire in
  più dataset nazionali o cambiare ruolo operativo nel tempo.

- **Template di Importazione**: ogni Paese ha il proprio template di
  mapping per estrarre e trasformare i dati dai file. I template devono
  essere memorizzati, modificabili e riutilizzabili da un utente
  amministratore. L'inserimento e modifica di un template deve essere
  guidato con l'utilizzo di un file excel che utente può caricare e la
  cui intestazione rappresenta le informazioni Source che possono essere
  trascinati verso i le informazioni target secondo la natura del
  modello che può essere Compensazione o Vendite.

> Le informazioni target previste per Compensation e Sales:

- Employee ID

- Year

- Month

- Shop Code

> Compensation:

- FTE

- Commission

- Quarterly Bonus

- Annual Bonus

- Extra Booster

- Other Compensation Type

> Sales:

- Total sales

- HA sales

- Monthly target

- Quarterly target

- Annual target

- Other Sales Type

<!-- -->

- **Dati di Compensazione**: inclusi in file appositi da caricare
  associati a dipendenti e negozi per mese tramite ID dipendente e ID
  negozio e utilizzando un template di riferimento.

- **Dati di Vendita**: inclusi in file appositi da caricare associati a
  dipendenti e negozi per mese tramite ID dipendente e ID negozio e
  utilizzando un template di riferimento.

**3. Integrazione e Elaborazione Dati**

**3.1 Fonti Accettate**

Il sistema deve poter integrare dati da più fonti eterogenee e non
strutturate:

- **File locali**: Caricamento manuale Excel/CSV con drag&drop o file
  picker per facilitare la modellazione dei template.

Ogni processo di importazione deve consentire all'utente di specificare
se il file contiene:

- solo compensazione variabile

- solo vendite

Il sistema deve unire in modo intelligente queste fonti in un record
mensile unificato.

------------------------------------------------------------------------

**3.2 Mapping e Import Wizard**

Il sistema include un **Import Wizard** con 3 fasi:

1.  **Definizione Template**:

    - L'utente seleziona Paese e file sorgente.

    - Il sistema propone mapping automatico basato sulle intestazioni.

    - L'utente può mappare manualmente (employee ID, shop ID, mese,
      anno, commissioni, ecc.).

    - Possibili logiche condizionali (es. "moltiplica bonus x100 se
      riportato in %").

    - Il mapping viene salvato come template riutilizzabile per
      Paese/tipo file.

2.  **Registrazione Fonte**:

    - Per file locali: upload manuale.

    - Se vendite e compensazione sono separati, vanno collegati
      entrambi.

3.  **Esecuzione Import Manuale**:

    - L'utente lancia l'import.

    - Il sistema applica mapping, valida i dati e mostra risultati:
      righe importate, duplicati, errori, dati mancanti.

    - L'utente può scaricare report di validazione o correggere e
      ripetere.

    - Ogni import è loggato con utente, timestamp, file e riepilogo.

------------------------------------------------------------------------

**3.3 Validazione & Pulizia**

Import Manuale deve superare controlli prima di essere salvata:

- **Duplicati**: rilevati su chiave employee+shop+mese+anno. L'utente
  può forzare o saltare.

- **Validazione campi**: obbligatori (ID, shop, mese), numerici, date
  coerenti.

- **Report post-import**: riepilogo con righe processate, valide,
  scartate, duplicati.

- **User guidance**: messaggi chiari, colori, tooltip, anteprima
  tabelle, help contestuale.

------------------------------------------------------------------------

**4. Dashboard**

**4.3 Visualizzazioni**

- Filtri: anno, Paese, regione, negozio, dipendenti.

------------------------------------------------------------------------

**5. Portabilità Dati**

Il sistema deve permettere esportazione completa del DB in CSV/Excel,
per supportare altri team globali (es. Network Footprint).

- Include tutti i record processati e validati.

- Mantiene granularità fino a livello dipendente/negozio/mese.
