# Print Calculator (OrcaSlicer Edition)

Un'applicazione Full Stack (Angular + Spring Boot) per calcolare preventivi di stampa 3D precisi utilizzando **OrcaSlicer** in modalità headless.

## Funzionalità

*   **Slicing Reale**: Usa il motore di OrcaSlicer per stimare tempo e materiale, garantendo la massima precisione.
*   **Preventivazione Database-Driven**: Calcolo basato su politiche di prezzo configurabili nel database (costo materiale, ammortamento macchina a scaglioni, energia e markup).
*   **Visualizzazione 3D**: Anteprima del file STL caricato tramite Three.js.
*   **Multi-Profilo**: Supporto per diverse stampanti, materiali e profili di processo.

## Stack Tecnologico

- **Backend**: Java 21, Spring Boot 3.4, PostgreSQL, Flyway.
- **Frontend**: Angular 19, Angular Material, Three.js.
- **Slicer**: OrcaSlicer (invocato via CLI).

## Prerequisiti

*   **Java 21** installato.
*   **Node.js 22** e **npm** installati.
*   **PostgreSQL** attivo.
*   **OrcaSlicer** installato sul sistema.

## Avvio Rapido

### 1. Database
Crea un database PostgreSQL chiamato `printcalc`. Le tabelle verranno create automaticamente al primo avvio tramite Flyway.

### 2. Backend
Configura il percorso di OrcaSlicer in `backend/src/main/resources/application.properties` o tramite la variabile d'ambiente `SLICER_PATH`.

```bash
cd backend
./gradlew bootRun
```

### 3. Frontend
```bash
cd frontend
npm install
npm start
```

Accedi a [http://localhost:4200](http://localhost:4200).

## Configurazione Prezzi

I prezzi non sono più gestiti tramite variabili d'ambiente fisse ma tramite tabelle nel database:
- `pricing_policy`: Definisce markup, fee fissi e costi elettrici.
- `pricing_policy_machine_hour_tier`: Definisce i costi orari delle macchine in base alla durata della stampa.
- `printer_machine`: Anagrafica stampanti e consumi energetici.
- `filament_material_type` / `filament_variant`: Listino prezzi materiali.

## Struttura del Progetto

*   `/backend`: API Spring Boot.
*   `/frontend`: Applicazione Angular.
*   `/backend/profiles`: Contiene i file di configurazione per OrcaSlicer.

## Troubleshooting

### Percorso OrcaSlicer
Assicurati che `slicer.path` punti al binario corretto. Su macOS è solitamente `/Applications/OrcaSlicer.app/Contents/MacOS/OrcaSlicer`. Su Linux è il percorso all'AppImage (estratta o meno).

### Database connection
Verifica le credenziali in `application.properties`. Se usi Docker, puoi passare `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` come variabili d'ambiente.
