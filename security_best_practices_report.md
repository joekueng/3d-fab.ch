# Security Best Practices Report

## Executive summary

Revisione sicurezza del progetto `print-calculator` (backend Spring Boot Java + frontend Angular/TypeScript) con focus su autenticazione/autorizzazione, esposizione dati, upload file, hardening e resilienza.

Risultato: **6 finding** totali.

- **Critical**: 1
- **High**: 3
- **Medium**: 2

Rischio principale: API pubbliche basate su UUID senza controllo di ownership/token, che consentono lettura PII e azioni di business su ordini.

## Scope e metodo

- Codice analizzato: backend (`backend/src/main/java`, `backend/src/main/resources`), frontend (`frontend/src/app`), config deploy (`deploy/`, `docker-compose*.yml`).
- Riferimenti skill usati: `javascript-general-web-frontend-security.md`.
- Nota: non è presente un riferimento specifico Java/Spring nel set dello skill; per il backend sono state applicate best practice consolidate Spring/security engineering.

## Critical findings

### SBP-001 - Broken access control su API ordine pubbliche (lettura PII + azioni stato)

- **Severity**: Critical
- **Impatto**: Chiunque ottenga un `orderId` può leggere dati personali ordine e invocare operazioni di business senza autenticazione.
- **Evidenze**:
  - `backend/src/main/java/com/printcalculator/config/SecurityConfig.java:36` (`.anyRequest().permitAll()`).
  - `backend/src/main/java/com/printcalculator/controller/OrderController.java:131` (`GET /api/orders/{orderId}`).
  - `backend/src/main/java/com/printcalculator/controller/OrderController.java:141` (`POST /api/orders/{orderId}/payments/report`).
  - `backend/src/main/java/com/printcalculator/controller/OrderController.java:93` (`POST /api/orders/{orderId}/items/{orderItemId}/file`).
  - `backend/src/main/java/com/printcalculator/controller/OrderController.java:295`-`337` (PII completa nel DTO: email, telefono, indirizzi billing/shipping).
  - `backend/src/main/java/com/printcalculator/service/PaymentService.java:53`-`75` (cambio stato pagamento a `REPORTED`).
- **Rischio tecnico**:
  - Assenza di autenticazione/authorization applicativa su endpoint ordine.
  - Modello “capability by UUID” senza token secondario, expiry o binding utente.
- **Fix raccomandato**:
  - Introdurre un `order_access_token` random ad alta entropia (>=128 bit), memorizzato hashato e richiesto sugli endpoint pubblici ordine.
  - Separare endpoint pubblici (minimo set dati) da endpoint interni/admin.
  - Rimuovere `orderItemId` e dettagli sensibili dal DTO pubblico, o usare URL firmate a scadenza per upload/download.
  - Valutare auth customer leggera (magic link OTP) per consultazione/modifica ordine.

## High findings

### SBP-002 - Esposizione PII su endpoint pubblico custom quote request

- **Severity**: High
- **Evidenze**:
  - `backend/src/main/java/com/printcalculator/controller/CustomQuoteRequestController.java:188`-`193` (`GET /api/custom-quote-requests/{id}` senza auth).
  - `backend/src/main/java/com/printcalculator/entity/CustomQuoteRequest.java:24`-`40` (campi PII e messaggio cliente).
  - `backend/src/main/java/com/printcalculator/config/SecurityConfig.java:36` (`.anyRequest().permitAll()`).
- **Rischio tecnico**:
  - Endpoint “lookup by UUID” ritorna oggetto completo con dati personali.
- **Fix raccomandato**:
  - Proteggere endpoint con token di accesso separato per richiesta (non solo UUID).
  - Restituire una vista redatta/minimale per endpoint pubblici.
  - Se endpoint non usato dal frontend, rimuoverlo.

### SBP-003 - Antivirus in fail-open + default scanner disattivato

- **Severity**: High
- **Evidenze**:
  - `backend/src/main/resources/application.properties:27` (`clamav.enabled=${CLAMAV_ENABLED:false}`).
  - `backend/src/main/java/com/printcalculator/service/ClamAVService.java:42`-`43` (scanner disabilitato => ritorna `true`).
  - `backend/src/main/java/com/printcalculator/service/ClamAVService.java:54`-`61` (errori scanner => `FAIL-OPEN`).
  - `backend/src/main/java/com/printcalculator/service/FileSystemStorageService.java:59`-`60` (eccezioni scanner ignorate, file mantenuto).
- **Rischio tecnico**:
  - File malevoli possono essere accettati quando scanner è down/non configurato.
- **Fix raccomandato**:
  - Policy fail-closed in ambienti non-dev (`reject on scan error`).
  - Rendere `CLAMAV_ENABLED=true` default in deploy runtime e bloccare startup se scanner richiesto ma non raggiungibile.
  - Telemetria/alerting su scan bypass e failure rate.

### SBP-004 - Endpoint costosi esposti senza throttling/rate limit (DoS applicativo)

- **Severity**: High
- **Evidenze**:
  - `backend/src/main/java/com/printcalculator/config/SecurityConfig.java:36` (endpoint pubblici permessi globalmente).
  - `backend/src/main/java/com/printcalculator/controller/QuoteController.java:38`-`39` (`POST /api/quote` pubblico).
  - `backend/src/main/java/com/printcalculator/controller/QuoteSessionController.java:114`-`120` (`POST /api/quote-sessions/{id}/line-items` pubblico).
  - `backend/src/main/java/com/printcalculator/controller/QuoteSessionController.java:228`-`235` (invocazione slicing).
  - `backend/src/main/java/com/printcalculator/service/SlicerService.java:156`-`163` (job fino a 5 minuti).
- **Rischio tecnico**:
  - Upload/slicing massivo può saturare CPU, I/O e worker thread.
- **Fix raccomandato**:
  - Rate limiting per IP/fingerprint/session (anche lato reverse proxy).
  - Coda asincrona con limiti di concorrenza e timeout più stretti.
  - Quote per utente/sessione e limite richieste per finestra temporale.
  - CAPTCHA o proof-of-work per endpoint anonimi ad alto costo.

## Medium findings

### SBP-005 - Secret/default credenziali deboli nel codice di configurazione

- **Severity**: Medium
- **Evidenze**:
  - `backend/src/main/resources/application.properties:7` (`DB_PASSWORD` fallback `printcalc_secret`).
  - `backend/src/main/resources/application-local.properties:7`-`8` (admin password/secret hardcoded per profilo local).
- **Rischio tecnico**:
  - In caso di misconfigurazione ambientale o uso improprio profilo, vengono usati valori prevedibili.
- **Fix raccomandato**:
  - Rimuovere fallback sensibili e rendere obbligatori i secret a startup.
  - Spostare credenziali locali in file non versionato (`.env.local`, `.gitignore`) con template placeholder.
  - Policy di secret rotation periodica.

### SBP-006 - CSRF disabilitato globalmente con autenticazione admin basata su cookie

- **Severity**: Medium
- **Evidenze**:
  - `backend/src/main/java/com/printcalculator/config/SecurityConfig.java:24` (CSRF disabilitato globalmente).
  - `backend/src/main/java/com/printcalculator/security/AdminSessionService.java:129`-`136` (cookie sessione admin).
  - `backend/src/main/java/com/printcalculator/security/AdminSessionService.java:133`-`134` (`Secure` + `SameSite=Strict` presenti, mitigazione parziale).
- **Rischio tecnico**:
  - Con auth cookie-based, la protezione CSRF andrebbe mantenuta sugli endpoint state-changing admin; `SameSite=Strict` riduce ma non elimina tutti i vettori.
- **Fix raccomandato**:
  - Riabilitare CSRF almeno su `/api/admin/**` e usare token CSRF (double-submit o synchronizer token).
  - Mantenere `SameSite=Strict` come difesa aggiuntiva.

## Note e assunzioni

- Alcuni endpoint pubblici sembrano progettati come flusso anonimo customer; il finding resta valido perché manca una seconda prova di possesso oltre all’UUID.
- Non è stata eseguita una DAST esterna o pentest black-box; analisi effettuata su codice statico e configurazioni nel repository.

