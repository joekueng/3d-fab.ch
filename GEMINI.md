# GEMINI Project Context

Questo file serve a dare contesto all'AI (Antigravity/Gemini) sulla struttura e logica del progetto.

## Project Overview
**Nome**: Print Calculator
**Scopo**: Calcolare costi e tempi di stampa 3D da file STL.
**Stack**:
- **Backend**: Python (FastAPI), libreria `trimesh` per analisi geometrica.
- **Frontend**: Angular 19 (TypeScript).

## Architecture

### Backend (`/backend`)
- **`main.py`**: Entrypoint dell'applicazione FastAPI.
    - Definisce l'API `POST /calculate/stl`.
    - Gestisce l'upload del file, invoca lo slicer e restituisce il preventivo.
    - Configura CORS per permettere chiamate dal frontend.
- **`slicer.py`**: Wrappa l'eseguibile di **OrcaSlicer** per effettuare lo slicing reale del modello.
    - Gestisce i profili di stampa (Macchina, Processo, Filamento).
    - Crea configurazioni on-the-fly per supportare mesh di grandi dimensioni.
- **`calculator.py`**: Analizza il G-Code generato.
    - `GCodeParser`: Estrae tempo di stampa e materiale usato dai metadati del G-Code.
    - `QuoteCalculator`: Applica i costi (orari, energia, materiale) per generare il prezzo finale.

### Frontend (`/frontend`)
- Applicazione Angular standard.
- Usa Angular Material.
- Service per upload STL e visualizzazione preventivo.

## Key Concepts
- **Real Slicing**: Il backend esegue un vero slicing usando OrcaSlicer in modalità headless. Questo garantisce stime di tempo e materiale estremamente precise, identiche a quelle che si otterrebbero preparando il file per la stampa.
- **G-Code Parsing**: Invece di stimare geometricamente, l'applicazione legge direttamene i commenti generati dallo slicer nel G-Code (es. `estimated printing time`, `filament used`).

## Development Notes
- Per eseguire il backend serve `uvicorn`.
- Il frontend richiede `npm install` al primo avvio.
- Le configurazioni di stampa (layer height, wall thickness, infill) sono attualmente hardcoded o con valori di default nel backend, ma potrebbero essere esposte come parametri API in futuro.
