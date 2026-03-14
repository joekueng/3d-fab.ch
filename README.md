# Print Calculator (OrcaSlicer Edition)

Un'applicazione Full Stack (Angular + Spring Boot) per calcolare preventivi di stampa 3D precisi utilizzando **OrcaSlicer** in modalità headless.

## Funzionalità

*   **Slicing Reale**: Usa il motore di OrcaSlicer per stimare tempo e materiale, garantendo la massima precisione.
*   **Preventivazione Database-Driven**: Calcolo basato su politiche di prezzo configurabili nel database (costo materiale, ammortamento macchina a scaglioni, energia e markup).
*   **Visualizzazione 3D**: Anteprima del file STL caricato tramite Three.js.
*   **Multi-Profilo**: Supporto per diverse stampanti, materiali e profili di processo.

## Stack Tecnologico

- **Backend**: Java 21, Spring Boot 3.4, PostgreSQL.
- **Frontend**: Angular 19, Angular Material, Three.js.
- **Slicer**: OrcaSlicer (invocato via CLI).

## Prerequisiti

*   **Java 21** installato.
*   **Node.js 22** e **npm** installati.
*   **PostgreSQL** attivo.
*   **OrcaSlicer** installato sul sistema.
*   **FFmpeg** installato sul sistema o presente nell'immagine Docker del backend.

## Avvio Rapido

### 1. Database
Crea un database PostgreSQL chiamato `printcalc`. Lo schema viene gestito dal progetto tramite configurazione JPA/SQL del repository.

### 2. Backend
Configura il percorso di OrcaSlicer in `backend/src/main/resources/application.properties` o tramite la variabile d'ambiente `SLICER_PATH`. Per il media service pubblico puoi configurare anche:

- `MEDIA_STORAGE_ROOT` per la root `storage_media` usata dal backend (`original/`, `public/`, `private/`)
- `SHOP_STORAGE_ROOT` per la root `storage_shop` usata dal backend per i modelli dei prodotti shop
- `MEDIA_FFMPEG_PATH` per il binario `ffmpeg` (nel deploy Docker default: `/usr/local/bin/ffmpeg-media`)
- `MEDIA_UPLOAD_MAX_FILE_SIZE_BYTES` per il limite per asset immagine

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
*   `/storage_media`: Originali e varianti media pubbliche/private su filesystem.
*   `/storage_shop`: Modelli e file prodotti dello shop.

## Media pubblici

Il backend salva sempre l'originale in `storage_media/original/` e precomputa le varianti pubbliche in `storage_media/public/`. La cartella `storage_media/private/` è predisposta per asset non pubblici.

Nel deploy Docker i volumi attesi sono `/mnt/cache/appdata/print-calculator/${ENV}/storage_media:/app/storage_media` e `/mnt/cache/appdata/print-calculator/${ENV}/storage_shop:/app/storage_shop`.

Nginx non deve passare dal backend per i file pubblici. Configurazione attesa:

```nginx
location /media/ {
    alias /mnt/cache/appdata/print-calculator/${ENV}/storage_media/public/;
}
```

Usage key iniziali previste per frontend:

- `HOME_SECTION / shop-gallery`
- `HOME_SECTION / founders-gallery`
- `HOME_SECTION / capability-prototyping`
- `HOME_SECTION / capability-custom-parts`
- `HOME_SECTION / capability-small-series`
- `HOME_SECTION / capability-cad`
- `ABOUT_MEMBER / joe`
- `ABOUT_MEMBER / matteo`
- riservati per estensioni future: `SHOP_PRODUCT`, `SHOP_CATEGORY`, `SHOP_GALLERY`

Operativamente:

- carica i file dal media admin endpoint del backend
- associa ogni asset con `POST /api/admin/media/usages`
- per `ABOUT_MEMBER` imposta `isPrimary=true` sulla foto principale del membro
- home e about leggono da `GET /api/public/media/usages?usageType=...&usageKey=...`
- il frontend usa `<picture>` e preferisce AVIF/WEBP con fallback JPEG, senza usare l'originale
- nel back-office frontend la gestione operativa della home passa dalla pagina `admin/home-media`

## Troubleshooting

### Percorso OrcaSlicer
Assicurati che `slicer.path` punti al binario corretto. Su macOS è solitamente `/Applications/OrcaSlicer.app/Contents/MacOS/OrcaSlicer`. Su Linux è il percorso all'AppImage (estratta o meno).

### FFmpeg e media pubblici
Verifica che `MEDIA_FFMPEG_PATH` punti a un `ffmpeg` con supporto JPEG, WebP e AVIF (encoder + muxer AVIF). Nel container backend il default è `/usr/local/bin/ffmpeg-media`: usa `/usr/bin/ffmpeg` se già compatibile, altrimenti installa un fallback statico con supporto AVIF. Se gli URL media restituiti dalle API admin non sono raggiungibili, controlla che `APP_FRONTEND_BASE_URL` punti al dominio corretto, che `location /media/` sia esposto da Nginx e che il volume `storage_media` sia montato correttamente.

### Database connection
Verifica le credenziali in `application.properties`. Se usi Docker, puoi passare `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` come variabili d'ambiente.

### Deploy e traduzioni OpenAI
Nel deploy Gitea la chiave OpenAI deve stare nel secret `OPENAI_API_KEY`. La pipeline genera il blocco `common.env` remoto durante il deploy e il container backend la riceve come variabile runtime. I file `deploy/envs/*.env` restano per i valori specifici di `dev/int/prod`.
