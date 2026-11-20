# Campagne stagionali — studio, 23 ottobre 2026

Stato: proposta per una fase successiva. Nessuna campagna, pianificazione automatica o pubblicazione attivata da questa implementazione.

## Pilota: Natale

Obiettivo: aiutare chi cerca regali personalizzati e piccole decorazioni a capire cosa è realizzabile, scegliere una finitura e inviare un modello o una richiesta concreta. Non promettere una data di consegna senza conferma della capacità produttiva.

Proposta per la home: una sezione dopo le capability esistenti, composta da titolo, breve descrizione, tre applicazioni fotografate (segnaposto con nome, decorazione sospesa, piccolo regalo personalizzato), collegamenti a Colori e finiture, Prezzi e calcolatore. Riutilizzare `app-feature-panel`, il sistema Media e le card già in uso. Le fotografie saranno di stampe reali con autorizzazione alla pubblicazione. Evitare immagini sintetiche presentate come prodotti realizzati.

Nel calcolatore: un solo richiamo testuale discreto sopra l’area di lavoro, con link alla guida stagionale. Non coprire caricamento, selezione materiali o preventivo e non introdurre modali. Un piccolo motivo geometrico ispirato al fiocco di neve può accompagnare la sezione home, usando token e forme del marchio; niente animazioni continue, cambi del logo o modifiche globali dei colori.

La guida proposta `/:lang/ideas/christmas` sarà permanente, con esempi originali, misure, materiali adatti al singolo uso, limiti di stampa e istruzioni per la personalizzazione. Non viene aggiunta ora come pagina vuota. Prezzi e disponibilità devono rimandare alle fonti correnti; eventuali termini di consegna sono editoriali, datati e approvati.

### Bozza di messaggio per le quattro lingue

| Lingua | Titolo home | Testo | CTA |
| --- | --- | --- | --- |
| IT | Un’idea personale, stampata per Natale | Segnaposto con nome, decorazioni e piccoli regali: raccontaci la tua idea o carica il tuo modello. | Prepara il tuo progetto |
| DE | Eine persönliche Idee, zu Weihnachten gedruckt | Namensschilder, Dekorationen und kleine Geschenke: Erzähle uns von deiner Idee oder lade dein Modell hoch. | Plane dein Projekt |
| FR | Une idée personnelle, imprimée pour Noël | Marque-places avec prénom, décorations et petits cadeaux : raconte-nous ton idée ou charge ton modèle. | Prépare ton projet |
| EN | A personal idea, printed for Christmas | Named place cards, decorations and small gifts: tell us your idea or upload your model. | Prepare your project |

Questi testi sono bozze per la revisione, non contenuti già pubblicati. Evitare promesse su sicurezza alimentare, giocattoli, esposizione a fiamme o contatto con lampadine calde senza verifica del caso concreto.

## Calendario

L’agente eseguirà un controllo settimanale (proposta: lunedì 09:00, Europe/Zurich). Quando una ricorrenza entra nell’orizzonte dei 42 giorni prepara una sola proposta per anno e campagna; se il controllo cade dopo la soglia, la prepara al primo controllo utile. Per avere la proposta pronta esattamente sei settimane prima, può prepararla al controllo precedente e fissare quella data come scadenza di revisione.

| Ricorrenza | Opportunità concreta | Preparazione | Attivazione proposta | Fine proposta |
| --- | --- | --- | --- | --- |
| Natale, 25 dicembre | Regali personalizzati, segnaposto, decorazioni | Dal 13 novembre; pilota 2026 | 20 novembre, dopo approvazione | 27 dicembre ore 00:00 |
| Pasqua, data mobile | Segnaposto, piccoli contenitori decorativi, decorazioni | Data della domenica di Pasqua meno 42 giorni; verificare ogni anno | 28 giorni prima | Martedì dopo Pasqua ore 00:00 |
| Halloween, 31 ottobre | Sagome, decorazioni, accessori espositivi | Dal 19 settembre; prossimo ciclo completo 2027 | 1 ottobre | 2 novembre ore 00:00 |
| Festa nazionale svizzera, 1 agosto | Segnaposto, decorazioni da tavolo, targhette | Dal 20 giugno | 11 luglio | 3 agosto ore 00:00 |

Le finestre sono proposte operative. L’approvazione deve includere le date effettive, la disponibilità e la capacità del laboratorio. Nessuna attivazione implicita per il solo passaggio del tempo.

## Flusso futuro dell’agente

1. Leggere il calendario, cercare una proposta esistente per `campaignId + year`, e preparare un branch/PR revisionabile; nessuna scrittura diretta sul sito pubblicato.
2. Esaminare catalogo, disponibilità e libreria Media; selezionare fotografie esistenti autorizzate. Segnalare le immagini mancanti senza inventarle. Preparare testi IT/DE/FR/EN e alt text, con link localizzati verso contenuti permanenti, Prezzi e Colori e finiture.
3. Produrre anteprima desktop/mobile e diff: testi, immagini, collegamenti, date, eventuali condizioni commerciali. Eseguire TypeScript, i18n, riuso UI, build e controlli SSR/accessibilità pertinenti.
4. Richiedere approvazione esplicita del proprietario sul contenuto concreto e sulle date. Una modifica dopo l’approvazione richiede una nuova revisione. Non inviare messaggi o email né pubblicare senza autorizzazione.
5. Un futuro record approvato conterrà `campaignId`, stato, revisione approvata, `startsAt`, `endsAt` e riferimenti ai contenuti/media. La UI mostrerà la campagna solo quando `approved && startsAt <= now && now < endsAt`, valutando gli stessi istanti su server e client.
6. Alla scadenza la UI torna alla home ordinaria senza un’azione dell’agente. Usare timestamp con offset/UTC, anteprima con orologio fissato, cache che non sopravviva alla prossima transizione e una procedura di disattivazione manuale. Il contenuto permanente utile resta consultabile tutto l’anno, con termini stagionali aggiornati.

Prima di implementare il record di campagna definire persistenza, invalidazione cache e gestione dell’approvazione. Il sistema non esiste ancora: questo studio non introduce cron, credenziali o automazioni.

## SEO e misurazione

Un cambio di decorazione da solo non ha un beneficio SEO documentato. Google invita a produrre contenuti utili, originali e rivolti alle persone; non garantisce posizioni. I suggerimenti e le fotografie reali del laboratorio possono rispondere a ricerche specifiche, ma questo è un risultato da misurare, non una promessa. [Google Search Central: contenuti utili](https://developers.google.com/search/docs/fundamentals/creating-helpful-content), consultato il 23 ottobre 2026.

Per eventi ricorrenti Google raccomanda URL riutilizzabili e collegamenti dalla home. L’applicazione alle nostre campagne è una proposta: pagine permanenti come `/:lang/ideas/christmas`, senza anno nell’URL, pubblicate solo quando sostanziali e mantenute negli anni. Collegarle internamente, includerle nella sitemap e applicare canonical/hreflang tramite il sistema SEO esistente. [Google: campagne ricorrenti](https://developers.google.com/search/blog/2020/10/best-practices-black-friday), consultato il 23 ottobre 2026.

Rilevare in Search Console impressioni, clic, CTR, query e pagine di destinazione, separando paese e lingua. Confrontare finestre stagionali equivalenti anno su anno; per Pasqua allineare le settimane rispetto alla ricorrenza. Annotare data di pubblicazione, variazioni delle pagine e disponibilità. Affiancare il numero di richieste di preventivo/contatto già disponibili; attribuire conversioni alla campagna solo se il dato esiste davvero. Non aggiungere tracking o cookie in questo studio. Senza storico, il primo ciclo crea una baseline; non attribuire causalità a semplici variazioni di traffico.

## Criteri di accettazione della futura implementazione

Nessuna campagna visibile senza approvazione; nessuna attivazione oltre la finestra; ripristino ordinario testato prima, durante e dopo la scadenza anche con SSR/cache; quattro lingue complete; link e fotografie reali verificati; mobile e tastiera controllati. Registrare risultati e limiti del pilota prima di estendere alle altre ricorrenze.
