# Print Calculator (OrcaSlicer Edition)

Un'applicazione Full Stack (Angular + Python/FastAPI) per calcolare preventivi di stampa 3D precisi utilizzando **OrcaSlicer** in modalità headless.

## Funzionalità

*   **Slicing Reale**: Usa il motore di OrcaSlicer per stimare tempo e materiale, non semplici approssimazioni geometriche.
*   **Preventivazione Completa**: Calcola costo materiale, ammortamento macchina, energia e ricarico.
*   **Configurabile**: Prezzi e parametri macchina modificabili via variabili d'ambiente.
*   **Docker Ready**: Tutto containerizzato per un facile deployment.

## Prerequisiti

*   Docker Desktop & Docker Compose installati.

## Avvio Rapido

1.  Clona il repository.
2.  Esegui lo script di avvio o docker-compose:
    ```bash
    docker-compose up --build
    ```
    *Nota: La prima build impiegherà alcuni minuti per scaricare OrcaSlicer (~200MB) e compilare il Frontend.*

3.  Accedi all'applicazione:
    *   **Frontend**: [http://localhost](http://localhost)
    *   **API Docs**: [http://localhost:8000/docs](http://localhost:8000/docs)

## Configurazione Prezzi

Puoi modificare i prezzi nel file `docker-compose.yml` (sezione `environment` del servizio backend):

*   `FILAMENT_COST_PER_KG`: Costo filamento al kg (es. 25.0).
*   `MACHINE_COST_PER_HOUR`: Costo orario macchina (ammortamento/manutenzione).
*   `ENERGY_COST_PER_KWH`: Costo energia elettrica.
*   `MARKUP_PERCENT`: Margine di profitto percentuale (es. 20 = +20%).

## Struttura del Progetto

*   `/backend`: API Python FastAPI. Include Dockerfile che scarica OrcaSlicer AppImage.
*   `/frontend`: Applicazione Angular 19+ con Material Design.
*   `/backend/profiles`: Contiene i profili di slicing (.ini). Attualmente configurato per una stima generica simil-Bambu Lab A1.

## Troubleshooting

### Errore Download OrcaSlicer
Se la build del backend fallisce durante il download di `OrcaSlicer.AppImage`, verifica la tua connessione internet o aggiorna l'URL nel `backend/Dockerfile`.

### Slicing Fallito (Costo 0 o Errore)
Se l'API ritorna errore o valori nulli:
1.  Controlla che il file STL sia valido (manifold).
2.  Controlla i log del backend: `docker logs print-calculator-backend`.

## Sviluppo Locale (Senza Docker)

**Backend**:
Richiede Linux (o WSL2) per eseguire l'AppImage di OrcaSlicer.
```bash
cd backend
pip install -r requirements.txt
# Assicurati di avere OrcaSlicer installato e nel PATH o aggiorna SLICER_PATH in slicer.py
uvicorn main:app --reload
```

**Frontend**:
```bash
cd frontend
npm install
npm start
```
