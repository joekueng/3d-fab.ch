# Collegamento tra calcolatore e catalogo colori

Stato: proposta tecnica, 9 novembre 2026. Analisi del repository; funzionalità non implementata.

## Obiettivo e percorso utente

Consentire a chi configura un modello di consultare foto, finiture e disponibilità,
scegliere una variante precisa e tornare al proprio lavoro.
«I suoi colori» viene interpretato come le varianti disponibili per il materiale
del modello, senza introdurre preferiti personali o un account.

1. Nella scheda di ogni file, vicino al selettore esistente, aggiungere «Vedi foto e finiture».
2. Aprire `/:lang/colors-finishes` nella stessa scheda tramite navigazione Angular.
   La pagina riceve il contesto del modello e parte dal suo materiale e dalla variante attuale.
3. Mostrare un richiamo «Stai scegliendo il colore per [nome file]», la selezione corrente
   e «Torna al calcolatore» anche prima che il catalogo abbia terminato il caricamento.
4. Conservare foto ingrandibili e confronto di massimo due varianti. Il confronto
   e la scelta da applicare sono azioni distinte.
5. Su una variante disponibile, «Usa questo colore» aggiorna la bozza e torna al
   calcolatore nella modalità di partenza, sul modello interessato.
6. «Torna al calcolatore» e Indietro del browser conservano il lavoro senza applicare
   una scelta non confermata.
7. Se esiste un preventivo e la variante cambia, mostrare che occorre ricalcolare
   prima di proseguire. Nessun avvio automatico dello slicer durante la consultazione.

La prima versione applica il colore al singolo file, anche con impostazioni di stampa
condivise: l'interfaccia attuale esclude già il colore dalla sincronizzazione globale.
L'applicazione a tutti i modelli sarebbe un'azione aggiuntiva esplicita.
Il cambio materiale resta nel calcolatore: nel flusso di scelta si selezionano solo
varianti del materiale del modello. La visita autonoma alla guida conserva i filtri
liberi e il collegamento attuale al calcolatore, senza applicazioni a bozze estranee.

## Riscontri nel codice attuale

| Area                                                | Stato attuale e implicazione                                                                                                                                                                                                                 |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `components/upload-form/upload-form.component.html` | Il collegamento generale ai colori usa `href` e `target="_blank"`: apre una scheda separata senza contesto né restituzione della scelta.                                                                                                     |
| `colors-finishes/colors-finishes-page.component.*`  | Filtri materiale/finitura, foto, disponibilità e confronto esistono; manca l'azione di applicazione. Il link finale va sempre a `calculator/basic`.                                                                                          |
| `services/calculator-options.service.ts`            | Legge `/api/calculator/options` senza creare o riprendere sessioni. È già condiviso tra guida e calcolatore.                                                                                                                                 |
| `services/quote-estimator.service.ts`               | `PendingCalculatorDraft` conserva richiesta, oggetti `File`, anteprime, sincronizzazione e nome del file selezionato in memoria.                                                                                                             |
| `calculator-page.component.ts`                      | `persistPendingDraft()`, `switchMode()` e il ripristino sessione forniscono il precedente da estendere; il ripristino locale deve prevalere sui valori server meno recenti.                                                                  |
| `upload-form.component.ts`                          | `updateItemColor()` aggiorna colore e variante per indice; l'anteprima 3D risolve già l'hex dalla variante. Il ripristino conserva i `clientModelKey`.                                                                                       |
| `shared/components/print-item-controls`             | Controlli quantità/colore condivisi col checkout; eventuale azione di apertura deve essere opzionale e comunicata al proprietario della pagina.                                                                                              |
| Controllo di ricalcolo                              | Le firme in `trackedItemSignature()` non includono colore o variante; `updateItemColor()` non emette un evento dedicato verso la pagina. Collegare solo il catalogo lascerebbe questo caso scoperto.                                         |
| Backend                                             | `QuoteCalculator` usa `variant.getCostChfPerKg()`: una variante diversa può modificare il prezzo. Il PATCH generico delle righe accetta `color_code`, ma non aggiorna la relazione `filamentVariant`: non è sufficiente per questa funzione. |

I percorsi frontend della tabella sono relativi a `frontend/src/app/features/calculator/`,
salvo la guida (`features/colors-finishes/`) e i controlli (`src/app/shared/`).

## Stato e navigazione proposti

Estendere il meccanismo di bozza esistente, senza creare una seconda copia dei file.
Separare la richiesta di preventivo dai metadati temporanei di navigazione mediante
tipi espliciti nel servizio esistente o un piccolo servizio del solo flusso di scelta:

```typescript
interface CalculatorColorContext {
  mode: 'easy' | 'advanced';
  sessionId: string | null;
  targetClientModelKey: string;
  materialCode: string;
  originalVariantId: number | null;
  scrollY: number;
}
```

- Prima di uscire, salvare la bozza completa e il contesto. Ripetere il salvataggio
  anche se il preventivo non è ancora stato calcolato.
- Usare `clientModelKey` per individuare il destinatario; indice e nome file non
  sono identificatori affidabili con eliminazioni, riordini o nomi duplicati.
  Estendere anche il ripristino del file selezionato, oggi basato sul nome.
- Nell'URL della guida mettere eventualmente i soli filtri pubblici: materiale,
  finitura, variante evidenziata. Conservare sessione e nome file nel contesto locale.
  Costruire il ritorno dalle route note basic/advanced e dalla lingua attiva,
  senza accettare un URL di ritorno arbitrario.
- Alla conferma, risolvere l'ID nel catalogo corrente, verificarne materiale e
  disponibilità, poi aggiornare l'elemento della bozza. Non identificare una variante
  solo dal nome tradotto o dall'hex: finiture diverse possono avere lo stesso colore.
- Al ritorno, attendere il ripristino del modulo e delle opzioni; coordinare il
  caricamento sessione affinché una risposta tardiva non sovrascriva la bozza aggiornata.
  Applicare/consumare il risultato del flusso una sola volta.
- Usare la cronologia in modo da non riaprire automaticamente la scelta dopo la
  conferma; coprire esplicitamente i casi Indietro/Avanti nei test.
- Restituire scroll e focus al controllo del modello. Disabilitare l'apertura durante
  calcolo/upload attivo; non estendere il flusso alle sessioni CAD bloccate.
- Note e allegati di istruzioni appartengono a `OrderInformationService`, già singleton:
  mantenerne lo stato durante la navigazione e verificare che il ripristino non lo azzeri.

La bozza attuale vive nella memoria della SPA: una normale navigazione Angular può
conservare i file non ancora caricati, un ricaricamento completo o una nuova scheda no.
Non serializzare `File` nel localStorage. In questa prima versione, se il contesto
non esiste più, la guida torna informativa e non dichiara di poter ripristinare il
lavoro non salvato. I preventivi già salvati restano recuperabili dal loro link.
Garantire il recupero dei file non caricati anche dopo refresh richiederebbe un
intervento distinto su persistenza browser o caricamento preventivo dei file.

## Coerenza con preventivo e ordine

1. Aggiungere un evento tipizzato di cambio variante dal modulo alla pagina; usare
   lo stesso percorso per il selettore attuale e per la selezione dal catalogo.
2. Includere `filamentVariantId` e il colore nelle baseline e nel confronto delle
   modifiche, associandoli ai modelli in modo stabile anche con nomi duplicati.
3. Quando la scelta differisce dal preventivo, impostare `requiresRecalculation` e
   impedire la prosecuzione con dati precedenti, verificando anche il gestore
   `onProceed()` e le azioni di salvataggio/invio sessione.
4. Usare il normale ricalcolo con `filamentVariantId` già previsto nella richiesta:
   il backend aggiorna variante, costo e dati della riga insieme.
5. Il riepilogo precedente resta riconoscibile come da aggiornare; anteprima e
   modulo mostrano la nuova scelta. Dopo il ricalcolo, riepilogo e checkout devono
   ricevere la medesima variante.

Non sono previste nuove tabelle, dipendenze o API per il primo rilascio. Il backend
continua a validare il normale calcolo; il catalogo non effettua scritture di sessione.
Un aggiornamento istantaneo della variante su un preventivo già calcolato richiederebbe
invece un contratto backend dedicato, validazione e aggiornamento prezzi coerente:
non usare il PATCH del solo `color_code` come scorciatoia.

## Disponibilità, resa e accessibilità

- Varianti esaurite: consultabili e confrontabili, ma non applicabili. Varianti
  disattivate/assenti o diventate indisponibili: messaggio chiaro e scelta invariata.
- Usare `isOutOfStock` del catalogo senza dedurre disponibilità da soglie nuove.
  Aggiornare/verificare le opzioni al momento della conferma; lo stock non è riservato.
- Errori opzioni/media: conservare contesto e ritorno. Senza foto riusare il fallback
  esistente; l'assenza di una foto non impedisce la selezione di una variante valida.
- Evidenziare la variante corrente anche se un filtro la esclude; distinguere
  «Selezionato» da «Confronta». Etichette con colore, materiale e finitura.
- Riutilizzare controlli, `app-image-preview`, banner e primitive `ui-guide-*` esistenti.
  Nessuna nuova libreria o nuova struttura di popup.
- Mantenere avvertenza sulla resa di schermo e luce: per una scelta più precisa
  servono foto reali pubblicate secondo `docs/pricing-and-samples.md`.
- Localizzare ogni nuovo testo in IT/EN/DE/FR, verificare tastiera e mobile.
  Il contesto privato deve comparire solo nel browser; preservare la lettura SSR
  autonoma della guida e il suo comportamento in caso di catalogo indisponibile.

## Sequenza di implementazione e verifica

1. **Bozza e ritorno:** contesto tipizzato, identità del file, navigazione/annullamento,
   ripristino di impostazioni, anteprime, quantità, note e allegati.
2. **Scelta nel catalogo:** modalità contestuale, filtro del materiale, evidenziazione,
   conferma e validazione; visita autonoma invariata.
3. **Preventivo coerente:** evento variante, invalidazione del risultato e protezione
   della prosecuzione; prova completa fino al checkout dopo ricalcolo.
4. **Verifica:** test mirati di bozza/identità e ricalcolo; browser desktop/mobile
   con più file, nomi duplicati, basic/advanced, sessione presente/assente,
   selezione invariata/modificata, annullamento, Indietro/Avanti, risposte server
   ritardate, stock cambiato, errori, refresh e contesto perso.
5. Eseguire TypeScript, test mirati, `check:i18n`, `check:ui-reuse`; verificare build
   e `check:guides-ssr` per il catalogo. Per i test browser seguire `frontend/e2e/README.md`.
   Usare fixture per la navigazione e il ricalcolo; una prova reale di slicing è
   necessaria solo se emergono modifiche al relativo contratto/backend.

Analisi svolta sui sorgenti e sulle convenzioni del repository. Nessun test o
flusso browser eseguito in questa fase di pianificazione.
