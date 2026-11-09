# Prezzi e campioni: gestione e pubblicazione

Le pagine `/:lang/pricing` e `/:lang/colors-finishes` seguono la linea minimal delle pagine pubbliche: sezioni aperte, testo e separatori sottili. Riutilizzano hero, select, checkbox, anteprima immagini, token e SEO esistenti, senza card decorative, ombre o badge nel contenuto delle guide. Non cambiano il calcolo dei prezzi e non aggiungono endpoint pubblici. La prima consulenza gratuita riguarda il primo confronto; le attività successive richiedono preventivo e accordo prima dell’inizio.

## Back-office

In **Admin → Media**:

- **Campioni filamento** elenca le varianti attive restituite dalle opzioni del calcolatore, comprese quelle temporaneamente esaurite. Caricare foto reali, compilare titolo e alt nelle quattro lingue, impostare primaria e ordinamento. L’associazione usa `FILAMENT_VARIANT` e l’ID numerico della variante convertito in `usageKey`; `ownerId` resta vuoto perché accetta UUID, non ID filamento. Non modificare l’ID se cambia il nome del colore.
- **Esempi di prezzo** offre i tre slot `functional`, `decorative`, `series` con `PRICING_EXAMPLE`. Caricare la foto reale e compilare i dati del preventivo, titolo e alt nelle quattro lingue. Quantità: 1 per componente funzionale e decorativo, 10 per la serie. Il prezzo è il totale indicativo in CHF, non un prezzo unitario per la serie. Le dimensioni sono L × P × H in mm.
- Nei testi delle impostazioni specificare almeno qualità/altezza strato, ugello, riempimento, supporti e orientamento rilevante. Nei costi inclusi/esclusi specificare materiale, stampa, preparazione, eventuali lavorazioni, IVA e spedizione. Non assumere aliquote o gratuità non confermate.
- L’esempio rimane una bozza finché **Confermo foto reale e dati verificati dal preventivo** non è selezionato. Il backend rifiuta esempi verificati incompleti, con prezzo non positivo/più di due decimali, data futura, quantità errata o testi mancanti. La lettura pubblica esclude le bozze anche se l’associazione è attiva. Le immagini devono anche essere READY e PUBLIC, come negli altri Media.
- **Modifica / sostituisci** consente di modificare dati e testi senza selezionare un nuovo file. Se si seleziona un file, il flusso esistente crea la nuova associazione e disattiva la precedente. Per ritirare un esempio, togliere la verifica e salvare, oppure disattivare l’associazione. Se sono presenti più foto/preventivi nello stesso slot, viene scelto quello primario, poi il primo per ordinamento: tenere un solo preventivo corrente attivo.

Non inserire riferimenti privati a sessioni, token, nominativi o indirizzi nei dati pubblici. Conservare la prova originale del preventivo internamente. La verifica è una dichiarazione editoriale dell’amministratore: il sistema non ricalcola il prezzo e non certifica che un’immagine sia una fotografia reale. Al momento della consegna non sono stati inseriti esempi reali, come concordato: nessun importo o risparmio inventato.

## Protocollo fotografico ripetibile

Usare per ogni variante lo stesso modello campione, con una faccia piana, una curva e una piccola inclinazione per mostrare gli strati. Fissare dimensioni e orientamento. Registrare filamento/variante, lotto, stampante, ugello, altezza strato, riempimento e supporti; se il materiale richiede parametri diversi annotare l’eccezione. Non attribuire la differenza di texture al colore quando dipende dalle impostazioni.

Illuminazione diffusa neutra (circa 5000 K), stesso fondale grigio neutro opaco, stessa distanza, treppiede e inquadratura. Impostare bilanciamento del bianco ed esposizione manuali con carta grigia; fissare obiettivo, ISO, diaframma, messa a fuoco e sviluppo. Disattivare filtri estetici e correzioni automatiche variabili. Registrare le impostazioni in una scheda di ripresa e mantenerle uguali nella sessione.

Produrre tre fotografie per variante: (1) generale frontale, (2) obliqua per i riflessi, (3) dettaglio della superficie per gli strati. Usare la generale come primaria e ordinare 0, 1, 2. Esportare JPEG/WebP in sRGB con risoluzione sufficiente per il preset hero (1280 px), conservando gli originali internamente. La pipeline Media applica validazione, antivirus e generazione dei formati già esistenti. Alt text: descrivere colore, materiale, punto di vista e superficie osservabile senza affermazioni non verificabili.

Confrontare sullo stesso schermo le foto di due varianti prima di pubblicarle. La pagina avverte che schermo e luce cambiano la resa. In assenza di immagini o dopo un errore di caricamento mostra la campitura e «Foto non ancora disponibile». Le finiture sono quelle del filamento: non implicano verniciatura o lucidatura.

## Preventivi della concorrenza

Ricognizione delle fonti ufficiali del 23 ottobre 2026: [TEIL3, Zurigo](https://www.teil3.ch/3d-printing-service-switzerland.html) e [11d.ch](https://11d.ch/?lang=en) presentano servizi di preventivazione per stampa 3D sul mercato svizzero. Sono candidati per la raccolta, non confronti di prezzo già validati. I listini o valori «a partire da» non equivalgono a preventivi sugli stessi file.

Preparare i tre campioni e ottenere prima i nostri preventivi reali. Per ognuno conservare file/versione e hash, dimensioni, unità, materiale/colore/finitura, processo, ugello/strato/riempimento/supporti, quantità (1, 1, 10), post-lavorazione, data, validità, IVA, spedizione nella stessa località e tempi richiesti. Inviare solo modelli del laboratorio di cui è autorizzata la condivisione. Raccogliere i sei preventivi (tre casi per due fornitori), conservare documento/screenshot datato e riferimento pubblico pertinente. Segnalare ogni specifica non selezionabile o non dichiarata; non dedurre equivalenza.

| Caso | Nostro preventivo | TEIL3 | 11d.ch | Esito attuale |
| --- | --- | --- | --- | --- |
| Piccolo componente, 1 pezzo | Da inserire dal back-office | Da raccogliere sul medesimo file | Da raccogliere sul medesimo file | Nessun confronto pubblicabile |
| Decorativo, 1 pezzo | Da inserire dal back-office | Da raccogliere sul medesimo file | Da raccogliere sul medesimo file | Nessun confronto pubblicabile |
| Serie, 10 pezzi identici | Da inserire dal back-office | Da raccogliere sul medesimo file | Da raccogliere sul medesimo file | Nessun confronto pubblicabile |

Confrontare totali sullo stesso perimetro di imposte e consegna, senza confondere prezzi unitari e di serie. Se completamente equivalente e documentato, calcolare `(concorrente − nostro) / concorrente × 100` con denominatore positivo, e riportare «In questo esempio, X% in meno rispetto a [fornitore]», specifiche, data e riferimento. Un risultato negativo non è un risparmio. Non generalizzare. Attualmente non esistono dati equivalenti: la pagina non pubblica percentuali e il back-office non offre un campo per affermazioni competitive non verificate. Un eventuale confronto pubblico sarà una modifica editoriale revisionabile successiva.

## Contratto e distribuzione

`CalculatorOptionsService` legge le opzioni senza istanziare servizi di sessione; il calcolatore delega allo stesso servizio. La disponibilità usa esattamente `isOutOfStock` del catalogo, senza soglie di stock inventate. Le varianti non attive non sono catalogate; i Media restano conservati se una variante viene temporaneamente disattivata.

`MediaUsage.pricing_example` è una colonna JSON nullable, coerente con il mapping già usato per le righe servizio. È una modifica additiva sotto `ddl-auto=update`: distribuire prima il backend, verificare l’aggiunta della colonna, poi il frontend. Le associazioni precedenti hanno null e mantengono il comportamento esistente; un rollback applicativo può lasciare la colonna. Non richiede riscrittura di prezzi, quote o inventario. Non eseguire DDL distruttivo. Gli endpoint Media esistenti accettano il campo opzionale `pricingExample`; `@Valid` e i controlli del servizio proteggono la pubblicazione. Le stesse protezioni admin/sessione/CSRF restano applicate.

Le due route registrano le chiavi SEO nel router e le quattro versioni nella sitemap statica. L’interceptor SSR consente l’origine API interna solo per opzioni e Media su queste due pagine, riutilizzando l’allowlist esistente; nessun accesso transazionale viene aggiunto. Prezzi continua a rendere il contenuto informativo se le immagini non sono disponibili; Colori mostra l’errore e permette di riprovare. SSR restituisce 503 con Retry-After per un catalogo indisponibile, evitando di indicizzare un catalogo vuoto come risposta riuscita.

Lo [studio stagionale](plans/seasonal-campaigns.md) resta separato: non attiva campagne o automazioni.

## Verifiche eseguite il 23 ottobre 2026

- TypeScript applicazione, specifiche ed E2E; parità IT/EN/DE/FR; riuso UI e inventario route: passati.
- Build produzione browser + SSR: passata. Restano due avvisi di budget SCSS già presenti in Home e Dashboard amministrativa; i nuovi fogli di stile rispettano i limiti.
- 31 test frontend mirati: passati, inclusi Media amministrativi, caricamento asincrono, opzioni/calcolatore, Materiali, selettore colori, interceptor SSR e dialoghi.
- 14 test backend mirati: passati, inclusi salvataggio e ritiro degli esempi, filtro pubblico delle bozze, validazione e round-trip JSON con Media preesistenti. Il test usa H2 isolato con compatibilità PostgreSQL e adatta solo la sintassi di binding JSON a `FORMAT JSON`, come documentato da [H2](https://www.h2database.com/html/datatypes.html#json); non modifica SQL di produzione. Il rollout sul PostgreSQL di produzione resta da eseguire.
- `check:guides-ssr` e `check:shop-ssr`: passati sul bundle di produzione. Verificati contenuti e metadati delle otto URL, immagini e dati esempio, assenza di prezzi quando mancano esempi verificati, sitemap e risposta 503/Retry-After sul catalogo indisponibile.
- Sei scenari Playwright passati (Chromium desktop e mobile): filtri, massimo due varianti, persistenza del confronto cambiando filtro, stato esaurito, ingrandimento, contenimento e ritorno del focus, Escape, foto mancanti, API Media non disponibile, immagini 404, catalogo vuoto, recupero dall’errore, quattro lingue e contatto con consulenza preselezionata. Verificati screenshot desktop 1440 px e mobile 390 px. Firefox e WebKit non eseguiti in questa sessione.
- Le prove browser/SSR usano risposte esplicitamente sintetiche, senza creare preventivi o caricare fotografie reali. Non sostituiscono la verifica visiva dei campioni del laboratorio né una prova end-to-end su PostgreSQL con upload/scansione reali. Il browser integrato non era disponibile; è stato usato il Playwright già installato nel progetto.
- Nessuna dipendenza aggiunta o aggiornata. `node scripts/check-external-packages.mjs`: 45/45 verifiche superate. Formattazione con il Prettier 3.6.2 già previsto dal workflow del repository, senza installare pacchetti.

Le immagini di verifica in `frontend/test-results/` sono artefatti ignorati e mostrano dati sintetici; non sono fotografie da pubblicare. Lo studio stagionale cita le fonti Google verificate e non promette un risultato SEO.

### Revisione visiva minimal

La revisione richiesta mantiene la tipografia pubblica di Contatti/Materiali e
l’impostazione aperta di Chi siamo e delle pagine legali. Le guide usano le
primitive `ui-guide-*` in `_ui.scss`: sezioni separate da una linea sottile,
contenuto centrato e spaziatura uniforme. I fattori di prezzo sono una lista di
definizioni, gli esempi sono articoli con foto e dettagli affiancati; il catalogo
non usa card, ombre o badge di disponibilità. Il confronto compare dopo la prima
selezione. Il richiamo introduttivo nei contatti usa lo stesso trattamento aperto.

Verificati nuovamente build produzione/SSR, TypeScript, i18n e riuso UI, con sei
scenari browser passati su Chromium desktop/mobile. Confrontate le anteprime con
Contatti e controllate entrambe le guide su mobile anche in tedesco. Per il caso
immagine 404 i test usano URL univoci, evitando di riutilizzare immagini già
caricate dal browser nella pagina Prezzi.

### Navigazione raggruppata — 26 ottobre 2026

Navbar e footer condividono ora il gruppo **Guide alla stampa**: Qualità e
Materiali, Colori e finiture, Prezzi, nello stesso ordine e con link Angular
localizzati. Su desktop la navbar usa una tendina compatta; nell’hamburger le tre
scelte sono inizialmente visibili, con possibilità di richiudere il gruppo. Il
footer le raccoglie in una colonna distinta dalle informazioni di contatto e legali.
La pagina attiva è indicata anche con `aria-current`, Escape restituisce il focus,
e la navigazione chiude il menu senza ricaricare il documento.

Passati build produzione/SSR, TypeScript applicazione ed E2E, parità i18n,
riuso UI e inventario route. Passate 20 prove browser su Chromium desktop/mobile
(nuova navigazione, guide e navigazione preesistente nelle quattro lingue),
inclusi tastiera, clic esterno, interazione con il carrello e larghezza 320 px.
Verificate le anteprime della navbar, dell’hamburger e del footer. Le prove
usano il server locale sintetico già documentato; Firefox/WebKit e pubblicazione
non sono stati eseguiti. Rimangono i precedenti avvisi di budget SCSS di Home e
Dashboard; nessuna nuova dipendenza, verifica registry 45/45 passata.


### Apertura al passaggio del mouse e sezione CAD

La tendina delle guide si apre anche al passaggio di un mouse su desktop; il
passaggio tra etichetta e link è continuo. Clic e tastiera restano disponibili,
mentre il comportamento touch resta basato sui tocchi. Nella pagina Prezzi la
sezione «Tre modi di usare la stampa 3D» è sostituita da una sezione sulla
progettazione CAD: riutilizza la descrizione del servizio già presente nei
contatti e offre un collegamento localizzato alla richiesta di consulenza.
Gli slot degli esempi reali restano disponibili per la gestione dal back-office.
