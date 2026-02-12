# GEMINI Project Context

Questo file serve a dare contesto all'AI (Antigravity/Gemini) sulla struttura e logica del progetto.

## Project Overview
**Nome**: Print Calculator
**Scopo**: Calcolare costi e tempi di stampa 3D da file STL in modo preciso tramite slicing reale.
**Stack**:
- **Backend**: Java 21 (Spring Boot 3.4), PostgreSQL, Flyway.
- **Frontend**: Angular 19 (TypeScript), Angular Material, Three.js per visualizzazione 3D.

## Architecture

### Backend (`/backend`)
- **`BackendApplication.java`**: Entrypoint dell'applicazione Spring Boot.
- **`controller/`**: Espone le API REST per l'upload e il calcolo dei preventivi.
- **`service/SlicerService.java`**: Wrappa l'eseguibile di **OrcaSlicer** per effettuare lo slicing reale del modello.
    - Gestisce i profili di stampa (Macchina, Processo, Filamento) caricati da file JSON.
    - Crea configurazioni on-the-fly e invoca OrcaSlicer in modalità headless.
- **`service/GCodeParser.java`**: Analizza il G-Code generato per estrarre tempo di stampa e peso del materiale dai metadati del file.
- **`service/QuoteCalculator.java`**: Calcola il prezzo finale basandosi su politiche di prezzo salvate nel database.
    - Gestisce costi macchina a scaglioni (tiered pricing).
    - Calcola costi energetici basati sulla potenza della stampante e costo del kWh.
    - Applica markup percentuali e fee fissi per job.

### Frontend (`/frontend`)
- Applicazione Angular 19 con architettura modulare (core, features, shared).
- **Three.js**: Utilizzato per il rendering dei file STL caricati dall'utente.
- **Angular Material**: Per l'interfaccia utente.
- **ngx-translate**: Per il supporto multilingua.

## Key Concepts
- **Real Slicing**: Il backend esegue un vero slicing usando OrcaSlicer. Questo garantisce stime di tempo e materiale estremamente precise.
- **Database-Driven Pricing**: A differenza di versioni precedenti, il calcolo del preventivo è ora guidato da entità DB (`PricingPolicy`, `PrinterMachine`, `FilamentVariant`).
- **G-Code Metadata**: L'applicazione legge direttamene i commenti generati dallo slicer nel G-Code (es. `; estimated printing time`, `; filament used [g]`).

## Development Notes
- **Backend**: Richiede JDK 21. Si avvia con `./gradlew bootRun`.
- **Database**: Richiede PostgreSQL. Le migrazioni sono gestite da Flyway.
- **Frontend**: Richiede Node.js 22. Si avvia con `npm start`.
- **OrcaSlicer**: Deve essere installato sul sistema e il percorso configurato in `application.properties` o tramite variabile d'ambiente `SLICER_PATH`.

## AI Agent Rules
- **No Inline Code**: Tutti i componenti Angular DEVONO usare file separati per HTML (`templateUrl`) e SCSS (`styleUrl`). È vietato usare `template` o `styles` inline nel decoratore `@Component`.
- **Spring Boot Conventions**: Seguire i pattern standard di Spring Boot (Service-Repository-Controller).
