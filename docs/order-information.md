# Istruzioni e allegati degli ordini

Il calcolatore e il checkout condividono una bozza salvata sul server. Ogni nuovo
preventivo prodotto dal ricalcolo fa riferimento alla stessa bozza. Al momento
dell'ordine, nello stesso commit, testo, riferimenti ai modelli e file vengono
copiati in un archivio separato dell'ordine. Le aggiunte successive sono append-only,
con identificativo e timestamp propri; il comando «Segna come lette» riconosce
solo gli identificativi mostrati, senza nascondere aggiunte concorrenti.

## Accesso e compatibilità

Le nuove API richiedono una chiave casuale di 256 bit, separata dall'identificativo
di preventivo/ordine/file. Le chiavi non compaiono nelle risposte pubbliche di
consultazione degli ordini o dei preventivi. La risposta di creazione dell'ordine
fornisce la sua chiave al browser acquirente. Il link nell'email di conferma include
la chiave nel frammento URL (non inviato al server web); la pagina la memorizza
localmente e rimuove il frammento dalla barra degli indirizzi.

Le API amministrative continuano a richiedere sessione e controllo CSRF esistenti.
La card amministrativa consente di copiare il link riservato del cliente, da
condividere tramite i canali attuali. Aprire un ordine preesistente in amministrazione
inizializza il suo archivio privato e conserva le vecchie note; occorre condividere
questo nuovo link per consentire al cliente di aggiungere informazioni a un ordine
la cui vecchia conferma non contiene la chiave.

Le credenziali delle bozze sono conservate nel browser che le ha create. Aprire
un preventivo con allegati in un altro browser richiede la credenziale della bozza;
il semplice identificativo non autorizza l'accesso. La funzione non cambia le
regole di accesso delle precedenti API pubbliche dell'ordine.

## File e rilascio

- JPEG, PNG e PDF, controllati tramite estensione, MIME e firma dei contenuti,
  quindi sottoposti al servizio antivirus esistente.
- Massimo 10 MB per file, 10 file e 30 MB complessivi per bozza/ordine; 5000 caratteri
  per nota e 100 voci nello storico. Le foto vengono visualizzate tramite blob
  autenticati; i download hanno `no-store`, `nosniff` e disposition `attachment`.
- Directory predefinita: `storage_orders/information`, già dentro il volume degli
  ordini del compose di deploy. Configurabile con `storage.information-root`.
  Includerla nei backup insieme alla tabella `order_information`.
- La pulizia giornaliera alle 03:30 rimuove le bozze scadute e i relativi file
  (30 giorni di conservazione, prolungati quando un preventivo dura più a lungo).
  Le copie degli ordini non dipendono dalla durata della bozza.
- Schema additivo: tabella `order_information`, colonne `information_draft_id`,
  `information_token` e `client_model_key`; nessuna migrazione di prezzi o stati.
  Le definizioni sono anche in fondo a `db.sql`. L'attuale `ddl-auto=update`
  crea lo schema. Gli ordini precedenti vengono inizializzati alla prima apertura
  della loro card amministrativa, senza un aggiornamento massivo dei dati.
- Rilasciare backend e frontend insieme: il checkout dei preventivi con istruzioni
  richiede la nuova credenziale. Ripristinare solo un vecchio backend permetterebbe
  di creare ordini senza la nuova copia delle istruzioni.

## Verifiche

I test di integrazione usano H2 e file temporanei per verificare copia all'acquisto,
rimozione, pulizia, MIME contraffatti, limiti, accessi incrociati, riferimenti ai
modelli, timestamp e append concorrenti. I test MVC verificano sessione/CSRF sulle
API admin. I test Angular verificano il recupero della bozza, le credenziali,
le anteprime/rimozioni, lo storico, la conferma e l'acknowledgement amministrativo.

Per un controllo sul deployment: inserire una nota e un allegato nel calcolatore,
ricalcolare, passare al checkout, acquistare, aprire il link riservato della
conferma, aggiungere una nota e verificare il badge amministrativo prima e dopo
«Segna come lette». Il test non richiede modifiche al pagamento o agli stati.
