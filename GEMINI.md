# GEMINI Project Context

Questo file serve a dare contesto all'AI (Antigravity/Gemini) sulla struttura e logica del progetto.

## Project Overview
**Nome**: Print Calculator
**Scopo**: Una piattaforma completa per la stampa 3D che include un calcolatore di preventivi basato su slicing reale, un sistema e-commerce (Shop), tracking di link tramite QR code e gestione di progetti (Showcase).
**Stack**:
- **Backend**: Java 21 (Spring Boot 3.4), PostgreSQL, Flyway.
- **Frontend**: Angular 19 (TypeScript), Angular Material, Three.js per visualizzazione 3D.

## Architecture

### Backend (`/backend`)
L'applicazione è strutturata in diversi moduli funzionali:

#### 1. Core Slicing & Quote (`service/quote`, `service/SlicerService.java`)
- **Slicing Reale**: Utilizza **OrcaSlicer** (headless) per generare G-Code reali e ottenere stime precise di tempo e materiale.
- **Quote Calculator**: Calcola il prezzo finale basandosi su politiche di prezzo (tiered pricing), costi energetici, markup e fee fissi.
- **G-Code Parser**: Estrae metadati (tempo, peso) direttamente dai commenti del G-Code generato.
- **3MF Support**: Supporto per la conversione e ispezione di file 3MF/STL tramite LWJGL/Assimp.

#### 2. Shop System (`service/shop`, `controller/PublicShopController.java`)
- Gestione di un catalogo prodotti con varianti, categorie gerarchiche e gestione del carrello tramite cookie e database.
- Integrazione SEO con generazione dinamica di sitemap (`SitemapController`).

#### 3. QR Tracking & Analytics (`service/qr`)
- Sistema di tracking per QR code con geo-localizzazione (GeoIP2) e statistiche di scansione (lingua, location, dispositivi).

#### 4. Media Management (`service/media`)
- Gestione di asset multimediali (immagini e video) con ispezione tecnica, elaborazione tramite FFmpeg e varianti ottimizzate.

#### 4b. Admin Localization (`service/admin`)
- Traduzioni admin tramite OpenAI centralizzate: prodotti shop mantengono il flusso dedicato con review, mentre testi localizzati generici (media e progetti home) usano `/api/admin/translations/localized-text`.

#### 5. Payments & Invoicing (`service/payment`)
- Supporto per pagamenti tramite **TWINT** e generazione di fatture PDF con standard **Swiss QR-Bill**.

#### 6. Security & Infrastructure
- **Security**: Autenticazione admin basata su sessione, protezione CSRF e throttling dei login.
- **Safety**: Scansione antivirus degli upload tramite **ClamAV**.
- **Storage**: Gestione file system organizzata per quote, richieste, shop e media.

### Documentation & DevOps
- **UML**: Documentazione architetturale dettagliata disponibile in `/docs/uml/` tramite file Mermaid (`.mmd`).
- **CI/CD**: Workflow definiti in `.gitea/workflows/` per deploy e PR checks.
- **Deployment**: Script di deploy automatizzati in `/deploy/`.

### Frontend (`/frontend`)
- Applicazione Angular 19 "Standalone" con architettura modulare:
    - **Features**: `calculator`, `shop`, `admin`, `checkout`, `contact`, `projects`.
    - **Shared**: Componenti riutilizzabili come `stl-viewer` (Three.js), `app-dropzone`, `brand-animation-logo`.
    - **Core**: Intercettori per la gestione dell'origine server e autenticazione admin, servizi SEO e internazionalizzazione (ngx-translate).

## Key Concepts
- **Real Slicing**: Garantisce preventivi accurati al grammo e al minuto, a differenza delle stime basate solo sul volume.
- **Database-Driven Pricing**: Prezzi guidati da entità DB (`PricingPolicy`, `PrinterMachine`, `FilamentVariant`).
- **Geo-Enriched Analytics**: Le scansioni QR vengono arricchite con dati geografici in tempo reale tramite eventi asincroni.
- **Automated Notifications**: Sistema di notifiche email per nuovi ordini e richieste di preventivo personalizzate.

## Development Notes
- **Backend**: Richiede JDK 21. `./gradlew bootRun` (profilo `local` di default).
- **Database**: PostgreSQL. Migrazioni gestite da Flyway in `src/main/resources/db/migration`.
- **Frontend**: Node.js 22. `npm start`.
- **Dipendenze Esterne**:
    - **OrcaSlicer**: Deve essere nel PATH o configurato in `application.properties`.
    - **FFmpeg**: Richiesto per l'elaborazione video.
    - **ClamAV**: Richiesto per la sicurezza degli upload in produzione.

## AI Agent Rules
- **No Inline Code**: Tutti i componenti Angular DEVONO usare file separati per HTML (`templateUrl`) e SCSS (`styleUrl`).
- **Spring Boot Conventions**: Seguire i pattern standard Service-Repository-Controller. Preferire `@RequiredArgsConstructor` per la Dependency Injection.
- **Validation**: Validare sempre gli input DTO tramite annotazioni `@Valid`.
- **Atomic Commits**: Se richiesto di committare, mantenere i commit piccoli e focalizzati per modulo.
