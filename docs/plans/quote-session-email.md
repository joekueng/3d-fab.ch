# Salvataggio della sessione via email

## Implementazione

La scheda “Salva la sessione” si trova nella colonna sinistra, sotto la scheda con “Calculate Quote”. Con “Copia link” si copia il link completo; inserendo l’email lo stesso link viene inviato. Si usa un unico link per riprendere il lavoro su un altro dispositivo, senza account e senza dipendere dal localStorage del browser originale.

- Note e allegati risiedono sul server, associati alla sessione.
- I nuovi allegati devono superare ClamAV prima del salvataggio. Scanner disabilitato, irraggiungibile o esito sconosciuto bloccano l’upload.
- Prima dell’email vengono ricontrollati anche gli allegati già salvati, compresi quelli caricati con il precedente comportamento antivirus permissivo.
- Le modifiche a modelli e impostazioni devono essere ricalcolate prima dell’invio. Il modulo email salva le note e i file ancora in sospeso.
- Il link contiene solo ?session=<id>. Per scelta del prodotto, il normale link del preventivo consente di recuperare tutta la sessione; il server risolve le informazioni associate dal database, senza informationKey nell’URL.
- Il server verifica stato e scadenza della sessione; un browser nuovo recupera modelli, impostazioni, quantità, preventivo, note e allegati senza credenziali pregresse nel localStorage.
- Anche “Salva” nel modulo istruzioni collega le informazioni alle sessioni precedenti che non avevano ancora un’area allegati. Il normale URL della sessione è sufficiente anche per le informazioni aggiunte successivamente.
- Il link viene rifiutato se la sessione è scaduta o convertita in ordine. L’invio non estende la scadenza della sessione.
- Sono disponibili interfaccia e email IT/EN/DE/FR. Il servizio SMTP esistente invia l’email; l’audit registra solo i metadati, senza URL o credenziale.
- Il limite di invio è uno al minuto per sessione e destinatario, oltre al limite IP esistente. Il limite aggiuntivo è in memoria per istanza.

## API

| Endpoint | Funzione |
| --- | --- |
| PUT /api/quote-sessions/{id}/information | Collega il draft autorizzato alla sessione |
| POST /api/quote-sessions/{id}/link | Salva e verifica la sessione, poi restituisce il link da copiare |
| POST /api/quote-sessions/{id}/email | Verifica e ricontrolla i file, poi invia il link |
| POST /api/quote-sessions/{id}/resume | Verifica stato e scadenza e risolve dal database le informazioni collegate |

L’accesso tramite link completo è intenzionale per le sessioni di preventivo, come richiesto. I draft indipendenti e le informazioni degli ordini mantengono i controlli separati già esistenti.

## Compatibilità e configurazione

Non sono richieste nuove colonne o migrazioni: si riutilizzano sessioni, draft e audit esistenti. Le email usano il dominio configurato in APP_FRONTEND_BASE_URL e la scadenza effettiva della sessione (durata predefinita: sei mesi).

Per gli allegati ClamAV deve essere attivo e raggiungibile: impostare CLAMAV_ENABLED=true e CLAMAV_HOST sul nome del servizio Docker o su localhost per un backend eseguito sull’host. Il profilo locale disabilita le email per default: gli invii reali richiedono APP_MAIL_ENABLED=true e SMTP configurato. Il comportamento degli altri uploader non è stato modificato. I file esistenti rimangono nello storage privato e continuano a seguire la pulizia dei draft esistente.

## Verifiche

- Test backend su autorizzazione, scadenza, conversione, limiti, validazione API, scansione e invio fallito/disabilitato.
- Test Chrome su recupero senza localStorage, download protetto, salvataggio prima dell’email, errore scanner, collegamento delle sessioni precedenti e blocco dei doppi clic.
- Prova opzionale con ClamAV reale tramite CLAMAV_LIVE_TEST=true: file innocuo accettato e campione EICAR bloccato.
- Controlli Angular, traduzioni e riuso UI.

Gli invii SMTP nei test sono simulati: non vengono inviate email reali a destinatari esterni.

Durante Copia link e invio email la pagina resta modificabile. Le risposte dei salvataggi in background preservano le modifiche successive e i file aggiunti nel frattempo.
